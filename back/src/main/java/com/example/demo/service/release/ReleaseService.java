package com.example.demo.service.release;

import com.example.demo.service.JsonStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReleaseService {
    private final JdbcTemplate db;
    private final JsonStore json;

    public ReleaseService(JdbcTemplate db, JsonStore json) {
        this.db = db;
        this.json = json;
    }

    public List<Map<String,Object>> releases() {
        return db.query("SELECT version,release_date,title,summary,changes_json,created_at FROM product_release WHERE active=TRUE ORDER BY id DESC", (row, index) -> {
            Map<String,Object> release = new LinkedHashMap<>();
            release.put("version", row.getString("version"));
            release.put("date", row.getString("release_date"));
            release.put("updatedAt", formatTime(row.getString("created_at")));
            release.put("title", row.getString("title"));
            release.put("summary", row.getString("summary"));
            Object items = json.read(row.getString("changes_json")).get("items");
            release.put("changes", items instanceof List<?> list ? list : List.of());
            return release;
        });
    }

    private String formatTime(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
        } catch (RuntimeException ignored) {
            return value.replace('T', ' ');
        }
    }
}

