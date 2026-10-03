package com.example.demo.service.notification;

import com.example.demo.service.ApiException;
import com.example.demo.service.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InboxService {
    private final JdbcTemplate db;
    private final CurrentUser user;

    public InboxService(JdbcTemplate db, CurrentUser user) {
        this.db = db;
        this.user = user;
    }

    public Map<String, Object> list() {
        Long employeeId = currentEmployeeId();
        if (employeeId == null) return Map.of("items", List.of(), "count", 0, "unreadCount", 0);
        List<Map<String, Object>> items = db.query(
            "SELECT m.id,m.category,m.title,m.summary,m.status,m.person_id,m.interview_id,m.is_read,m.read_at,m.created_at,i.person_id interview_person_id " +
                "FROM message m LEFT JOIN interview i ON i.id=m.interview_id AND i.org_id=m.org_id " +
                "WHERE m.org_id=? AND m.recipient_employee_id=? AND (m.category IN ('人才已填写问卷','人才问卷','面试安排','Offer 已确认') " +
                "OR (m.category='Offer 回复' AND m.status='已接受')) ORDER BY m.created_at DESC,m.id DESC",
            (row, number) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.getLong("id"));
                String category = row.getString("category");
                item.put("category", "人才问卷".equals(category) ? "人才已填写问卷" : "Offer 回复".equals(category) ? "Offer 已确认" : category);
                item.put("title", row.getString("title"));
                item.put("summary", row.getString("summary"));
                item.put("status", row.getString("status"));
                item.put("personId", row.getObject("person_id"));
                item.put("interviewId", row.getObject("interview_id"));
                item.put("read", row.getBoolean("is_read"));
                item.put("readAt", row.getString("read_at"));
                item.put("createdAt", row.getString("created_at"));
                Object personId = row.getObject("person_id");
                Object interviewPersonId = row.getObject("interview_person_id");
                item.put("route", personId != null ? "/person/" + personId : interviewPersonId != null ? "/person/" + interviewPersonId + "?tab=interviews" : "/inbox");
                return item;
            }, user.org(), employeeId
        );
        long unreadCount = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("read"))).count();
        return Map.of("items", items, "count", items.size(), "unreadCount", unreadCount);
    }

    @Transactional
    public Map<String, Object> markRead(String messageId) {
        Long id = positiveId(messageId);
        Long employeeId = currentEmployeeId();
        if (id == null || employeeId == null) throw ApiException.bad("消息编号不正确");
        if (db.update("UPDATE message SET is_read=TRUE,read_at=COALESCE(read_at,?) WHERE id=? AND org_id=? AND recipient_employee_id=?", now(), id, user.org(), employeeId) == 0) throw ApiException.missing();
        return Map.of("messageId", messageId, "read", true);
    }

    @Transactional
    public Map<String, Object> markAllRead() {
        Long employeeId = currentEmployeeId();
        int changed = employeeId == null ? 0 : db.update("UPDATE message SET is_read=TRUE,read_at=COALESCE(read_at,?) WHERE org_id=? AND recipient_employee_id=? AND is_read=FALSE", now(), user.org(), employeeId);
        return Map.of("read", true, "changed", changed, "unreadCount", 0);
    }

    public Long currentEmployeeId() {
        return db.query("SELECT employee_id FROM user WHERE id=? AND org_id=? AND employee_id IS NOT NULL", (row, number) -> row.getLong(1), user.id(), user.org()).stream().findFirst().orElse(null);
    }

    public void create(long recipientEmployeeId, String category, String title, String summary, String status, Long personId, Long interviewId, String sourceKey) {
        create(user.org(), recipientEmployeeId, category, title, summary, status, personId, interviewId, sourceKey);
    }

    public void create(long orgId, long recipientEmployeeId, String category, String title, String summary, String status, Long personId, Long interviewId, String sourceKey) {
        if (personId != null && interviewId != null) throw new IllegalArgumentException("消息只能关联人才或面试中的一种");
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM message WHERE org_id=? AND recipient_employee_id=? AND source_key=?", Integer.class, orgId, recipientEmployeeId, sourceKey);
        if (exists != null && exists > 0) return;
        db.update("INSERT INTO message(org_id,recipient_employee_id,category,title,summary,status,person_id,interview_id,source_key,is_read,created_at) VALUES(?,?,?,?,?,?,?,?,?,FALSE,?)", orgId, recipientEmployeeId, category, title, summary, status, personId, interviewId, sourceKey, now());
    }

    private Long positiveId(String value) {
        try { long id = Long.parseLong(value); return id > 0 ? id : null; }
        catch (Exception ignored) { return null; }
    }

    private String now() { return OffsetDateTime.now().toString(); }
}
