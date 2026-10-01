package com.example.demo.controller;

import com.example.demo.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/settings/mail")
public class SettingsController {
    private final MailSettingsService settings;
    private final MailDeliveryService mail;
    private final CurrentUser user;

    public SettingsController(MailSettingsService settings, MailDeliveryService mail, CurrentUser user) {
        this.settings = settings;
        this.mail = mail;
        this.user = user;
    }

    @GetMapping
    public Map<String,Object> current(@RequestParam(required=false) Long companyId){ return settings.current(companyId); }

    @PutMapping
    public Map<String,Object> save(@RequestBody Map<String,Object> input){ return settings.save(input); }

    @PostMapping("/test")
    public Map<String,Object> test(@RequestBody Map<String,Object> input) {
        user.requireAdmin();
        String recipient = Objects.toString(input.get("recipient"), "").strip();
        Long companyId;
        try{companyId=Long.valueOf(Objects.toString(input.get("companyId"),""));}catch(Exception e){throw ApiException.bad("请选择要测试的公司");}
        if (!recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw ApiException.bad("请填写接收测试邮件的邮箱");
        Map<String,Object> config=settings.current(companyId);
        mail.send(user.org(), Objects.toString(config.get("companyName"),"公司"), recipient, "人才管理系统发件测试",
            "这是一封发件测试邮件。收到此邮件，说明系统已经可以正常发送人才问卷和 Offer。",
            "<div style=\"font-family:Arial,'Microsoft YaHei',sans-serif;max-width:600px;margin:auto;padding:28px;border:1px solid #dbe5f2;border-radius:16px\"><h2 style=\"color:#1d4ed8\">发件配置成功</h2><p>这是一封发件测试邮件。</p><p>收到此邮件，说明系统已经可以正常发送人才问卷和 Offer。</p></div>");
        return Map.of("sent", true, "recipient", recipient);
    }
}
