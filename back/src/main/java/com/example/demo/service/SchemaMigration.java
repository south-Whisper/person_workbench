package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.time.OffsetDateTime;
import java.util.regex.*;

@Component
public class SchemaMigration {
    private final JdbcTemplate db;
    private final DataSource source;
    private final JsonStore json;
    private final TransactionTemplate transactions;
    public SchemaMigration(JdbcTemplate db, DataSource source, JsonStore json, PlatformTransactionManager tx) {
        this.db=db; this.source=source; this.json=json; this.transactions=new TransactionTemplate(tx);
    }
    @PostConstruct
    public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS sh_migration (version VARCHAR(80) PRIMARY KEY, applied_at VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS sh_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, username VARCHAR(120) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, role VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS sh_person (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, revision BIGINT NOT NULL, body LONGTEXT NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS sh_position (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, revision BIGINT NOT NULL, body LONGTEXT NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS sh_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, record_type VARCHAR(40) NOT NULL, revision BIGINT NOT NULL, body LONGTEXT NOT NULL, CONSTRAINT fk_record_person FOREIGN KEY(person_id) REFERENCES sh_person(id))");
        db.execute("CREATE TABLE IF NOT EXISTS sh_position_version (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, position_id BIGINT NOT NULL, version BIGINT NOT NULL, body LONGTEXT NOT NULL, UNIQUE(position_id,version), CONSTRAINT fk_version_position FOREIGN KEY(position_id) REFERENCES sh_position(id))");
        db.execute("CREATE TABLE IF NOT EXISTS sh_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, actor VARCHAR(120) NOT NULL, action VARCHAR(80) NOT NULL, created_at VARCHAR(40) NOT NULL, body LONGTEXT NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS sh_import_key (org_id BIGINT NOT NULL, batch_id VARCHAR(80) NOT NULL, row_number BIGINT NOT NULL, person_id BIGINT NOT NULL, PRIMARY KEY(org_id,batch_id,row_number), CONSTRAINT fk_import_person FOREIGN KEY(person_id) REFERENCES sh_person(id))");
        if (db.queryForObject("SELECT COUNT(*) FROM sh_migration WHERE version='schema-v1'", Integer.class)==0) {
            db.update("INSERT INTO sh_migration(version,applied_at) VALUES('schema-v1',?)", now());
        }
        transactions.executeWithoutResult(tx -> {
            db.queryForObject("SELECT version FROM sh_migration WHERE version='schema-v1' FOR UPDATE", String.class);
            if (db.queryForObject("SELECT COUNT(*) FROM sh_migration WHERE version='legacy-v1'", Integer.class)>0) return;
            importLegacy();
            db.update("INSERT INTO sh_migration(version,applied_at) VALUES('legacy-v1',?)", now());
        });
    }
    private boolean tableExists(String name) {
        try(Connection c=source.getConnection(); ResultSet rs=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})) {
            while(rs.next()) if(name.equalsIgnoreCase(rs.getString("TABLE_NAME"))) return true;
            return false;
        } catch(SQLException e) { throw new IllegalStateException("无法检查旧数据库表",e); }
    }
    private void importLegacy() {
        if(tableExists("sys_user")) for(Map<String,Object> r : db.queryForList("SELECT * FROM sys_user")) {
            String username=string(r,"username");
            if(!username.isBlank() && db.queryForObject("SELECT COUNT(*) FROM sh_user WHERE username=?",Integer.class,username)==0)
                db.update("INSERT INTO sh_user(org_id,username,password,role) VALUES(1,?,?,?)",username,string(r,"password"),string(r,"role").isBlank()?"ADMIN":string(r,"role"));
        }
        Map<String,Long> positions=new LinkedHashMap<>();
        if(tableExists("job_position")) for(Map<String,Object> r : db.queryForList("SELECT * FROM job_position")) {
            String name=string(r,"name"); if(name.isBlank()) continue;
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("name",name); body.put("minSalary",value(r,"min_salary")); body.put("maxSalary",value(r,"max_salary"));
            body.put("status","招聘中"); body.put("version",1); body.put("createdAt",now()); body.put("versionNote","旧系统岗位迁移"); body.put("legacyId",value(r,"id"));
            long id=insert("sh_position",1,body); positions.putIfAbsent(name,id);
            body.put("id",id); db.update("INSERT INTO sh_position_version(org_id,position_id,version,body) VALUES(1,?,1,?)",id,json.write(body));
        }
        if(tableExists("candidate")) for(Map<String,Object> r:db.queryForList("SELECT * FROM candidate")) {
            Map<String,Object> body=new LinkedHashMap<>();
            for(String key:List.of("name","gender","source","salary","status","result","experience","remark","phone","email","wechat")) body.put(key,string(r,key));
            body.put("applyTime",string(r,"apply_time")); body.put("legacyId",value(r,"id")); body.put("tags",List.of());
            String created=string(r,"create_time"); body.put("createdAt",created.isBlank()?now():created); body.put("version",1);
            String job=string(r,"job");
            if(!job.isBlank()) {
                if(!positions.containsKey(job)) {
                    Map<String,Object> p=new LinkedHashMap<>(); p.put("name",job);p.put("status","招聘中");p.put("version",1);p.put("createdAt",now());p.put("versionNote","从旧人才职位补全岗位");
                    long pid=insert("sh_position",1,p);positions.put(job,pid);p.put("id",pid);
                    db.update("INSERT INTO sh_position_version(org_id,position_id,version,body) VALUES(1,?,1,?)",pid,json.write(p));
                }
                body.put("jobId",positions.get(job)); body.put("job",job);
            }
            Matcher salary=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-–~至]\\s*(\\d+(?:\\.\\d+)?)").matcher(string(r,"salary"));
            if(salary.find()){body.put("salaryMin",Double.valueOf(salary.group(1)));body.put("salaryMax",Double.valueOf(salary.group(2)));}
            long id=insert("sh_person",1,body);
            Map<String,Object> event=new LinkedHashMap<>(); event.put("type","import");event.put("title","旧系统档案迁移");event.put("summary","保留原始档案字段；旧系统未记录的面试和沟通历史尚待补录。"); event.put("occurredAt",body.get("createdAt"));event.put("createdAt",now());event.put("actor","系统迁移");event.put("revision",1);
            db.update("INSERT INTO sh_record(org_id,person_id,record_type,revision,body) VALUES(1,?,'events',1,?)",id,json.write(event));
        }
    }
    public long insert(String table,long org,Map<String,Object> body) {
        if(!List.of("sh_person","sh_position").contains(table)) throw new IllegalArgumentException();
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO "+table+"(org_id,revision,body) VALUES(?,1,?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,org);p.setString(2,json.write(body));return p;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private static Object value(Map<String,Object> r,String key){for(Map.Entry<String,Object> e:r.entrySet())if(e.getKey().equalsIgnoreCase(key))return e.getValue();return null;}
    private static String string(Map<String,Object> r,String key){Object v=value(r,key);return v==null?"":v.toString();}
    static String now(){return OffsetDateTime.now().toString();}
}
