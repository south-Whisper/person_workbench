package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
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
        return db.query("SELECT version,release_date,title,summary,changes_json FROM product_release WHERE active=TRUE ORDER BY id DESC", (row, index) -> {
            Map<String,Object> release = new LinkedHashMap<>();
            release.put("version", row.getString("version"));
            release.put("date", row.getString("release_date"));
            release.put("title", row.getString("title"));
            release.put("summary", row.getString("summary"));
            Object items = json.read(row.getString("changes_json")).get("items");
            release.put("changes", items instanceof List<?> list ? list : List.of());
            return release;
        });
    }
}
