package com.example.demo.service.mail;

import org.springframework.stereotype.Component;

@Component
public class MailTemplateSupport {
    public String escape(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    public String card(String body) {
        return "<div style=\"font-family:Arial,'Microsoft YaHei',sans-serif;max-width:640px;margin:auto;color:#334155\">"
            + "<div style=\"padding:28px;border:1px solid #dbe5f2;border-radius:18px\">"
            + body
            + "</div></div>";
    }

    public String tableRow(String name, String value) {
        return "<tr><td style=\"padding:10px 14px;color:#94a3b8;border-bottom:1px solid #e2e8f0\">"
            + escape(name)
            + "</td><td style=\"padding:10px 14px;border-bottom:1px solid #e2e8f0\">"
            + escape(value)
            + "</td></tr>";
    }
}

