package com.example.demo.service.mail;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class QuestionnaireMailService {
    private final MailDeliveryService mail;
    private final MailTemplateSupport template;
    private final AtomicReference<QuestionnaireEmail> lastCaptured=new AtomicReference<>();

    public QuestionnaireMailService(MailDeliveryService mail, MailTemplateSupport template){
        this.mail=mail;
        this.template=template;
    }

    public void send(QuestionnaireEmail invitation){
        lastCaptured.set(invitation);
        String company=invitation.company().isBlank()?"我们公司":invitation.company();
        String text=invitation.personName()+"，您好：\n\n"+company+"邀请您填写“"+invitation.jobName()+"”岗位的人才资料问卷。\n请打开下面的专属链接填写并提交：\n"+invitation.url()+"\n\n该链接仅供您本人使用，有效期为 7 天。提交后，负责 HR "+invitation.owner()+"会收到提醒。";
        String html=template.card("<p style=\"color:#2563eb;font-size:12px;letter-spacing:2px\">TALENT QUESTIONNAIRE</p><h1 style=\"font-size:25px;margin:10px 0\">"+template.escape(invitation.personName())+"，您好</h1><p style=\"line-height:1.8\">"+template.escape(company)+"邀请您填写以下岗位的人才资料：</p><div style=\"padding:18px;border-radius:12px;background:#eff5ff;border-left:4px solid #3370ff\"><strong style=\"font-size:18px\">"+template.escape(invitation.jobName())+"</strong><br><span style=\"color:#64748b;font-size:13px\">"+template.escape(company)+" · "+template.escape(invitation.department())+"</span></div><p style=\"margin:26px 0\"><a href=\""+template.escape(invitation.url())+"\" style=\"display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:13px 25px;border-radius:10px;font-weight:600\">填写人才问卷</a></p><p style=\"font-size:13px;color:#64748b;line-height:1.7\">专属链接有效期为 7 天。提交后，负责 HR "+template.escape(invitation.owner())+"会收到提醒。</p>");
        mail.send(invitation.orgId(),company,invitation.recipient(),"【"+company+"】请填写“"+invitation.jobName()+"”人才问卷",text,html);
    }

    public Optional<QuestionnaireEmail> lastCaptured(){return Optional.ofNullable(lastCaptured.get());}
    public record QuestionnaireEmail(String recipient,String personName,String company,String department,String jobName,String owner,String url,long orgId){}
}

