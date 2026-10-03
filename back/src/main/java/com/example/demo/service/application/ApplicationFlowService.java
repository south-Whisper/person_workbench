package com.example.demo.service.application;

import com.example.demo.service.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ApplicationFlowService {
    private static final Set<String> DIRECT_STAGES = Set.of("面试中", "Offer中", "已入职");

    private final JdbcTemplate db;
    private final CurrentUser user;

    public ApplicationFlowService(JdbcTemplate db, CurrentUser user) {
        this.db = db;
        this.user = user;
    }

    public void syncLatestFromTalent(long personId, Long jobId, String talentStatus) {
        if (!DIRECT_STAGES.contains(talentStatus)) return;
        List<Long> applications = db.query(
            "SELECT id FROM application WHERE org_id=? AND person_id=? ORDER BY id DESC LIMIT 1",
            (row, number) -> row.getLong(1), user.org(), personId
        );
        if (applications.isEmpty()) return;
        long applicationId = applications.get(0);
        String timestamp = OffsetDateTime.now().toString();

        if (jobId == null) {
            db.update(
                "UPDATE application SET status=?,result=CASE WHEN ?='已入职' THEN '录用' ELSE result END,updated_at=?,revision=revision+1 WHERE id=? AND org_id=? AND person_id=?",
                talentStatus, talentStatus, timestamp, applicationId, user.org(), personId
            );
            return;
        }

        List<Map<String, Object>> positions = db.queryForList(
            "SELECT id,name,version_number,company,recruitment_code,base_location FROM position WHERE id=? AND org_id=?",
            jobId, user.org()
        );
        if (positions.isEmpty()) return;
        Map<String, Object> position = positions.get(0);
        db.update(
            "UPDATE application SET job_id=?,job_name=?,job_version=?,company=?,job_code=?,base_location=?,status=?,result=CASE WHEN ?='已入职' THEN '录用' ELSE result END,updated_at=?,revision=revision+1 WHERE id=? AND org_id=? AND person_id=?",
            jobId,
            position.get("name"),
            position.get("version_number"),
            position.get("company"),
            Objects.toString(position.get("recruitment_code"), ""),
            Objects.toString(position.get("base_location"), ""),
            talentStatus,
            talentStatus,
            timestamp,
            applicationId,
            user.org(),
            personId
        );
    }
}
