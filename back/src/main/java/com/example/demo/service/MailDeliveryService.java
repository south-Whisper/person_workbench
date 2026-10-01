package com.example.demo.service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.AuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class MailDeliveryService {
    private static final Logger log=LoggerFactory.getLogger(MailDeliveryService.class);
    private final MailSettingsService settings;
    private final boolean captureOnly;

    public MailDeliveryService(MailSettingsService settings,@Value("${sethub.mail.capture-only:false}") boolean captureOnly) {
        this.settings=settings;
        this.captureOnly=captureOnly;
    }

    public void send(long orgId,String company,String recipient,String subject,String text,String html) {
        if(captureOnly)return;
        MailSettingsService.MailConfig config=settings.resolve(orgId,company);
        if(!config.configured()) throw new ApiException(503,"MAIL_NOT_CONFIGURED","“"+company+"”还没有配置发件邮箱。请先到“系统设置 → 公司发件邮箱”完成配置");
        try {
            JavaMailSenderImpl sender=new JavaMailSenderImpl();
            sender.setHost(config.host());sender.setPort(config.port());sender.setUsername(config.username());sender.setPassword(config.password());sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties properties=sender.getJavaMailProperties();properties.put("mail.smtp.auth","true");properties.put("mail.smtp.ssl.enable",Boolean.toString(config.ssl()));properties.put("mail.smtp.starttls.enable",Boolean.toString(config.starttls()));properties.put("mail.smtp.connectiontimeout","10000");properties.put("mail.smtp.timeout","15000");properties.put("mail.smtp.writetimeout","15000");
            MimeMessage message=sender.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(message,true,StandardCharsets.UTF_8.name());
            helper.setFrom(config.from().isBlank()?config.username():config.from());helper.setTo(recipient);helper.setSubject(subject);helper.setText(text,html);sender.send(message);
        } catch(ApiException e){throw e;} catch(Exception e){
            Throwable root=e;while(root.getCause()!=null)root=root.getCause();
            String detail=String.valueOf(root.getMessage());
            log.error("Mail send failed: company={}, provider={}, host={}, sender={}, recipient={}, cause={}: {}",company,config.provider(),config.host(),config.username(),recipient,root.getClass().getSimpleName(),detail);
            String lower=detail.toLowerCase();
            if(root instanceof AuthenticationFailedException||lower.contains("535")||lower.contains("authentication")||lower.contains("auth"))
                throw new ApiException(502,"MAIL_AUTH_FAILED","邮箱服务器拒绝登录。请确认已在邮箱后台开启 SMTP 服务，并填写邮箱生成的授权码；这里不能填写 QQ 登录密码");
            if(lower.contains("sender address")||lower.contains("from address"))
                throw new ApiException(502,"MAIL_FROM_REJECTED","邮箱服务器拒绝了发件地址。发件地址必须与登录邮箱一致");
            if(lower.contains("timeout")||lower.contains("timed out")||lower.contains("connection"))
                throw new ApiException(502,"MAIL_CONNECTION_FAILED","连接邮箱服务器超时，请稍后重试或检查 SMTP 服务器和端口");
            throw new ApiException(502,"MAIL_SEND_FAILED","邮件发送失败："+root.getClass().getSimpleName()+"。请检查邮箱授权码和 SMTP 设置");
        }
    }
}
