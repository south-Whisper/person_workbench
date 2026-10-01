package com.example.demo.service;

import com.example.demo.utils.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords;
    private final JwtUtil jwt;
    private final AuditService audits;

    public AuthService(JdbcTemplate db, BCryptPasswordEncoder passwords, JwtUtil jwt, AuditService audits) {
        this.db = db;
        this.passwords = passwords;
        this.jwt = jwt;
        this.audits = audits;
    }

    public Map<String, Object> setupStatus() {
        return Map.of("needsSetup", db.queryForObject("SELECT COUNT(*) FROM user", Integer.class) == 0);
    }

    @Transactional
    public Map<String, Object> setup(Map<String, Object> input) {
        db.queryForObject("SELECT version FROM migration WHERE version='schema-v1' FOR UPDATE", String.class);
        if (db.queryForObject("SELECT COUNT(*) FROM user", Integer.class) > 0) {
            throw new ApiException(409, "SETUP_COMPLETE", "系统已有账号，请使用现有账号登录");
        }
        String username = Objects.toString(input.get("username"), "").trim();
        String password = Objects.toString(input.get("password"), "");
        validateSetupCredentials(username, password);

        long employeeId = createEmployee(username);
        long userId = createUser(username, password, employeeId);
        db.update("UPDATE employee SET user_id=? WHERE id=?", userId, employeeId);
        audits.recordAuthentication("initial_setup", username);
        return loginResult(userId, username, "ADMIN", employeeName(employeeId, username));
    }

    public Map<String, Object> login(Map<String, Object> input) {
        String username = Objects.toString(input.get("username"), "").trim();
        String password = Objects.toString(input.get("password"), "");
        if (username.isBlank() || password.isBlank() || username.length() > 120 || password.length() > 72) {
            throw invalidCredentials();
        }

        List<Map<String, Object>> users = db.queryForList(
            "SELECT u.id,u.username,u.password,u.role,e.name employee_name FROM user u LEFT JOIN employee e ON e.id=u.employee_id AND e.org_id=u.org_id WHERE u.username=? AND u.active=TRUE",
            username
        );
        if (users.isEmpty() || !passwords.matches(password, users.get(0).get("password").toString())) {
            throw invalidCredentials();
        }

        Map<String, Object> user = users.get(0);
        audits.recordAuthentication("login", username);
        return loginResult(
            ((Number) user.get("id")).longValue(),
            username,
            user.get("role").toString(),
            Objects.toString(user.get("employee_name"), username)
        );
    }

    private void validateSetupCredentials(String username, String password) {
        if (username.length() < 2 || username.length() > 80) throw ApiException.bad("用户名需为 2 至 80 个字符");
        if (password.length() < 8 || password.length() > 72) throw ApiException.bad("密码需为 8 至 72 个字符");
    }

    private long createEmployee(String username) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO employee(org_id,name,department,title,role,active,created_at) VALUES(1,?,'人才管理','HR','HR',TRUE,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, java.time.OffsetDateTime.now().toString());
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private long createUser(String username, String password, long employeeId) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO user(org_id,username,password,role,active,employee_id) VALUES(1,?,?,'ADMIN',TRUE,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, passwords.encode(password));
            statement.setLong(3, employeeId);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private String employeeName(Long employeeId, String fallback) {
        if (employeeId == null) return fallback;
        List<String> names = db.query("SELECT name FROM employee WHERE id=?", (rs, rowNum) -> rs.getString("name"), employeeId);
        return names.isEmpty() || names.get(0) == null || names.get(0).isBlank() ? fallback : names.get(0);
    }

    private Map<String, Object> loginResult(long id, String username, String role, String employeeName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwt.createToken(id));
        result.put("username", username);
        result.put("employeeName", employeeName);
        result.put("role", role);
        result.put("orgId", 1);
        return result;
    }

    private ApiException invalidCredentials() {
        return new ApiException(401, "INVALID_CREDENTIALS", "用户名或密码错误");
    }

}
