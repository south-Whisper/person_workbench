package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditService {
    private final JdbcTemplate db;
    private final JsonStore json;

    public AuditService(JdbcTemplate db, JsonStore json) {
        this.db = db;
        this.json = json;
    }

    public void recordAuthentication(String action, String username) {
        db.update(
            "INSERT INTO audit(org_id,actor,action,created_at,summary) VALUES(1,?,?,?,?)",
            username,
            action,
            OffsetDateTime.now().toString(),
            action + " 认证操作"
        );
    }

    public void recordBusinessChange(long orgId, String actor, String action, Map<String, Object> data) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit(org_id,actor,action,created_at,person_id,record_id,position_id,summary) VALUES(?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, orgId);
            statement.setString(2, actor);
            statement.setString(3, action);
            statement.setString(4, OffsetDateTime.now().toString());
            statement.setObject(5, positiveLongOrNull(data.get("personId")));
            statement.setObject(6, positiveLongOrNull(data.get("recordId")));
            statement.setObject(7, positiveLongOrNull(data.get("positionId")));
            statement.setString(8, action + " 业务数据变更");
            return statement;
        }, keys);

        long auditId = Objects.requireNonNull(keys.getKey()).longValue();
        Map<String, Object> before = stringMap(data.get("before"));
        Map<String, Object> after = stringMap(data.get("after"));
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());
        for (String field : fields) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                db.update(
                    "INSERT INTO audit_change(audit_id,field_name,before_value,after_value) VALUES(?,?,?,?)",
                    auditId,
                    field,
                    plain(before.get(field)),
                    plain(after.get(field))
                );
            }
        }
    }

    private Long positiveLongOrNull(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            long number = Long.parseLong(value.toString());
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(Objects.toString(key), item));
        return result;
    }

    private String plain(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).collect(Collectors.joining("，"));
        }
        if (value instanceof Map<?, ?>) return json.write(value);
        return Objects.toString(value);
    }
}
