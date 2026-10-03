package com.example.demo.service.offer;

import com.example.demo.service.ApiException;
import com.example.demo.service.notification.InboxService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OfferResponseService {
    private final JdbcTemplate db;
    private final InboxService inbox;

    public OfferResponseService(JdbcTemplate db, InboxService inbox) {
        this.db = db;
        this.inbox = inbox;
    }

    public Map<String, Object> find(String token) {
        return findRow(token, false);
    }

    @Transactional
    public Map<String, Object> respond(String token, Map<String, Object> input) {
        Map<String, Object> offer = findRow(token, true);
        String decision = Objects.toString(input.get("decision"), "").strip().toUpperCase(Locale.ROOT);
        String status = switch (decision) {
            case "ACCEPTED", "接受", "已接受" -> "已接受";
            case "DECLINED", "拒绝", "已拒绝" -> "已拒绝";
            default -> throw ApiException.bad("请选择接受或拒绝 Offer");
        };
        String reason = Objects.toString(input.get("reason"), "").strip();
        if ("已拒绝".equals(status) && reason.isBlank()) throw ApiException.bad("拒绝 Offer 时请填写原因");
        String current = Objects.toString(offer.get("responseStatus"), "待回复");
        if (Set.of("已接受", "已拒绝").contains(current)) {
            if (current.equals(status)) return offer;
            throw new ApiException(409, "OFFER_ALREADY_RESPONDED", "这份 Offer 已经提交过回复，不能重复更改");
        }
        LocalDate expires = LocalDate.parse(Objects.toString(offer.get("expiresAt")));
        if (expires.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")))) throw new ApiException(410, "OFFER_EXPIRED", "这份 Offer 已过有效期，请联系 HR");

        long offerId = number(offer, "id");
        long orgId = number(offer, "orgId");
        long personId = number(offer, "personId");
        long applicationId = number(offer, "applicationId");
        long ownerId = number(offer, "ownerEmployeeId");
        String timestamp = OffsetDateTime.now().toString();
        db.update("UPDATE offer SET response_status=?,responded_at=?,response_reason=? WHERE id=? AND org_id=?", status, timestamp, reason.isBlank() ? null : reason, offerId, orgId);
        if ("已接受".equals(status)) {
            db.update("UPDATE application SET status='待入职',talent_intent='强烈',updated_at=?,revision=revision+1 WHERE id=? AND org_id=? AND person_id=?", timestamp, applicationId, orgId, personId);
            db.update("UPDATE person SET status='待入职',talent_intent='强烈',updated_at=?,revision=revision+1 WHERE id=? AND org_id=?", timestamp, personId, orgId);
        } else {
            String closeReason = "候选人拒绝 Offer：" + reason;
            db.update("UPDATE application SET status='候选人退出',talent_intent='明确拒绝',reason=?,updated_at=?,revision=revision+1 WHERE id=? AND org_id=? AND person_id=?", closeReason, timestamp, applicationId, orgId, personId);
            db.update("UPDATE person SET status='已关闭',result='候选人退出',talent_intent='明确拒绝',reason=?,updated_at=?,revision=revision+1 WHERE id=? AND org_id=?", closeReason, timestamp, personId, orgId);
        }
        String personName = Objects.toString(offer.get("personName"), "候选人");
        String jobName = Objects.toString(offer.get("jobName"), "岗位");
        if ("已接受".equals(status)) inbox.create(orgId, ownerId, "Offer 已确认", personName + "已接受 Offer", jobName + " · 可进入入职流程", status, personId, null, "offer-accepted-" + offerId);
        db.update("INSERT INTO record(org_id,person_id,application_id,entity_type,entity_id,action,title,summary,result,reason,occurred_at,actor,created_at) VALUES(?,?,?,'offer',?,'responded','候选人回复 Offer',?,?,?,?,?,'候选人',?)", orgId, personId, applicationId, offerId, personName + "已" + ("已接受".equals(status) ? "接受" : "拒绝") + " " + jobName, status, reason, timestamp, timestamp);
        db.update("INSERT INTO audit(org_id,actor,action,created_at,person_id,record_id,summary) VALUES(?,'候选人','offer.respond',?,?,?,'候选人通过邮件回复 Offer')", orgId, timestamp, personId, offerId);
        return findRow(token, false);
    }

    @Transactional
    public String acceptAndRender(String token) {
        try {
            Map<String, Object> offer = respond(token, Map.of("decision", "ACCEPTED"));
            return resultPage("Offer 已接受", Objects.toString(offer.get("personName"), "候选人") + "，您的选择已经记录，无需再登录或操作。", true);
        } catch (ApiException error) {
            return resultPage("无法接受 Offer", error.getMessage(), false);
        }
    }

    private Map<String, Object> findRow(String token, boolean lock) {
        String hash = tokenHash(token);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT o.id,o.org_id,o.person_id,o.application_id,o.owner_employee_id,o.job_name,o.company,o.salary_mode,o.actual_salary,o.annual_salary,o.salary_months,o.probation_months,o.social_insurance,o.expected_start_date,o.expires_at,o.sent_at,o.response_status,o.responded_at,o.response_reason,p.name person_name,e.name owner_name " +
                "FROM offer o JOIN person p ON p.id=o.person_id AND p.org_id=o.org_id LEFT JOIN employee e ON e.id=o.owner_employee_id AND e.org_id=o.org_id " +
                "WHERE o.response_token_hash=? AND o.current_record=TRUE AND o.status='已完成'" + (lock ? " FOR UPDATE" : ""), hash
        );
        if (rows.isEmpty()) throw new ApiException(404, "OFFER_LINK_INVALID", "Offer 链接不存在或已经失效");
        Map<String, Object> row = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, row, "id", "id"); put(out, row, "orgId", "org_id"); put(out, row, "personId", "person_id");
        put(out, row, "applicationId", "application_id"); put(out, row, "ownerEmployeeId", "owner_employee_id");
        put(out, row, "personName", "person_name"); put(out, row, "jobName", "job_name"); put(out, row, "company", "company");
        put(out, row, "salaryMode", "salary_mode"); put(out, row, "actualSalary", "actual_salary"); put(out, row, "annualSalary", "annual_salary");
        put(out, row, "salaryMonths", "salary_months"); put(out, row, "probationMonths", "probation_months"); put(out, row, "socialInsurance", "social_insurance");
        put(out, row, "expectedStartDate", "expected_start_date"); put(out, row, "expiresAt", "expires_at"); put(out, row, "sentAt", "sent_at");
        out.put("responseStatus", Objects.toString(row.get("response_status"), "待回复")); put(out, row, "respondedAt", "responded_at");
        put(out, row, "responseReason", "response_reason"); put(out, row, "owner", "owner_name");
        out.put("expired", LocalDate.parse(Objects.toString(row.get("expires_at"))).isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))));
        return out;
    }

    private void put(Map<String, Object> target, Map<String, Object> source, String targetKey, String sourceKey) { target.put(targetKey, source.get(sourceKey)); }
    private long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }

    private String tokenHash(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{40,100}$")) throw new ApiException(404, "OFFER_LINK_INVALID", "Offer 链接不存在或已经失效");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("无法校验 Offer 链接", error); }
    }

    private String resultPage(String title, String message, boolean accepted) {
        String color = accepted ? "#16794b" : "#b4233b";
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>" + escape(title) + "</title><body style=\"margin:0;min-height:100vh;display:grid;place-items:center;background:#f4f7fb;font-family:Arial,'Microsoft YaHei',sans-serif;color:#27364a\">" +
            "<main style=\"width:min(520px,calc(100% - 40px));box-sizing:border-box;padding:38px;border:1px solid #dbe5f2;border-radius:22px;background:#fff;text-align:center;box-shadow:0 20px 60px #4560801f\">" +
            "<div style=\"width:62px;height:62px;margin:0 auto 18px;display:grid;place-items:center;border-radius:50%;background:" + (accepted ? "#e8f8ee" : "#fff0f1") + ";color:" + color + ";font-size:30px;font-weight:800\">" + (accepted ? "✓" : "!") + "</div>" +
            "<h1 style=\"margin:0 0 12px;color:" + color + ";font-size:25px\">" + escape(title) + "</h1><p style=\"margin:0;line-height:1.8;color:#64748b\">" + escape(message) + "</p>" +
            "<p style=\"margin:24px 0 0;font-size:12px;color:#94a3b8\">现在可以关闭此页面</p></main></body></html>";
    }

    private String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
