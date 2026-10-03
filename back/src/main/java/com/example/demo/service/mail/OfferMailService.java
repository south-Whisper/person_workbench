package com.example.demo.service.mail;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OfferMailService {
    private final MailDeliveryService mail;
    private final MailTemplateSupport template;
    private final AtomicReference<OfferEmail> lastCaptured=new AtomicReference<>();

    public OfferMailService(MailDeliveryService mail, MailTemplateSupport template) {
        this.mail = mail;
        this.template = template;
    }

    public void send(OfferEmail offer) {
        lastCaptured.set(offer);
        mail.send(offer.orgId(),offer.company(),offer.recipient(),"【"+offer.company()+"】"+offer.jobName()+" Offer",text(offer),html(offer));
    }
    public Optional<OfferEmail> lastCaptured(){return Optional.ofNullable(lastCaptured.get());}

    private String text(OfferEmail o){return o.personName()+"，您好：\n\n"+o.company()+"诚邀您加入，岗位为“"+o.jobName()+"”。\n薪资方案："+o.salary()+"\n试用期："+o.probation()+"\n社保与公积金："+o.socialInsurance()+"\n计划入职："+o.expectedStartDate()+"\n有效期至："+o.expiresAt()+"\n\n接受 Offer（点击即提交）：\n"+o.acceptUrl()+"\n\n拒绝 Offer（需要填写原因并再次确认）：\n"+o.declineUrl()+"\n\n如有疑问，请联系负责 HR："+o.owner()+"。";}
    private String html(OfferEmail o){return template.card("<p style=\"color:#2563eb;font-size:12px;letter-spacing:2px\">OFFER LETTER</p><h1 style=\"font-size:25px\">"+template.escape(o.personName())+"，您好</h1><p>"+template.escape(o.company())+"诚邀您加入，担任 <strong>"+template.escape(o.jobName())+"</strong>。</p><table style=\"width:100%;border-collapse:collapse;background:#f8fafc\">"+template.tableRow("薪资方案",o.salary())+template.tableRow("试用期",o.probation())+template.tableRow("社保与公积金",o.socialInsurance())+template.tableRow("计划入职",o.expectedStartDate())+template.tableRow("有效期至",o.expiresAt())+"</table><p style=\"margin:25px 0 10px;font-size:13px;color:#64748b\">接受会直接提交；拒绝需要填写原因并再次确认：</p><table role=\"presentation\" style=\"border-collapse:collapse\"><tr><td style=\"padding:0 10px 0 0\"><a href=\""+template.escape(o.acceptUrl())+"\" style=\"display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:13px 25px;border-radius:10px;font-weight:700\">接受 Offer</a></td><td><a href=\""+template.escape(o.declineUrl())+"\" style=\"display:inline-block;background:#fff;color:#b4233b;text-decoration:none;padding:12px 24px;border:1px solid #e6b8bf;border-radius:10px;font-weight:700\">拒绝 Offer</a></td></tr></table><p style=\"margin-top:20px;font-size:12px;color:#8a97a8\">每份 Offer 只能回复一次，请按真实意愿选择。</p><p style=\"font-size:13px;color:#64748b\">如有疑问，请联系负责 HR："+template.escape(o.owner())+"。</p>");}

    public record OfferEmail(String recipient,String personName,String company,String jobName,String salary,String probation,String socialInsurance,String expectedStartDate,String expiresAt,String owner,String acceptUrl,String declineUrl,long orgId) {}
}

