package com.example.demo.service.organization;

import com.example.demo.service.ApiException;
import com.example.demo.service.audit.AuditService;
import com.example.demo.service.CurrentUser;
import com.example.demo.service.SchemaMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class EmployeeService {
    private final JdbcTemplate db;
    private final CurrentUser user;
    private final SchemaMigration schema;
    private final BCryptPasswordEncoder passwords;
    private final AuditService audits;

    public EmployeeService(JdbcTemplate db, CurrentUser user, SchemaMigration schema, BCryptPasswordEncoder passwords, AuditService audits) {
        this.db = db;
        this.user = user;
        this.schema = schema;
        this.passwords = passwords;
        this.audits = audits;
    }

    public List<Map<String, Object>> employees() {
        Long current = db.queryForObject("SELECT employee_id FROM user WHERE id=? AND org_id=?", Long.class, user.id(), user.org());
        return db.query("SELECT e.id,e.employee_number,e.person_id,e.application_id,e.onboarding_id,e.job_id,e.name,e.department,e.title,e.role,e.status,e.start_date,e.end_date,e.active,u.username,u.active account_active,u.role account_role FROM employee e LEFT JOIN user u ON u.id=e.user_id AND u.employee_id=e.id WHERE e.org_id=? ORDER BY e.id", (row, number) -> {
            Map<String, Object> employee = new LinkedHashMap<>();
            employee.put("id", row.getLong("id")); employee.put("employeeNumber", row.getString("employee_number")); employee.put("personId", row.getObject("person_id"));
            employee.put("applicationId", row.getObject("application_id")); employee.put("onboardingId", row.getObject("onboarding_id")); employee.put("jobId", row.getObject("job_id"));
            employee.put("name", row.getString("name")); employee.put("department", row.getString("department")); employee.put("title", row.getString("title")); employee.put("role", row.getString("role"));
            employee.put("status", row.getString("status")); employee.put("startDate", row.getString("start_date")); employee.put("endDate", row.getString("end_date")); employee.put("active", row.getBoolean("active"));
            employee.put("username", row.getString("username")); employee.put("hasAccount", row.getString("username") != null); employee.put("accountActive", row.getObject("account_active") != null && row.getBoolean("account_active"));
            employee.put("accountRole", row.getString("account_role")); employee.put("current", Objects.equals(current, row.getLong("id")));
            return employee;
        }, user.org());
    }

    public List<Map<String, Object>> hrs() {
        schema.resolveHrId(user.org(), "");
        return employees().stream().filter(employee -> "HR".equals(text(employee, "role")) && Boolean.TRUE.equals(employee.get("active"))).toList();
    }

    public List<Map<String, Object>> companies() {
        return db.query("SELECT id,name,active FROM company WHERE org_id=? AND active=TRUE ORDER BY id", (row, number) -> {
            Map<String, Object> company = new LinkedHashMap<>(); long companyId = row.getLong("id");
            company.put("id", companyId); company.put("name", row.getString("name")); company.put("active", row.getBoolean("active")); company.put("locations", companyLocations(companyId));
            return company;
        }, user.org());
    }

    public List<Map<String, Object>> companyLocations(long companyId) {
        return db.query("SELECT id,base_city,district,street,office_address FROM company_location WHERE org_id=? AND company_id=? AND active=TRUE ORDER BY base_city,district,street,office_address", (row, number) -> {
            Map<String, Object> location = new LinkedHashMap<>();
            String base = Objects.toString(row.getString("base_city"), ""), district = Objects.toString(row.getString("district"), ""), street = Objects.toString(row.getString("street"), ""), address = Objects.toString(row.getString("office_address"), "");
            location.put("id", row.getLong("id")); location.put("baseCity", base); location.put("district", district); location.put("street", street); location.put("address", address); location.put("value", String.join(" · ", List.of(base, district, street, address)));
            return location;
        }, user.org(), companyId);
    }

    public Map<String, Object> company(long companyId) {
        return companies().stream().filter(company -> Objects.equals(id(company.get("id")), companyId)).findFirst().orElseThrow(() -> ApiException.bad("请选择公司表中的有效公司"));
    }

    public long employeeId(String name) {
        if (name == null || name.isBlank()) return schema.resolveHrId(user.org(), "");
        return hrs().stream().filter(employee -> name.equals(text(employee, "name"))).map(employee -> id(employee.get("id"))).findFirst().orElseThrow(() -> ApiException.bad("HR 必须从员工表中选择"));
    }

    public void normalizeOwner(Map<String, Object> body) {
        Long employee = id(body.get("ownerEmployeeId"));
        if (employee == null) employee = id(body.get("ownerHrId"));
        if (employee == null && !text(body, "owner").isBlank()) employee = employeeId(text(body, "owner"));
        if (employee == null) employee = employeeId("");
        long selected = employee;
        Map<String, Object> option = hrs().stream().filter(item -> Objects.equals(id(item.get("id")), selected)).findFirst().orElseThrow(() -> ApiException.bad("请选择员工表中的 HR"));
        body.put("ownerEmployeeId", selected); body.put("ownerHrId", selected); body.put("owner", option.get("name"));
    }

    @Transactional
    public Map<String, Object> changeRole(long employeeId, Map<String, Object> input) {
        user.requireAdmin();
        String role = text(input, "role").toUpperCase(Locale.ROOT);
        if (!Set.of("HR", "EMPLOYEE").contains(role)) throw ApiException.bad("角色只能选择 HR 或普通员工");
        List<Map<String, Object>> rows = db.queryForList("SELECT id,org_id,user_id,employee_number,name,role FROM employee WHERE id=? AND org_id=?", employeeId, user.org());
        if (rows.isEmpty()) throw ApiException.missing();
        Map<String, Object> employee = rows.get(0); Long accountId = id(employee.get("user_id")); Map<String, Object> result = new LinkedHashMap<>();
        if ("HR".equals(role)) {
            String username = "", initialPassword = "";
            if (accountId == null) {
                String base = "hr" + String.format("%04d", employeeId), candidate = base; int suffix = 2;
                while (Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM user WHERE username=?", Integer.class, candidate)) > 0) candidate = base + suffix++;
                username = candidate; initialPassword = "SetHub@" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
                GeneratedKeyHolder keys = new GeneratedKeyHolder(); String finalUsername = username, finalPassword = initialPassword;
                db.update(connection -> { PreparedStatement statement = connection.prepareStatement("INSERT INTO user(org_id,username,password,role,active,employee_id) VALUES(?,?,?,'HR',TRUE,?)", Statement.RETURN_GENERATED_KEYS); statement.setLong(1, user.org()); statement.setString(2, finalUsername); statement.setString(3, passwords.encode(finalPassword)); statement.setLong(4, employeeId); return statement; }, keys);
                accountId = Objects.requireNonNull(keys.getKey()).longValue(); db.update("UPDATE employee SET user_id=?,role='HR',title=CASE WHEN title IS NULL OR title='' THEN 'HR' ELSE title END WHERE id=? AND org_id=?", accountId, employeeId, user.org());
                result.put("accountCreated", true); result.put("username", username); result.put("initialPassword", initialPassword);
            } else {
                db.update("UPDATE user SET active=TRUE,role='HR' WHERE id=? AND employee_id=?", accountId, employeeId); db.update("UPDATE employee SET role='HR' WHERE id=? AND org_id=?", employeeId, user.org()); result.put("accountCreated", false);
            }
        } else {
            if (Objects.equals(db.queryForObject("SELECT employee_id FROM user WHERE id=?", Long.class, user.id()), employeeId)) throw ApiException.bad("不能把当前登录的 HR 改成普通员工，请先使用另一名 HR 账号登录");
            db.update("UPDATE employee SET role='EMPLOYEE' WHERE id=? AND org_id=?", employeeId, user.org()); if (accountId != null) db.update("UPDATE user SET role='EMPLOYEE' WHERE id=? AND employee_id=?", accountId, employeeId); result.put("accountRemoved", false);
        }
        result.put("employeeId", employeeId); result.put("role", role); audit("employee.role", Map.of("before", Map.of("role", employee.get("role")), "after", Map.of("role", role), "recordId", employeeId)); return result;
    }

    @Transactional
    public Map<String, Object> updateAccount(long employeeId, Map<String, Object> input) {
        user.requireAdmin();
        List<Map<String, Object>> rows = db.queryForList("SELECT e.id,e.user_id,e.role,e.title,u.username,u.role account_role,u.active account_active FROM employee e LEFT JOIN user u ON u.id=e.user_id WHERE e.id=? AND e.org_id=?", employeeId, user.org());
        if (rows.isEmpty()) throw ApiException.missing(); Map<String, Object> before = rows.get(0);
        String role = text(input, "role").toUpperCase(Locale.ROOT), title = text(input, "title").strip(), username = text(input, "username").strip(), password = text(input, "password");
        boolean enabled = !input.containsKey("accountEnabled") || Boolean.parseBoolean(Objects.toString(input.get("accountEnabled"), "true"));
        if (!Set.of("HR", "EMPLOYEE").contains(role)) throw ApiException.bad("系统角色只能选择 HR 或普通员工");
        if (title.isBlank()) throw ApiException.bad("请保留员工的职业，例如摄影师、设计师或 HR");
        Long accountId = id(before.get("user_id"));
        if (!enabled && Objects.equals(db.queryForObject("SELECT employee_id FROM user WHERE id=?", Long.class, user.id()), employeeId)) throw ApiException.bad("不能停用当前正在登录的账号");
        if (enabled) {
            if (username.isBlank() || !username.matches("^[A-Za-z0-9_.@-]{3,120}$")) throw ApiException.bad("登录账号需为 3—120 位字母、数字或常用符号");
            Integer duplicate = db.queryForObject("SELECT COUNT(*) FROM user WHERE username=? AND (? IS NULL OR id<>?)", Integer.class, username, accountId, accountId); if (duplicate != null && duplicate > 0) throw ApiException.bad("这个登录账号已被使用");
            String accountRole = "HR".equals(role) ? "HR" : "EMPLOYEE";
            if (accountId == null) {
                if (password.length() < 6) throw ApiException.bad("新账号密码至少 6 位"); GeneratedKeyHolder keys = new GeneratedKeyHolder();
                db.update(connection -> { PreparedStatement statement = connection.prepareStatement("INSERT INTO user(org_id,username,password,role,active,employee_id) VALUES(?,?,?,?,TRUE,?)", Statement.RETURN_GENERATED_KEYS); statement.setLong(1, user.org()); statement.setString(2, username); statement.setString(3, passwords.encode(password)); statement.setString(4, accountRole); statement.setLong(5, employeeId); return statement; }, keys); accountId = Objects.requireNonNull(keys.getKey()).longValue();
            } else {
                db.update("UPDATE user SET username=?,role=?,active=TRUE WHERE id=? AND employee_id=?", username, accountRole, accountId, employeeId); if (!password.isBlank()) { if (password.length() < 6) throw ApiException.bad("新密码至少 6 位"); db.update("UPDATE user SET password=? WHERE id=?", passwords.encode(password), accountId); }
            }
        } else if (accountId != null) db.update("UPDATE user SET active=FALSE WHERE id=? AND employee_id=?", accountId, employeeId);
        db.update("UPDATE employee SET role=?,title=?,user_id=? WHERE id=? AND org_id=?", role, title, accountId, employeeId, user.org());
        audit("employee.account", Map.of("before", Map.of("role", Objects.toString(before.get("role"), ""), "title", Objects.toString(before.get("title"), "")), "after", Map.of("role", role, "title", title, "accountEnabled", enabled), "recordId", employeeId));
        return employees().stream().filter(item -> Objects.equals(id(item.get("id")), employeeId)).findFirst().orElseThrow(ApiException::missing);
    }

    private void audit(String action, Map<String, Object> data) { audits.recordBusinessChange(user.org(), user.name(), action, data); }
    private String text(Map<String, Object> map, String key) { return Objects.toString(map.get(key), ""); }
    private Long id(Object value) { if (value == null || value.toString().isBlank()) return null; try { long id = Long.parseLong(value.toString()); return id > 0 ? id : null; } catch (NumberFormatException ignored) { return null; } }
}
