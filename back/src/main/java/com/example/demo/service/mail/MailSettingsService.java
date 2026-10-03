package com.example.demo.service.mail;

import com.example.demo.service.ApiException;
import com.example.demo.service.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MailSettingsService {
    private final JdbcTemplate db;
    private final CurrentUser user;
    private final byte[] encryptionKey;
    private final MailConfig environment;

    public MailSettingsService(
        JdbcTemplate db,
        CurrentUser user,
        @Value("${sethub.jwt-secret:person-workbench-local-mail-secret}") String secret,
        @Value("${sethub.mail.host:}") String host,
        @Value("${sethub.mail.port:465}") int port,
        @Value("${sethub.mail.username:}") String username,
        @Value("${sethub.mail.password:}") String password,
        @Value("${sethub.mail.from:}") String from,
        @Value("${sethub.mail.ssl:true}") boolean ssl,
        @Value("${sethub.mail.starttls:false}") boolean starttls
    ) {
        this.db = db;
        this.user = user;
        try { this.encryptionKey = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("无法初始化发件配置加密", e); }
        this.environment = new MailConfig(host.strip(), port, username.strip(), password, from.strip(), ssl, starttls, "自定义");
    }

    public Map<String,Object> current(Long companyId) {
        user.requireAdmin();
        CompanyRef company = company(companyId);
        return publicView(resolve(user.org(), company.id()), company);
    }

    public MailConfig resolve(long orgId,String companyName) {
        List<Long> ids=db.query("SELECT id FROM company WHERE org_id=? AND name=? AND active=TRUE ORDER BY id LIMIT 1",(r,n)->r.getLong(1),orgId,companyName);
        return ids.isEmpty()?environment:resolve(orgId,ids.get(0));
    }

    public MailConfig resolve(long orgId,long companyId) {
        List<Map<String,Object>> rows = db.queryForList("SELECT provider,host,port,username,password_cipher,sender_email,ssl_enabled,starttls_enabled FROM mail_setting WHERE org_id=? AND company_id=? ORDER BY id DESC LIMIT 1", orgId,companyId);
        if (rows.isEmpty()) return environment;
        Map<String,Object> row = rows.get(0);
        String password = decrypt(Objects.toString(row.get("password_cipher"), ""));
        if (password.isBlank()) password = environment.password();
        return new MailConfig(
            Objects.toString(row.get("host"), "").strip(),
            ((Number)row.get("port")).intValue(),
            Objects.toString(row.get("username"), "").strip(),
            password,
            Objects.toString(row.get("sender_email"), "").strip(),
            Boolean.TRUE.equals(row.get("ssl_enabled")),
            Boolean.TRUE.equals(row.get("starttls_enabled")),
            Objects.toString(row.get("provider"), "自定义")
        );
    }

    @Transactional
    public Map<String,Object> save(Map<String,Object> input) {
        user.requireAdmin();
        long orgId = user.org();
        CompanyRef company=company(numberLong(input.get("companyId")));
        String provider = text(input, "provider", "自定义");
        String host = text(input, "host", "");
        String username = text(input, "username", "");
        String senderEmail = text(input, "senderEmail", username);
        String suppliedPassword = Objects.toString(input.get("password"), "");
        int port = number(input.get("port"), 465);
        boolean ssl = bool(input.get("ssl"), true);
        boolean starttls = bool(input.get("starttls"), false);
        if (host.isBlank() || host.length() > 240 || !host.matches("[A-Za-z0-9.-]+")) throw ApiException.bad("请填写正确的发件服务器地址");
        if (port < 1 || port > 65535) throw ApiException.bad("发件端口不正确");
        if (!email(username) || !email(senderEmail)) throw ApiException.bad("请填写正确的发件邮箱");
        if (ssl && starttls) throw ApiException.bad("SSL 和 STARTTLS 只能开启一种加密方式");
        List<String> existing = db.query("SELECT password_cipher FROM mail_setting WHERE org_id=? AND company_id=?", (r,n)->Objects.toString(r.getString(1), ""), orgId,company.id());
        String cipher = suppliedPassword.isBlank() ? (existing.isEmpty() ? "" : existing.get(0)) : encrypt(suppliedPassword);
        if (cipher.isBlank() && environment.password().isBlank()) throw ApiException.bad("请填写邮箱授权码");
        String now = OffsetDateTime.now().toString();
        db.update("INSERT INTO mail_setting(org_id,company_id,provider,host,port,username,password_cipher,sender_email,ssl_enabled,starttls_enabled,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE provider=VALUES(provider),host=VALUES(host),port=VALUES(port),username=VALUES(username),password_cipher=VALUES(password_cipher),sender_email=VALUES(sender_email),ssl_enabled=VALUES(ssl_enabled),starttls_enabled=VALUES(starttls_enabled),updated_at=VALUES(updated_at),updated_by=VALUES(updated_by)",
            orgId, company.id(), provider, host, port, username, cipher, senderEmail, ssl, starttls, now, user.name());
        db.update("INSERT INTO audit(org_id,actor,action,created_at,summary) VALUES(?,?, 'mail.settings',?,'更新发件邮箱配置（授权码未写入审计）')", orgId, user.name(), now);
        return publicView(resolve(orgId,company.id()),company);
    }

    private Map<String,Object> publicView(MailConfig config,CompanyRef company) {
        Map<String,Object> view = new LinkedHashMap<>();
        view.put("companyId",company.id());
        view.put("companyName",company.name());
        view.put("provider", config.provider());
        view.put("host", config.host());
        view.put("port", config.port());
        view.put("username", config.username());
        view.put("senderEmail", config.from().isBlank() ? config.username() : config.from());
        view.put("ssl", config.ssl());
        view.put("starttls", config.starttls());
        view.put("configured", config.configured());
        view.put("passwordConfigured", !config.password().isBlank());
        return view;
    }

    private CompanyRef company(Long requested){
        List<CompanyRef> rows=requested==null
            ?db.query("SELECT id,name FROM company WHERE org_id=? AND active=TRUE ORDER BY id LIMIT 1",(r,n)->new CompanyRef(r.getLong(1),r.getString(2)),user.org())
            :db.query("SELECT id,name FROM company WHERE org_id=? AND id=? AND active=TRUE",(r,n)->new CompanyRef(r.getLong(1),r.getString(2)),user.org(),requested);
        if(rows.isEmpty())throw ApiException.bad("请选择公司表中的有效公司");
        return rows.get(0);
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(joined);
        } catch (Exception e) { throw new IllegalStateException("无法加密邮箱授权码", e); }
    }

    private String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] joined = Base64.getDecoder().decode(value);
            byte[] iv = Arrays.copyOfRange(joined, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(joined, 12, joined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new ApiException(500, "MAIL_SETTINGS_UNREADABLE", "发件邮箱授权码无法读取，请在系统设置中重新填写"); }
    }

    private static String text(Map<String,Object> input,String key,String fallback){String value=Objects.toString(input.get(key),fallback).strip();return value;}
    private static int number(Object value,int fallback){try{return Integer.parseInt(Objects.toString(value,Integer.toString(fallback)));}catch(Exception e){return fallback;}}
    private static Long numberLong(Object value){try{String text=Objects.toString(value,"");return text.isBlank()?null:Long.valueOf(text);}catch(Exception e){return null;}}
    private static boolean bool(Object value,boolean fallback){if(value==null)return fallback;return Boolean.parseBoolean(value.toString());}
    private static boolean email(String value){return value!=null&&value.length()<=240&&value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");}

    public record MailConfig(String host,int port,String username,String password,String from,boolean ssl,boolean starttls,String provider) {
        public boolean configured(){return !host.isBlank()&&!username.isBlank()&&!password.isBlank();}
    }
    private record CompanyRef(long id,String name){}
}

