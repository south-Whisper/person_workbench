package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.*;

@Component
public class SchemaMigration {
    private static final LinkedHashMap<String,String> PERSON_COLUMNS=new LinkedHashMap<>();
    static {
        PERSON_COLUMNS.put("name","name VARCHAR(120)");PERSON_COLUMNS.put("nickname","nickname VARCHAR(120)");PERSON_COLUMNS.put("gender","gender VARCHAR(20)");
        PERSON_COLUMNS.put("phone","phone VARCHAR(80)");PERSON_COLUMNS.put("email","email VARCHAR(240)");PERSON_COLUMNS.put("wechat","wechat VARCHAR(120)");
        PERSON_COLUMNS.put("source","source VARCHAR(80)");PERSON_COLUMNS.put("jobId","job_id BIGINT");PERSON_COLUMNS.put("applyTime","apply_time VARCHAR(40)");
        PERSON_COLUMNS.put("salaryMin","salary_min DECIMAL(12,2)");PERSON_COLUMNS.put("salaryMax","salary_max DECIMAL(12,2)");PERSON_COLUMNS.put("status","status VARCHAR(60)");
        PERSON_COLUMNS.put("result","result VARCHAR(60)");PERSON_COLUMNS.put("reason","reason LONGTEXT");PERSON_COLUMNS.put("location","location VARCHAR(160)");
        PERSON_COLUMNS.put("workStatus","work_status VARCHAR(20)");PERSON_COLUMNS.put("company","company VARCHAR(240)");PERSON_COLUMNS.put("currentRole","current_position VARCHAR(240)");PERSON_COLUMNS.put("tags","tags LONGTEXT");
        PERSON_COLUMNS.put("experience","experience VARCHAR(120)");PERSON_COLUMNS.put("remark","remark LONGTEXT");PERSON_COLUMNS.put("nextStep","next_step VARCHAR(600)");
        PERSON_COLUMNS.put("nextContactAt","next_contact_at VARCHAR(40)");PERSON_COLUMNS.put("companyIntent","company_intent VARCHAR(40)");PERSON_COLUMNS.put("talentIntent","talent_intent VARCHAR(40)");
        PERSON_COLUMNS.put("birthday","birthday VARCHAR(40)");PERSON_COLUMNS.put("education","education VARCHAR(160)");PERSON_COLUMNS.put("website","website VARCHAR(500)");
        PERSON_COLUMNS.put("assets","assets LONGTEXT");PERSON_COLUMNS.put("experiences","experiences LONGTEXT");PERSON_COLUMNS.put("employments","employments LONGTEXT");PERSON_COLUMNS.put("compensations","compensations LONGTEXT");
        PERSON_COLUMNS.put("createdAt","created_at VARCHAR(40)");PERSON_COLUMNS.put("updatedAt","updated_at VARCHAR(40)");
    }
    private final JdbcTemplate db;
    private final DataSource source;
    private final JsonStore json;
    private final TransactionTemplate transactions;
    private final BCryptPasswordEncoder passwords;
    public SchemaMigration(JdbcTemplate db, DataSource source, JsonStore json, PlatformTransactionManager tx, BCryptPasswordEncoder passwords) {
        this.db=db; this.source=source; this.json=json; this.transactions=new TransactionTemplate(tx);this.passwords=passwords;
    }
    @PostConstruct
    public void initialize() {
        renamePrefixedTables();
        prepareRecordTables();
        db.execute("CREATE TABLE IF NOT EXISTS migration (version VARCHAR(80) PRIMARY KEY, applied_at VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS user (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, username VARCHAR(120) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, role VARCHAR(40) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, employee_id BIGINT UNIQUE)");
        migrateEmployees();
        if(!columnExists("user","employee_id"))db.execute("ALTER TABLE user ADD COLUMN employee_id BIGINT");
        if(!columnExists("user","active"))db.execute("ALTER TABLE user ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL");
        db.execute("CREATE TABLE IF NOT EXISTS company (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, created_at VARCHAR(40) NOT NULL, UNIQUE(org_id,name))");
        db.execute("CREATE TABLE IF NOT EXISTS company_location (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, company_id BIGINT NOT NULL, base_city VARCHAR(100) NOT NULL, district VARCHAR(120) NOT NULL, street VARCHAR(160) NOT NULL, office_address VARCHAR(240), active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(company_id,base_city,district,street,office_address))");
        db.execute("CREATE TABLE IF NOT EXISTS person (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, revision BIGINT NOT NULL, name VARCHAR(120), nickname VARCHAR(120), gender VARCHAR(20), phone VARCHAR(80), email VARCHAR(240), wechat VARCHAR(120), source VARCHAR(80), owner_employee_id BIGINT, job_id BIGINT, apply_time VARCHAR(40), salary_min DECIMAL(12,2), salary_max DECIMAL(12,2), status VARCHAR(60), result VARCHAR(60), reason LONGTEXT, location VARCHAR(160), work_status VARCHAR(20), company VARCHAR(240), current_position VARCHAR(240), tags LONGTEXT, experience VARCHAR(120), remark LONGTEXT, next_step VARCHAR(600), next_contact_at VARCHAR(40), company_intent VARCHAR(40), talent_intent VARCHAR(40), birthday VARCHAR(40), education VARCHAR(160), website VARCHAR(500), created_at VARCHAR(40), updated_at VARCHAR(40))");
        migratePersonBody();
        db.update("UPDATE person SET result='录用' WHERE result IN ('已入职','已离职')");
        db.execute("CREATE TABLE IF NOT EXISTS position (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, revision BIGINT NOT NULL, body LONGTEXT NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS profile_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, record_type VARCHAR(40) NOT NULL, revision BIGINT NOT NULL, body LONGTEXT, CONSTRAINT fk_profile_record_person FOREIGN KEY(person_id) REFERENCES person(id))");
        db.execute("CREATE TABLE IF NOT EXISTS record (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, application_id BIGINT, entity_type VARCHAR(60) NOT NULL, entity_id BIGINT, action VARCHAR(80) NOT NULL, title VARCHAR(500), summary LONGTEXT, result VARCHAR(160), reason LONGTEXT, import_batch VARCHAR(80), import_row BIGINT, occurred_at VARCHAR(40), actor VARCHAR(120), created_at VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS application (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, revision BIGINT NOT NULL, owner_employee_id BIGINT, CONSTRAINT fk_application_person FOREIGN KEY(person_id) REFERENCES person(id))");
        db.execute("CREATE TABLE IF NOT EXISTS communication (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, revision BIGINT NOT NULL, owner_employee_id BIGINT)");
        db.execute("CREATE TABLE IF NOT EXISTS interview (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, revision BIGINT NOT NULL, owner_employee_id BIGINT)");
        db.execute("CREATE TABLE IF NOT EXISTS offer (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, revision BIGINT NOT NULL, owner_employee_id BIGINT, version BIGINT)");
        db.execute("CREATE TABLE IF NOT EXISTS onboarding (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, person_id BIGINT NOT NULL, application_id BIGINT, employment_record_id BIGINT UNIQUE, revision BIGINT NOT NULL DEFAULT 1, created_at VARCHAR(40) NOT NULL, updated_at VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS message (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, recipient_employee_id BIGINT NOT NULL, category VARCHAR(80) NOT NULL, title VARCHAR(300) NOT NULL, summary LONGTEXT, status VARCHAR(80), person_id BIGINT, interview_id BIGINT, source_key VARCHAR(160), is_read BOOLEAN NOT NULL DEFAULT FALSE, read_at VARCHAR(40), created_at VARCHAR(40) NOT NULL, UNIQUE(org_id,recipient_employee_id,source_key))");
        db.execute("CREATE TABLE IF NOT EXISTS questionnaire_invitation (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE, recipient_email VARCHAR(240) NOT NULL, recipient_name VARCHAR(120), job_id BIGINT NOT NULL, owner_employee_id BIGINT NOT NULL, status VARCHAR(40) NOT NULL DEFAULT '待填写', expires_at VARCHAR(40) NOT NULL, submitted_at VARCHAR(40), person_id BIGINT, application_id BIGINT, created_at VARCHAR(40) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS mail_setting (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, company_id BIGINT, provider VARCHAR(80) NOT NULL, host VARCHAR(240) NOT NULL, port INT NOT NULL, username VARCHAR(240) NOT NULL, password_cipher LONGTEXT, sender_email VARCHAR(240) NOT NULL, ssl_enabled BOOLEAN NOT NULL DEFAULT TRUE, starttls_enabled BOOLEAN NOT NULL DEFAULT FALSE, updated_at VARCHAR(40) NOT NULL, updated_by VARCHAR(120) NOT NULL, UNIQUE KEY uq_mail_setting_company(org_id,company_id))");
        migrateMailSettings();
        db.execute("CREATE TABLE IF NOT EXISTS product_release (id BIGINT AUTO_INCREMENT PRIMARY KEY, version VARCHAR(40) NOT NULL UNIQUE, release_date VARCHAR(40) NOT NULL, title VARCHAR(240) NOT NULL, summary LONGTEXT NOT NULL, changes_json LONGTEXT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, created_at VARCHAR(40) NOT NULL)");
        if(!columnExists("product_release","active"))db.execute("ALTER TABLE product_release ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE");
        initializeProductReleases();
        db.execute("CREATE TABLE IF NOT EXISTS audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, actor VARCHAR(120) NOT NULL, action VARCHAR(80) NOT NULL, created_at VARCHAR(40) NOT NULL, body LONGTEXT NOT NULL)");
        backfillPersonRecords();
        migrateStructuredBodies();
        db.update("UPDATE position SET headcount=1 WHERE headcount IS NULL OR headcount<1");
        migrateImportKeys();
        if (db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='schema-v1'", Integer.class)==0) {
            db.update("INSERT INTO migration(version,applied_at) VALUES('schema-v1',?)", now());
        }
        transactions.executeWithoutResult(tx -> {
            db.queryForObject("SELECT version FROM migration WHERE version='schema-v1' FOR UPDATE", String.class);
            if (db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='legacy-v1'", Integer.class)>0) return;
            importLegacy();
            db.update("INSERT INTO migration(version,applied_at) VALUES('legacy-v1',?)", now());
        });
        migrateApplicationTable();
        backfillMissingApplications();
        backfillEmploymentEmployees();
        linkUsersAndEmployees();
        ensureInitialHrAccount();
        migrateInitialAdminPassword();
        initializeReferenceData();
        migrateMessages();
        migrateOtherMessages();
        migrateProfileRecordsIntoPerson();
        finalizeOnboardingAndEmployeeLinks();
    }

    private void renamePrefixedTables() {
        for(String table:List.of("migration","user","inbox_read","message","employee","hr","company","company_location","person","position","record","profile_record","application","communication","interview","position_version","audit","audit_change","import_key","offer","staff"))
            if(tableExists("sh_"+table)&&!tableExists(table))db.execute("ALTER TABLE sh_"+table+" RENAME TO "+table);
    }

    private void prepareRecordTables(){
        if(tableExists("record")&&!columnExists("record","entity_type")&&!tableExists("profile_record"))db.execute("ALTER TABLE record RENAME TO profile_record");
    }

    private void migrateImportKeys() {
        if(tableExists("import_key")) {
            for(Map<String,Object> row:db.queryForList("SELECT org_id,batch_id,`row_number`,person_id FROM import_key"))insertImportRecord(row.get("org_id"),row.get("person_id"),row.get("batch_id"),row.get("row_number"));
            db.execute("DROP TABLE import_key");
        }
        if(columnExists("person","import_batch")&&columnExists("person","import_row")) {
            for(Map<String,Object> row:db.queryForList("SELECT org_id,id person_id,import_batch,import_row FROM person WHERE import_batch IS NOT NULL AND import_batch<>'' AND import_row IS NOT NULL"))insertImportRecord(row.get("org_id"),row.get("person_id"),row.get("import_batch"),row.get("import_row"));
            db.execute("ALTER TABLE person DROP COLUMN import_batch");db.execute("ALTER TABLE person DROP COLUMN import_row");
        }
    }

    private void insertImportRecord(Object org,Object personId,Object batch,Object row) {
        Integer exists=db.queryForObject("SELECT COUNT(*) FROM record WHERE org_id=? AND import_batch=? AND import_row=?",Integer.class,org,batch,row);if(exists!=null&&exists>0)return;
        String timestamp=now();db.update("INSERT INTO record(org_id,person_id,entity_type,action,title,summary,result,import_batch,import_row,occurred_at,actor,created_at) VALUES(?,?,'person','import','批量导入人才','记录导入批次与源文件行号','已记录',?,?,?,'系统迁移',?)",org,personId,batch,row,timestamp,timestamp);
    }

    private void linkUsersAndEmployees() {
        for(Map<String,Object> row:db.queryForList("SELECT id,employee_id,role FROM user WHERE employee_id IS NOT NULL"))
            db.update("UPDATE employee SET user_id=?,role=? WHERE id=? AND (user_id IS NULL OR user_id=?)",row.get("id"),Set.of("ADMIN","HR").contains(Objects.toString(row.get("role"),""))?"HR":"EMPLOYEE",row.get("employee_id"),row.get("id"));
        for(Map<String,Object> account:db.queryForList("SELECT id,org_id,username,role FROM user WHERE employee_id IS NULL")) {
            long userId=((Number)account.get("id")).longValue(),org=((Number)account.get("org_id")).longValue();String username=Objects.toString(account.get("username"),"HR");
            List<Long> matches=db.query("SELECT id FROM employee WHERE org_id=? AND name=? AND user_id IS NULL ORDER BY id LIMIT 1",(r,n)->r.getLong(1),org,username);long employeeId;
            if(!matches.isEmpty())employeeId=matches.get(0);else{GeneratedKeyHolder keys=new GeneratedKeyHolder();db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO employee(org_id,name,department,title,role,active,created_at) VALUES(?,?,? ,'HR','HR',TRUE,?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,org);p.setString(2,username);p.setString(3,"人才管理");p.setString(4,now());return p;},keys);employeeId=Objects.requireNonNull(keys.getKey()).longValue();}
            db.update("UPDATE user SET employee_id=?,active=TRUE WHERE id=?",employeeId,userId);db.update("UPDATE employee SET user_id=?,role='HR' WHERE id=?",userId,employeeId);
        }
    }

    private void migrateMailSettings(){
        if(!columnExists("mail_setting","company_id"))db.execute("ALTER TABLE mail_setting ADD COLUMN company_id BIGINT");
        List<String> oldUnique=db.query("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mail_setting' AND COLUMN_NAME='org_id' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'",(r,n)->r.getString(1));
        for(String index:oldUnique)if(!"uq_mail_setting_company".equals(index))db.execute("ALTER TABLE mail_setting DROP INDEX `"+index.replace("`","")+"`");
        Integer exists=db.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mail_setting' AND INDEX_NAME='uq_mail_setting_company'",Integer.class);
        if(exists==null||exists==0)db.execute("ALTER TABLE mail_setting ADD UNIQUE KEY uq_mail_setting_company(org_id,company_id)");
        db.update("UPDATE mail_setting m SET company_id=(SELECT MIN(c.id) FROM company c WHERE c.org_id=m.org_id AND c.active=TRUE) WHERE company_id IS NULL");
    }

    private void initializeProductReleases(){
        insertRelease("V1.0.1","2026-10-01","人才招聘全流程基础版本","完成从人才建档、沟通、面试、Offer 到入职和员工管理的完整流程，并统一后端结构、岗位名额和版本记录。",List.of(
                "人才库、问卷、沟通、面试、Offer、入职、岗位、员工和系统设置形成完整工作流。",
                "证件照头像、表格与卡片样式、岗位名额和已招人数在各页面统一展示。",
                "Offer 邮件支持候选人直接接受或拒绝，版本信息改由数据库统一管理。",
                "后端按业务职责整理，附件、邮件、认证等公共能力合并复用。",
                "补充总体架构图、ER 图、时序图、接口用途和部署脚本。"
        ));
        insertRelease("V1.0.2","2026-10-01","面试、Offer 与通知流程优化","精简页面信息，完善面试结果、Offer 回复、入职流转和消息提醒。",List.of(
                "面试卡片和详情页统一为清晰的中性色；评分改为 1—5 分按钮。",
                "沟通中安排面试时可单独选择面试官，已完成面试可直接发 Offer 或新增下一轮面试。",
                "Offer 支持按 PDF 打印保存；接受后直接进入入职流程，拒绝需要二次确认。",
                "入职页只显示已接受 Offer 的人才，Offer 发送或草稿不再提前出现。",
                "消息信箱只保留人才已填写问卷、面试安排和 Offer 已确认三类提醒。",
                "移除顶部搜索框回车提示，并合并人才列表中重复的已入职与录用状态。"
        ));
        db.update("UPDATE product_release SET active=FALSE");
        db.update("UPDATE product_release SET active=TRUE WHERE version IN ('V1.0.1','V1.0.2')");
    }

    private void insertRelease(String version,String date,String title,String summary,List<String> changes){
        Integer exists=db.queryForObject("SELECT COUNT(*) FROM product_release WHERE version=?",Integer.class,version);
        String changesJson=json.write(Map.of("items",changes));
        if(exists!=null&&exists>0){db.update("UPDATE product_release SET release_date=?,title=?,summary=?,changes_json=?,active=TRUE WHERE version=?",date,title,summary,changesJson,version);return;}
        db.update("INSERT INTO product_release(version,release_date,title,summary,changes_json,active,created_at) VALUES(?,?,?,?,?,TRUE,?)",version,date,title,summary,changesJson,now());
    }

    private void ensureInitialHrAccount() {
        if(Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM user",Integer.class))>0)return;
        String timestamp=now();GeneratedKeyHolder employeeKey=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO employee(org_id,name,department,title,role,status,active,created_at) VALUES(1,'昱宁','人才管理','HR','HR','在职',TRUE,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,timestamp);return p;},employeeKey);
        long employeeId=Objects.requireNonNull(employeeKey.getKey()).longValue();GeneratedKeyHolder userKey=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO user(org_id,username,password,role,active,employee_id) VALUES(1,'admin',?,'ADMIN',TRUE,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,passwords.encode("123456"));p.setLong(2,employeeId);return p;},userKey);
        db.update("UPDATE employee SET user_id=? WHERE id=?",Objects.requireNonNull(userKey.getKey()).longValue(),employeeId);
    }

    private void migrateInitialAdminPassword() {
        List<Map<String,Object>> admins=db.queryForList("SELECT id,password FROM user WHERE username='admin' ORDER BY id LIMIT 1");
        if(admins.isEmpty())return;
        Map<String,Object> admin=admins.get(0);
        String current=Objects.toString(admin.get("password"),"");
        if(passwords.matches("SetHub@123456",current))
            db.update("UPDATE user SET password=? WHERE id=?",passwords.encode("123456"),admin.get("id"));
    }

    private void initializeReferenceData() {
        transactions.executeWithoutResult(tx -> {
            db.queryForObject("SELECT version FROM migration WHERE version='schema-v1' FOR UPDATE", String.class);
            boolean initialized=db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='initial-reference-data-v1'", Integer.class)>0;
            boolean repairBrokenFreshInitialization=initialized&&shouldRepairBrokenFreshInitialization();
            if (initialized&&!repairBrokenFreshInitialization) return;
            if(repairBrokenFreshInitialization)repairBrokenFreshInitialization();
            Connection connection=DataSourceUtils.getConnection(source);
            try {
                ScriptUtils.executeSqlScript(connection,new EncodedResource(new ClassPathResource("db/initial-data.sql"),StandardCharsets.UTF_8));
                if(!initialized)db.update("INSERT INTO migration(version,applied_at) VALUES('initial-reference-data-v1',?)",now());
            } finally {
                DataSourceUtils.releaseConnection(connection,source);
            }
        });
    }

    private boolean shouldRepairBrokenFreshInitialization() {
        if(!"1".equals(String.valueOf(db.queryForObject("SELECT COUNT(*) FROM company",Integer.class))))return false;
        if(!"5".equals(String.valueOf(db.queryForObject("SELECT COUNT(*) FROM company_location",Integer.class))))return false;
        if(!"0".equals(String.valueOf(db.queryForObject("SELECT COUNT(*) FROM position",Integer.class))))return false;
        for(String table:List.of("person","application","communication","interview","offer","onboarding"))
            if(Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class))>0)return false;
        String companyName=db.queryForObject("SELECT name FROM company ORDER BY id LIMIT 1",String.class);
        return !"南通宣通文化投资".equals(companyName);
    }

    private void repairBrokenFreshInitialization() {
        Long companyId=db.queryForObject("SELECT id FROM company ORDER BY id LIMIT 1",Long.class);
        db.update("UPDATE company SET name='南通宣通文化投资' WHERE id=?",companyId);
        List<Long> locationIds=db.query("SELECT id FROM company_location WHERE company_id=? ORDER BY id",(rs,rowNum)->rs.getLong(1),companyId);
        List<String[]> locations=List.of(
            new String[]{"南通","崇川区","文峰街道","青年中路"},
            new String[]{"南通","崇川区","新城桥街道","工农南路"},
            new String[]{"南通","崇川区","狼山镇街道","长江南路"},
            new String[]{"南通","通州区","金沙街道","朝霞路"},
            new String[]{"上海","浦东新区","陆家嘴街道","世纪大道"}
        );
        for(int i=0;i<locationIds.size();i++){
            String[] location=locations.get(i);
            db.update("UPDATE company_location SET base_city=?,district=?,street=?,office_address=? WHERE id=?",location[0],location[1],location[2],location[3],locationIds.get(i));
        }
    }

    public void syncEmployeeFromEmployment(long org,long personId,long ignoredEmploymentItemId,Map<String,Object> employment) {
        Long applicationId=number(employment.get("applicationId")),jobId=number(employment.get("jobId"));String status=Objects.toString(employment.get("status"),"试用期");
        String name=Objects.toString(employment.get("employeeName"),"");if(name.isBlank())name=db.queryForObject("SELECT name FROM person WHERE id=? AND org_id=?",String.class,personId,org);
        String department="",title="";if(jobId!=null){List<Map<String,Object>> jobs=db.queryForList("SELECT department,name FROM position WHERE id=? AND org_id=?",jobId,org);if(!jobs.isEmpty()){department=Objects.toString(jobs.get(0).get("department"),"");title=Objects.toString(jobs.get(0).get("name"),"");}}
        String startDate=Objects.toString(employment.get("startDate"),LocalDate.now().toString()),endDate=Objects.toString(employment.get("endDate"),"");if(endDate.isBlank())endDate=null;boolean active=!"已离职".equals(status);String timestamp=now();
        Long onboardingId=applicationId==null?null:db.query("SELECT id FROM onboarding WHERE org_id=? AND person_id=? AND application_id=? ORDER BY id DESC LIMIT 1",(r,n)->r.getLong(1),org,personId,applicationId).stream().findFirst().orElse(null);
        List<Long> existing=applicationId==null?db.query("SELECT id FROM employee WHERE org_id=? AND person_id=? ORDER BY id LIMIT 1",(r,n)->r.getLong(1),org,personId):db.query("SELECT id FROM employee WHERE org_id=? AND person_id=? AND application_id=? ORDER BY id LIMIT 1",(r,n)->r.getLong(1),org,personId,applicationId);
        if(!existing.isEmpty()){db.update("UPDATE employee SET onboarding_id=?,person_id=?,application_id=?,job_id=?,name=?,department=?,title=?,status=?,start_date=?,end_date=?,active=? WHERE id=?",onboardingId,personId,applicationId,jobId,name,department,title,status,startDate,endDate,active,existing.get(0));return;}
        String employeeNumber=nextEmployeeNumber(org);GeneratedKeyHolder keys=new GeneratedKeyHolder();String finalName=name,finalDepartment=department,finalTitle=title,finalEndDate=endDate;
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO employee(org_id,employee_number,person_id,application_id,onboarding_id,job_id,name,department,title,role,status,start_date,end_date,active,created_at) VALUES(?,?,?,?,?,?,?,?,?,'EMPLOYEE',?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);int i=1;p.setLong(i++,org);p.setString(i++,employeeNumber);p.setLong(i++,personId);p.setObject(i++,applicationId);p.setObject(i++,onboardingId);p.setObject(i++,jobId);p.setString(i++,finalName);p.setString(i++,finalDepartment);p.setString(i++,finalTitle);p.setString(i++,status);p.setString(i++,startDate);p.setObject(i++,finalEndDate);p.setBoolean(i++,active);p.setString(i,timestamp);return p;},keys);
    }

    private String nextEmployeeNumber(long org) {
        int sequence=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*)+1 FROM employee WHERE org_id=? AND role='EMPLOYEE'",Integer.class,org));String value;
        do {value=String.format("E%06d",sequence++);} while(Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM employee WHERE org_id=? AND employee_number=?",Integer.class,org,value))>0);return value;
    }

    private void backfillEmploymentEmployees() {
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,person_id,application_id,job_id,status,start_date,end_date,employee_name,created_at,updated_at FROM profile_record WHERE record_type='employments' ORDER BY id")) {
            Map<String,Object> employment=new LinkedHashMap<>();employment.put("applicationId",row.get("application_id"));employment.put("jobId",row.get("job_id"));employment.put("status",row.get("status"));employment.put("startDate",row.get("start_date"));employment.put("endDate",row.get("end_date"));employment.put("employeeName",row.get("employee_name"));employment.put("createdAt",row.get("created_at"));employment.put("updatedAt",row.get("updated_at"));
            syncEmployeeFromEmployment(((Number)row.get("org_id")).longValue(),((Number)row.get("person_id")).longValue(),((Number)row.get("id")).longValue(),employment);
        }
    }
    private void backfillPersonRecords() {
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='person-record-links-v3'",Integer.class)>0)return;
        Set<String> hrNames=new LinkedHashSet<>(db.queryForList("SELECT name FROM employee WHERE role='HR'",String.class));
        for(Map<String,Object> row:db.queryForList("SELECT p.*,h.name owner_name FROM person p LEFT JOIN employee h ON h.id=p.owner_employee_id")) {
            long personId=((Number)row.get("id")).longValue(),org=((Number)row.get("org_id")).longValue();String owner=Objects.toString(row.get("owner_name"),"");
            for(Map<String,Object> record:db.queryForList("SELECT id,body FROM profile_record WHERE person_id=? AND org_id=?",personId,org)) {Map<String,Object> body=json.read(Objects.toString(record.get("body"),"{}"));if(!Objects.toString(body.get("owner"),"").isBlank()&&!hrNames.contains(Objects.toString(body.get("owner"),""))){body.put("owner",owner);db.update("UPDATE profile_record SET body=? WHERE id=?",json.write(body),record.get("id"));}}
            if(row.get("salary_min")!=null&&db.queryForObject("SELECT COUNT(*) FROM profile_record WHERE person_id=? AND org_id=? AND record_type='compensations'",Integer.class,personId,org)==0){Map<String,Object> compensation=new LinkedHashMap<>();compensation.put("type","候选人公开期望");compensation.put("salaryMin",row.get("salary_min"));compensation.put("salaryMax",row.get("salary_max"));compensation.put("occurredAt",Objects.toString(row.get("apply_time"),now().substring(0,10)));compensation.put("source","人才档案原始信息");compensation.put("remark","由人才档案中的期望薪资自动补全");compensation.put("currency","CNY");compensation.put("period","月");compensation.put("personId",personId);compensation.put("createdAt",now());compensation.put("updatedAt",now());compensation.put("createdBy","系统迁移");compensation.put("revision",1);List<Long> applications=db.query("SELECT id FROM application WHERE person_id=? AND org_id=? ORDER BY id DESC LIMIT 1",(r,n)->r.getLong(1),personId,org);if(!applications.isEmpty())compensation.put("applicationId",applications.get(0));db.update("INSERT INTO profile_record(org_id,person_id,record_type,revision,body) VALUES(?,?,'compensations',1,?)",org,personId,json.write(compensation));}
        }
        db.update("INSERT INTO migration(version,applied_at) VALUES('person-record-links-v3',?)",now());
    }
    private void migrateMessages(){
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='message-table-v1'",Integer.class)==0){
            for(Map<String,Object> row:db.queryForList("SELECT a.id,a.org_id,a.person_id,a.created_at,p.owner_employee_id,p.name,p.phone,p.email FROM audit a JOIN person p ON p.id=a.person_id AND p.org_id=a.org_id WHERE a.action='questionnaire.submit'")){
                Long recipient=number(row.get("owner_employee_id"));if(recipient==null)continue;String contact=Objects.toString(row.get("phone"),"");if(contact.isBlank())contact=Objects.toString(row.get("email"),"");
                insertMessageIfMissing(number(row.get("org_id")),recipient,"人才问卷","收到新的问卷结果",Objects.toString(row.get("name"),"未命名人才")+(contact.isBlank()?" 已提交人才资料":" · "+contact),"待查看",number(row.get("person_id")),null,"questionnaire-"+row.get("id"),Objects.toString(row.get("created_at"),now()));
            }
            for(Map<String,Object> row:db.queryForList("SELECT i.id,i.org_id,i.owner_employee_id,i.scheduled_at,i.updated_at,i.status,i.interview_round,p.name FROM interview i JOIN person p ON p.id=i.person_id AND p.org_id=i.org_id")){
                Long recipient=number(row.get("owner_employee_id"));if(recipient==null)continue;String summary=Objects.toString(row.get("interview_round"),"面试")+" · "+Objects.toString(row.get("scheduled_at"),"时间待定");
                insertMessageIfMissing(number(row.get("org_id")),recipient,"面试安排",Objects.toString(row.get("name"),"人才")+"的面试",summary,Objects.toString(row.get("status"),"待面试"),null,number(row.get("id")),"interview-"+row.get("id"),Objects.toString(row.get("updated_at"),Objects.toString(row.get("scheduled_at"),now())));
            }
            if(tableExists("inbox_read"))for(Map<String,Object> row:db.queryForList("SELECT r.org_id,r.user_id,r.message_id,r.read_at,u.employee_id FROM inbox_read r JOIN user u ON u.id=r.user_id AND u.org_id=r.org_id WHERE u.employee_id IS NOT NULL"))db.update("UPDATE message SET is_read=TRUE,read_at=? WHERE org_id=? AND recipient_employee_id=? AND source_key=?",row.get("read_at"),row.get("org_id"),row.get("employee_id"),row.get("message_id"));
            db.update("INSERT INTO migration(version,applied_at) VALUES('message-table-v1',?)",now());
        }
        if(tableExists("inbox_read"))db.execute("DROP TABLE inbox_read");
    }
    private void insertMessageIfMissing(Long org,Long recipient,String category,String title,String summary,String status,Long personId,Long interviewId,String sourceKey,String createdAt){
        if(org==null||recipient==null)return;Integer exists=db.queryForObject("SELECT COUNT(*) FROM message WHERE org_id=? AND recipient_employee_id=? AND source_key=?",Integer.class,org,recipient,sourceKey);if(exists!=null&&exists>0)return;
        db.update("INSERT INTO message(org_id,recipient_employee_id,category,title,summary,status,person_id,interview_id,source_key,is_read,created_at) VALUES(?,?,?,?,?,?,?,?,?,FALSE,?)",org,recipient,category,title,summary,status,personId,interviewId,sourceKey,createdAt);
    }
    private void migrateOtherMessages(){
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='message-other-sources-v2'",Integer.class)>0)return;
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,owner_employee_id,name,next_step,next_contact_at,updated_at FROM person WHERE owner_employee_id IS NOT NULL AND next_contact_at IS NOT NULL AND next_contact_at<>''"))insertMessageIfMissing(number(row.get("org_id")),number(row.get("owner_employee_id")),"跟进提醒",Objects.toString(row.get("name"),"人才")+"需要跟进",Objects.toString(row.get("next_step"),"查看人才档案并安排下一步"),"待处理",null,null,"followup-"+row.get("id")+"-"+row.get("next_contact_at"),Objects.toString(row.get("next_contact_at"),Objects.toString(row.get("updated_at"),now())));
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,owner_employee_id,job_name,status,expires_at,updated_at FROM offer WHERE owner_employee_id IS NOT NULL AND current_record=TRUE"))insertMessageIfMissing(number(row.get("org_id")),number(row.get("owner_employee_id")),"Offer","最新 Offer 状态",Objects.toString(row.get("job_name"),"岗位待补充")+" · 有效期至 "+Objects.toString(row.get("expires_at"),"待补充"),Objects.toString(row.get("status"),"已创建"),null,null,"offer-"+row.get("id"),Objects.toString(row.get("updated_at"),now()));
        db.update("INSERT INTO migration(version,applied_at) VALUES('message-other-sources-v2',?)",now());
    }
    private void migrateProfileRecordsIntoPerson(){
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='profile-records-in-person-v1'",Integer.class)>0)return;
        for(Map<String,Object> person:db.queryForList("SELECT id,org_id FROM person")){
            long personId=((Number)person.get("id")).longValue(),org=((Number)person.get("org_id")).longValue();Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();for(String type:List.of("assets","experiences","employments","compensations"))groups.put(type,new ArrayList<>());
            for(Map<String,Object> row:db.queryForList("SELECT r.*,e.name owner_name FROM profile_record r LEFT JOIN employee e ON e.id=r.owner_employee_id AND e.org_id=r.org_id WHERE r.person_id=? AND r.org_id=? AND r.record_type IN ('assets','experiences','employments','compensations') ORDER BY r.id",personId,org)){
                String type=Objects.toString(row.get("record_type"),"");Map<String,Object> item=new LinkedHashMap<>();item.put("id",row.get("id"));item.put("revision",row.get("revision"));item.put("personId",personId);for(Map.Entry<String,String> entry:StructuredColumns.RECORD.entrySet()){Object value=row.get(StructuredColumns.column(entry.getValue()));if(value!=null)item.put(entry.getKey(),value);}if(row.get("owner_name")!=null)item.put("owner",row.get("owner_name"));
                if("employments".equals(type)&&columnExists("onboarding","employment_record_id")){List<Map<String,Object>> onboarding=db.queryForList("SELECT * FROM onboarding WHERE org_id=? AND employment_record_id=?",org,row.get("id"));if(!onboarding.isEmpty()){Map<String,Object> form=onboarding.get(0);item.put("onboardingId",form.get("id"));for(Map.Entry<String,String> entry:StructuredColumns.ONBOARDING.entrySet()){Object value=form.get(StructuredColumns.column(entry.getValue()));if(value!=null)item.put(entry.getKey(),value);}}}
                groups.get(type).add(item);
            }
            db.update("UPDATE person SET assets=?,experiences=?,employments=?,compensations=? WHERE id=? AND org_id=?",json.write(groups.get("assets")),json.write(groups.get("experiences")),json.write(groups.get("employments")),json.write(groups.get("compensations")),personId,org);
        }
        db.update("INSERT INTO migration(version,applied_at) VALUES('profile-records-in-person-v1',?)",now());
    }
    private void finalizeOnboardingAndEmployeeLinks(){
        if(columnExists("employee","employment_record_id")){if(columnExists("onboarding","employment_record_id"))db.update("UPDATE employee e SET onboarding_id=(SELECT MAX(o.id) FROM onboarding o WHERE o.org_id=e.org_id AND o.employment_record_id=e.employment_record_id) WHERE e.employment_record_id IS NOT NULL");db.execute("ALTER TABLE employee DROP COLUMN employment_record_id");}
        if(columnExists("onboarding","employment_record_id")){dropForeignKeys("onboarding","employment_record_id");db.execute("ALTER TABLE onboarding DROP COLUMN employment_record_id");}
        if(tableExists("profile_record"))db.execute("DROP TABLE profile_record");
    }
    private void dropForeignKeys(String table,String column){
        List<String> names=new ArrayList<>();try(Connection connection=source.getConnection();ResultSet keys=connection.getMetaData().getImportedKeys(connection.getCatalog(),null,table)){while(keys.next())if(column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))&&keys.getString("FK_NAME")!=null)names.add(keys.getString("FK_NAME"));}catch(SQLException e){throw new IllegalStateException("无法检查旧外键",e);}for(String name:names){try{db.execute("ALTER TABLE "+table+" DROP CONSTRAINT `"+name+"`");}catch(Exception ignored){db.execute("ALTER TABLE "+table+" DROP FOREIGN KEY `"+name+"`");}}
    }
    private void migrateEmployees(){
        if(tableExists("hr")&&!tableExists("employee"))db.execute("ALTER TABLE hr RENAME TO employee");
        db.execute("CREATE TABLE IF NOT EXISTS employee (id BIGINT AUTO_INCREMENT PRIMARY KEY, org_id BIGINT NOT NULL, user_id BIGINT UNIQUE, employee_number VARCHAR(40), person_id BIGINT, application_id BIGINT, onboarding_id BIGINT, job_id BIGINT, name VARCHAR(120) NOT NULL, department VARCHAR(120), title VARCHAR(120), role VARCHAR(40), status VARCHAR(40), start_date VARCHAR(40), end_date VARCHAR(40), active BOOLEAN NOT NULL DEFAULT TRUE, created_at VARCHAR(40) NOT NULL)");
        if(!columnExists("employee","user_id"))db.execute("ALTER TABLE employee ADD COLUMN user_id BIGINT");
        for(String definition:List.of("employee_number VARCHAR(40)","person_id BIGINT","application_id BIGINT","onboarding_id BIGINT","job_id BIGINT","status VARCHAR(40)","start_date VARCHAR(40)","end_date VARCHAR(40)")){String column=definition.substring(0,definition.indexOf(' '));if(!columnExists("employee",column))db.execute("ALTER TABLE employee ADD COLUMN "+definition);}
        if(!columnExists("employee","role"))db.execute("ALTER TABLE employee ADD COLUMN role VARCHAR(40)");
        db.update("UPDATE employee SET role='HR' WHERE role IS NULL OR role=''");
        db.update("UPDATE employee SET title='HR' WHERE role='HR' AND (title IS NULL OR title='' OR title='招聘负责人' OR title='负责人')");
    }
    private void migrateStructuredBodies(){
        db.execute("CREATE TABLE IF NOT EXISTS audit_change (id BIGINT AUTO_INCREMENT PRIMARY KEY, audit_id BIGINT NOT NULL, field_name VARCHAR(120) NOT NULL, before_value LONGTEXT, after_value LONGTEXT)");
        migratePositionTable("position",false);if(tableExists("position_version"))migratePositionTable("position_version",true);consolidatePositionVersions();backfillPositionIdentity();migrateRecordTable();migrateApplicationTable();migrateDedicatedProcessTables();migrateOnboardingTable();migrateAuditTable();recoverDedicatedProcessRows();
        db.update("UPDATE offer SET salary_mode='monthly' WHERE salary_mode IS NULL OR salary_mode=''");
        db.update("UPDATE offer SET annual_salary=actual_salary*salary_months WHERE annual_salary IS NULL AND actual_salary IS NOT NULL AND salary_months IS NOT NULL");
        db.update("UPDATE offer SET validity_days=7 WHERE validity_days IS NULL");
        ensureColumns("offer",StructuredColumns.OFFER_RESPONSE);
        db.update("UPDATE offer SET response_status='待回复' WHERE status='已完成' AND (response_status IS NULL OR response_status='')");
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='all-body-columns-v4'",Integer.class)==0)db.update("INSERT INTO migration(version,applied_at) VALUES('all-body-columns-v4',?)",now());
    }
    private void ensureColumns(String table,LinkedHashMap<String,String> columns){for(String definition:columns.values()){String column=StructuredColumns.column(definition);if(!columnExists(table,column))db.execute("ALTER TABLE "+table+" ADD COLUMN "+definition);}}
    private void migrateApplicationTable(){
        ensureColumns("application",StructuredColumns.RECORD);
        String columns=StructuredColumns.RECORD.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));
        db.update("INSERT INTO application(id,org_id,person_id,revision,owner_employee_id,"+columns+") SELECT r.id,r.org_id,r.person_id,r.revision,r.owner_employee_id,"+columns+" FROM profile_record r WHERE r.record_type='applications' AND NOT EXISTS (SELECT 1 FROM application a WHERE a.id=r.id)");
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='application-table-v1'",Integer.class)==0)db.update("INSERT INTO migration(version,applied_at) VALUES('application-table-v1',?)",now());
    }
    private void backfillMissingApplications(){
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='missing-applications-v6'",Integer.class)>0)return;
        String timestamp=now();
        db.update("INSERT INTO application(org_id,person_id,revision,owner_employee_id,job_id,job_name,job_version,company,job_code,base_location,status,company_intent,talent_intent,started_at,created_at,updated_at,created_by) " +
            "SELECT p.org_id,p.id,1,p.owner_employee_id,p.job_id,j.name,j.revision,j.company,j.recruitment_code,j.base_location," +
            "CASE WHEN p.status IN ('面试中','Offer中','待入职','已入职','人才储备') THEN p.status ELSE '沟通中' END," +
            "COALESCE(NULLIF(p.company_intent,''),'未判断'),COALESCE(NULLIF(p.talent_intent,''),'未知'),COALESCE(NULLIF(p.apply_time,''),NULLIF(p.created_at,''),?),COALESCE(NULLIF(p.created_at,''),?),COALESCE(NULLIF(p.updated_at,''),?),'系统补全' " +
            "FROM person p JOIN position j ON j.id=p.job_id AND j.org_id=p.org_id " +
            "WHERE p.job_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM application a WHERE a.org_id=p.org_id AND a.person_id=p.id)",timestamp,timestamp,timestamp);
        db.update("INSERT INTO migration(version,applied_at) VALUES('missing-applications-v6',?)",timestamp);
    }
    private void migratePositionTable(String table,boolean versionTable){
        ensureColumns(table,StructuredColumns.POSITION);if(!columnExists(table,"owner_employee_id"))db.execute("ALTER TABLE "+table+" ADD COLUMN owner_employee_id BIGINT");if(!columnExists(table,"body"))return;
        String assignments=StructuredColumns.POSITION.values().stream().map(v->StructuredColumns.column(v)+"=?").reduce((a,b)->a+","+b).orElseThrow();
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,body FROM "+table)){long org=((Number)row.get("org_id")).longValue();Map<String,Object> body=json.read(Objects.toString(row.get("body"),"{}"));List<Object> values=new ArrayList<>();for(String key:StructuredColumns.POSITION.keySet())values.add(structuredValue(body.get(key),key));values.add(resolveHrId(org,Objects.toString(body.get("owner"),"")));values.add(row.get("id"));db.update("UPDATE "+table+" SET "+assignments+",owner_employee_id=? WHERE id=?",values.toArray());}
        db.execute("ALTER TABLE "+table+" DROP COLUMN body");
    }
    private void consolidatePositionVersions(){
        for(String definition:List.of("parent_position_id BIGINT","current_record BOOLEAN DEFAULT TRUE NOT NULL","version_number BIGINT")){String column=definition.substring(0,definition.indexOf(' '));if(!columnExists("position",column))db.execute("ALTER TABLE position ADD COLUMN "+definition);}
        db.update("UPDATE position SET current_record=TRUE,version_number=COALESCE(version_number,revision) WHERE parent_position_id IS NULL");
        if(!tableExists("position_version"))return;
        String columns=StructuredColumns.POSITION.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));
        String marks=String.join(",",Collections.nCopies(StructuredColumns.POSITION.size()+6,"?"));
        for(Map<String,Object> row:db.queryForList("SELECT * FROM position_version ORDER BY position_id,version")){
            long org=((Number)value(row,"org_id")).longValue(),parent=((Number)value(row,"position_id")).longValue(),version=((Number)value(row,"version")).longValue();
            Integer exists=db.queryForObject("SELECT COUNT(*) FROM position WHERE org_id=? AND parent_position_id=? AND version_number=?",Integer.class,org,parent,version);if(exists!=null&&exists>0)continue;
            List<Object> values=new ArrayList<>(List.of(org,1,parent,false,version));values.add(value(row,"owner_employee_id"));for(String definition:StructuredColumns.POSITION.values())values.add(value(row,StructuredColumns.column(definition)));
            db.update("INSERT INTO `position`(org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,"+columns+") VALUES("+marks+")",values.toArray());
        }
        db.execute("DROP TABLE position_version");
    }
    private void seedCompanyLocations(long org) {
        List<String[]> locations=List.of(
            new String[]{"南通宣通文化投资","南通","崇川区","文峰街道","青年中路"},new String[]{"南通宣通文化投资","南通","崇川区","新城桥街道","工农南路"},new String[]{"南通宣通文化投资","南通","崇川区","狼山镇街道","长江南路"},new String[]{"南通宣通文化投资","南通","通州区","金沙街道","朝霞路"},new String[]{"南通宣通文化投资","南通","海门区","海门街道","北京中路"},new String[]{"南通宣通文化投资","上海","浦东新区","陆家嘴街道","世纪大道"},
            new String[]{"A公司","北京","朝阳区","望京街道","广顺南大街"},new String[]{"A公司","北京","海淀区","中关村街道","丹棱街"},
            new String[]{"B公司","上海","徐汇区","徐家汇街道","虹桥路"},new String[]{"B公司","上海","静安区","南京西路街道","南京西路"}
        );
        for(String[] location:locations){List<Long> ids=db.query("SELECT id FROM company WHERE org_id=? AND name=?",(r,n)->r.getLong(1),org,location[0]);if(ids.isEmpty())continue;long companyId=ids.get(0);if(db.queryForObject("SELECT COUNT(*) FROM company_location WHERE company_id=? AND base_city=? AND district=? AND street=? AND office_address=?",Integer.class,companyId,location[1],location[2],location[3],location[4])==0)db.update("INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active) VALUES(?,?,?,?,?,?,TRUE)",org,companyId,location[1],location[2],location[3],location[4]);}
    }
    private void backfillPositionIdentity(){
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,company,location,base_location,recruitment_code FROM position WHERE parent_position_id IS NULL")){long id=((Number)row.get("id")).longValue(),org=((Number)row.get("org_id")).longValue();String company=Objects.toString(row.get("company"),"").isBlank()?"南通宣通文化投资":row.get("company").toString();String base=Objects.toString(row.get("base_location"),"").isBlank()?(Objects.toString(row.get("location"),"").isBlank()?"南通":row.get("location").toString()):row.get("base_location").toString();String code=Objects.toString(row.get("recruitment_code"),"").isBlank()?String.format("%03d",id):row.get("recruitment_code").toString();List<Long> companies=db.query("SELECT id FROM company WHERE org_id=? AND name=?",(r,n)->r.getLong(1),org,company);Long companyId=companies.isEmpty()?null:companies.get(0);db.update("UPDATE position SET company_id=?,company=?,base_location=?,recruitment_code=?,employment_type=COALESCE(employment_type,'全职') WHERE id=?",companyId,company,base,code,id);db.update("UPDATE position SET company_id=COALESCE(company_id,?),company=COALESCE(company,?),base_location=COALESCE(base_location,?),recruitment_code=COALESCE(recruitment_code,?),employment_type=COALESCE(employment_type,'全职') WHERE parent_position_id=?",companyId,company,base,code,id);}
    }
    private void migrateRecordTable(){
        ensureColumns("profile_record",StructuredColumns.RECORD);if(!columnExists("profile_record","owner_employee_id"))db.execute("ALTER TABLE profile_record ADD COLUMN owner_employee_id BIGINT");if(!columnExists("profile_record","body"))return;
        String assignments=StructuredColumns.RECORD.values().stream().map(v->StructuredColumns.column(v)+"=?").reduce((a,b)->a+","+b).orElseThrow();
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,person_id,record_type,revision,body FROM profile_record")){long org=((Number)row.get("org_id")).longValue();Map<String,Object> body=json.read(Objects.toString(row.get("body"),"{}"));if("offers".equals(row.get("record_type")))normalizeLegacyOffer(body);List<Object> values=new ArrayList<>();for(String key:StructuredColumns.RECORD.keySet())values.add(structuredValue(body.get(key),key));String owner=Objects.toString(body.get("owner"),Objects.toString(body.get("interviewer"),""));values.add(resolveHrId(org,owner));values.add(row.get("id"));db.update("UPDATE profile_record SET "+assignments+",owner_employee_id=? WHERE id=?",values.toArray());}
        db.execute("ALTER TABLE profile_record DROP COLUMN body");
    }
    private void migrateDedicatedProcessTables(){
        for(String table:List.of("communication","interview","offer"))ensureColumns(table,StructuredColumns.RECORD);
        if(columnExists("offer","version"))db.update("UPDATE offer SET business_version=COALESCE(business_version,version)");
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='dedicated-process-tables-v1'",Integer.class)>0)return;
        for(Map<String,Object> row:db.queryForList("SELECT * FROM profile_record WHERE record_type IN ('events','interviews','offers') ORDER BY id")){
            String type=Objects.toString(value(row,"record_type"),"");long oldId=((Number)value(row,"id")).longValue();Long entityId=null;String entityType=type,action="migrated";
            if("interviews".equals(type)){entityType="interview";entityId=copyProcessRow("interview",row,"application_id","scheduled_at");}
            else if("offers".equals(type)){entityType="offer";entityId=copyProcessRow("offer",row,"application_id","business_version");}
            else if("communication".equals(Objects.toString(value(row,"event_type"),""))){entityType="communication";action="created";entityId=copyProcessRow("communication",row,"application_id","occurred_at");}
            else{entityType=Objects.toString(value(row,"event_type"),"person");action=entityType;}
            String duplicateSql="SELECT COUNT(*) FROM record WHERE org_id=? AND person_id=? AND entity_type=? AND "+(entityId==null?"entity_id IS NULL":"entity_id=?")+" AND title=? AND occurred_at=?";List<Object> duplicateArgs=new ArrayList<>(List.of(value(row,"org_id"),value(row,"person_id"),entityType));if(entityId!=null)duplicateArgs.add(entityId);duplicateArgs.add(Objects.toString(value(row,"title"),""));duplicateArgs.add(Objects.toString(value(row,"occurred_at"),Objects.toString(value(row,"created_at"),now())));Integer exists=db.queryForObject(duplicateSql,Integer.class,duplicateArgs.toArray());
            if(exists==null||exists==0)insertLedgerRow(row,entityType,entityId,action);
            if(entityId!=null)db.update("UPDATE audit SET record_id=? WHERE record_id=? AND org_id=? AND action LIKE ?",entityId,oldId,value(row,"org_id"),type+".%");
        }
        db.update("DELETE FROM profile_record WHERE record_type IN ('events','applications','interviews','offers')");
        db.update("INSERT INTO migration(version,applied_at) VALUES('dedicated-process-tables-v1',?)",now());
    }
    private long copyProcessRow(String table,Map<String,Object> row,String matchOne,String matchTwo){
        List<Long> found=matchTwo==null?db.query("SELECT id FROM "+table+" WHERE org_id=? AND person_id=? AND "+matchOne+"=? ORDER BY id LIMIT 1",(r,n)->r.getLong(1),value(row,"org_id"),value(row,"person_id"),value(row,matchOne)):db.query("SELECT id FROM "+table+" WHERE org_id=? AND person_id=? AND "+matchOne+"=? AND "+matchTwo+"=? ORDER BY id LIMIT 1",(r,n)->r.getLong(1),value(row,"org_id"),value(row,"person_id"),value(row,matchOne),value(row,matchTwo));
        if(!found.isEmpty())return found.get(0);
        boolean offerTable=table.equals("offer");String columns=(offerTable?"version,":"")+StructuredColumns.RECORD.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));String marks=String.join(",",Collections.nCopies(StructuredColumns.RECORD.size()+4+(offerTable?1:0),"?"));GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO "+table+"(org_id,person_id,revision,owner_employee_id,"+columns+") VALUES("+marks+")",Statement.RETURN_GENERATED_KEYS);int i=1;p.setObject(i++,value(row,"org_id"));p.setObject(i++,value(row,"person_id"));p.setObject(i++,value(row,"revision"));p.setObject(i++,value(row,"owner_employee_id"));if(offerTable)p.setObject(i++,value(row,"business_version"));for(String definition:StructuredColumns.RECORD.values())p.setObject(i++,value(row,StructuredColumns.column(definition)));return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void insertLedgerRow(Map<String,Object> row,String entityType,Long entityId,String action){String occurred=Objects.toString(value(row,"occurred_at"),Objects.toString(value(row,"created_at"),now()));db.update("INSERT INTO record(org_id,person_id,application_id,entity_type,entity_id,action,title,summary,result,reason,occurred_at,actor,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",value(row,"org_id"),value(row,"person_id"),value(row,"application_id"),entityType,entityId,action,value(row,"title"),value(row,"summary"),value(row,"result"),value(row,"reason"),occurred,Objects.toString(value(row,"actor"),"系统迁移"),Objects.toString(value(row,"created_at"),occurred));}
    private void recoverDedicatedProcessRows(){
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='dedicated-process-recovery-v2'",Integer.class)>0)return;
        for(Map<String,Object> ledger:db.queryForList("SELECT * FROM record WHERE entity_type='communication' ORDER BY id")){
            Object application=value(ledger,"application_id"),occurred=value(ledger,"occurred_at");Integer exists=db.queryForObject("SELECT COUNT(*) FROM communication WHERE org_id=? AND person_id=? AND application_id=? AND occurred_at=?",Integer.class,value(ledger,"org_id"),value(ledger,"person_id"),application,occurred);if(exists!=null&&exists>0)continue;
            Map<String,Object> body=new LinkedHashMap<>();body.put("type","communication");body.put("title",value(ledger,"title"));body.put("summary",value(ledger,"summary"));body.put("result",value(ledger,"result"));body.put("reason",value(ledger,"reason"));body.put("applicationId",application);body.put("occurredAt",occurred);body.put("actor",value(ledger,"actor"));body.put("createdAt",value(ledger,"created_at"));body.put("updatedAt",value(ledger,"created_at"));insertProcessBody("communication",((Number)value(ledger,"org_id")).longValue(),((Number)value(ledger,"person_id")).longValue(),body);
        }
        for(Map<String,Object> audit:db.queryForList("SELECT id,org_id,person_id,created_at,actor FROM audit WHERE action='interviews.save' AND person_id IS NOT NULL ORDER BY id")){
            Map<String,Object> body=new LinkedHashMap<>();for(Map<String,Object> change:db.queryForList("SELECT field_name,after_value FROM audit_change WHERE audit_id=?",audit.get("id")))if(change.get("after_value")!=null&&!change.get("after_value").toString().isBlank())body.put(toCamel(change.get("field_name").toString()),change.get("after_value"));Long application=number(body.get("applicationId"));String scheduled=Objects.toString(body.get("scheduledAt"),""),round=Objects.toString(body.get("round"),"");if(application==null||scheduled.isBlank()||round.isBlank())continue;Integer exists=db.queryForObject("SELECT COUNT(*) FROM interview WHERE org_id=? AND person_id=? AND application_id=? AND scheduled_at=? AND interview_round=?",Integer.class,audit.get("org_id"),audit.get("person_id"),application,scheduled,round);if(exists!=null&&exists>0)continue;body.put("createdAt",audit.get("created_at"));body.put("updatedAt",audit.get("created_at"));body.putIfAbsent("actor",audit.get("actor"));insertProcessBody("interview",((Number)audit.get("org_id")).longValue(),((Number)audit.get("person_id")).longValue(),body);
        }
        db.update("INSERT INTO migration(version,applied_at) VALUES('dedicated-process-recovery-v2',?)",now());
    }
    private long insertProcessBody(String table,long org,long personId,Map<String,Object> body){long owner=resolveHrId(org,Objects.toString(body.get("owner"),Objects.toString(body.get("actor"),"")));String columns=StructuredColumns.RECORD.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));String marks=String.join(",",Collections.nCopies(StructuredColumns.RECORD.size()+4,"?"));GeneratedKeyHolder keys=new GeneratedKeyHolder();db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO "+table+"(org_id,person_id,revision,owner_employee_id,"+columns+") VALUES("+marks+")",Statement.RETURN_GENERATED_KEYS);int i=1;p.setLong(i++,org);p.setLong(i++,personId);p.setLong(i++,1);p.setLong(i++,owner);for(String key:StructuredColumns.RECORD.keySet())p.setObject(i++,structuredValue(body.get(key),key));return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    private String toCamel(String field){if(!field.contains("_"))return field;StringBuilder out=new StringBuilder();boolean upper=false;for(char c:field.toCharArray()){if(c=='_'){upper=true;continue;}out.append(upper?Character.toUpperCase(c):c);upper=false;}return out.toString();}
    private void migrateOnboardingTable(){
        ensureColumns("onboarding",StructuredColumns.ONBOARDING);
        String columns=StructuredColumns.ONBOARDING.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));
        String marks=String.join(",",Collections.nCopies(StructuredColumns.ONBOARDING.size()+7,"?"));
        for(Map<String,Object> row:db.queryForList("SELECT * FROM profile_record WHERE record_type='employments' ORDER BY id")){
            long recordId=((Number)value(row,"id")).longValue();Integer exists=db.queryForObject("SELECT COUNT(*) FROM onboarding WHERE employment_record_id=?",Integer.class,recordId);if(exists!=null&&exists>0)continue;
            String created=Objects.toString(value(row,"created_at"),now()),updated=Objects.toString(value(row,"updated_at"),created);List<Object> values=new ArrayList<>();values.add(value(row,"org_id"));values.add(value(row,"person_id"));values.add(value(row,"application_id"));values.add(recordId);values.add(1);values.add(created);values.add(updated);for(String definition:StructuredColumns.ONBOARDING.values())values.add(value(row,StructuredColumns.column(definition)));
            db.update("INSERT INTO onboarding(org_id,person_id,application_id,employment_record_id,revision,created_at,updated_at,"+columns+") VALUES("+marks+")",values.toArray());
        }
        String recordClear=StructuredColumns.ONBOARDING.values().stream().map(v->StructuredColumns.column(v)+"=NULL").collect(java.util.stream.Collectors.joining(","));String applicationClear=StructuredColumns.ONBOARDING.values().stream().map(v->StructuredColumns.column(v)+"=NULL").collect(java.util.stream.Collectors.joining(","));
        db.update("UPDATE profile_record SET "+recordClear+" WHERE record_type='employments'");db.update("UPDATE application SET "+applicationClear);
    }
    private void normalizeLegacyOffer(Map<String,Object> body){String old=Objects.toString(body.get("status"),"");body.put("status",Set.of("已发送","已查看","已接受","已入职","已完成").contains(old)?"已完成":"已创建");body.putIfAbsent("salaryMode","monthly");body.putIfAbsent("validityDays",7);body.putIfAbsent("currentRecord",true);if("已完成".equals(body.get("status")))body.putIfAbsent("sentAt",Objects.toString(body.get("updatedAt"),now()));}
    private void migrateAuditTable(){
        for(String definition:List.of("person_id BIGINT","record_id BIGINT","position_id BIGINT","summary LONGTEXT")){String column=definition.substring(0,definition.indexOf(' '));if(!columnExists("audit",column))db.execute("ALTER TABLE audit ADD COLUMN "+definition);}if(!columnExists("audit","body"))return;
        for(Map<String,Object> row:db.queryForList("SELECT id,action,body FROM audit")){Map<String,Object> body=json.read(Objects.toString(row.get("body"),"{}"));db.update("UPDATE audit SET person_id=?,record_id=?,position_id=?,summary=? WHERE id=?",number(body.get("personId")),number(body.get("recordId")),number(body.get("positionId")),row.get("action")+" 业务数据变更",row.get("id"));Map<String,Object> before=body.get("before") instanceof Map<?,?> value?stringMap(value):Map.of(),after=body.get("after") instanceof Map<?,?> value?stringMap(value):Map.of();Set<String> keys=new LinkedHashSet<>();keys.addAll(before.keySet());keys.addAll(after.keySet());for(String key:keys)if(!Objects.equals(before.get(key),after.get(key)))db.update("INSERT INTO audit_change(audit_id,field_name,before_value,after_value) VALUES(?,?,?,?)",row.get("id"),key,plain(before.get(key)),plain(after.get(key)));}
        db.execute("ALTER TABLE audit DROP COLUMN body");
    }
    private Map<String,Object> stringMap(Map<?,?> input){Map<String,Object> out=new LinkedHashMap<>();input.forEach((k,v)->out.put(Objects.toString(k),v));return out;}
    private String plain(Object value){if(value==null)return null;if(value instanceof Collection<?> c)return c.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("，"));return Objects.toString(value);}
    private Object structuredValue(Object value,String key){if(value==null||value.toString().isBlank())return null;if(Set.of("salaryMin","salaryMax","actualSalary","annualSalary","score","amount").contains(key))return decimal(value);if(Set.of("companyId","jobId","jobVersion","applicationId","opportunityId","size","version","identityFrontAssetId","identityBackAssetId","graduationCertificateAssetId","degreeCertificateAssetId","educationVerificationAssetId","departureCertificateAssetId").contains(key))return number(value);if(Set.of("salaryMonths","validityDays","probationMonths").contains(key))return integer(value);if(Set.of("external","currentRecord").contains(key))return value instanceof Boolean b?b:Boolean.valueOf(value.toString());return plain(value);}
    private Long number(Object value){if(value==null||value.toString().isBlank())return null;try{return new java.math.BigDecimal(value.toString()).longValue();}catch(NumberFormatException e){return null;}}
    private Integer integer(Object value){Long n=number(value);return n==null?null:n.intValue();}
    private java.math.BigDecimal decimal(Object value){if(value==null||value.toString().isBlank())return null;try{return new java.math.BigDecimal(value.toString());}catch(NumberFormatException e){return null;}}
    private boolean columnExists(String table,String column) {
        try(Connection c=source.getConnection();ResultSet rs=c.getMetaData().getColumns(c.getCatalog(),null,"%", "%")) {
            while(rs.next()) if(table.equalsIgnoreCase(rs.getString("TABLE_NAME"))&&column.equalsIgnoreCase(rs.getString("COLUMN_NAME")))return true;
            return false;
        } catch(SQLException e){throw new IllegalStateException("无法检查数据库字段",e);}
    }
    private void migratePersonBody() {
        boolean legacy=columnExists("person","body");
        for(String definition:PERSON_COLUMNS.values()) {
            String column=definition.substring(0,definition.indexOf(' '));
            if(!columnExists("person",column))db.execute("ALTER TABLE person ADD COLUMN "+definition);
        }
        if(!columnExists("person","owner_employee_id"))db.execute("ALTER TABLE person ADD COLUMN owner_employee_id BIGINT");
        if(columnExists("person","owner_hr_id")){db.update("UPDATE person SET owner_employee_id=owner_hr_id WHERE owner_employee_id IS NULL");db.execute("ALTER TABLE person DROP COLUMN owner_hr_id");}
        if(!legacy)return;
        String assignments=PERSON_COLUMNS.values().stream().map(v->v.substring(0,v.indexOf(' '))+"=?").reduce((a,b)->a+","+b).orElseThrow();
        for(Map<String,Object> row:db.queryForList("SELECT id,org_id,body FROM person")) {
            long org=((Number)row.get("org_id")).longValue();
            Map<String,Object> body=json.read(Objects.toString(row.get("body"),"{}"));List<Object> values=new ArrayList<>();
            for(String key:PERSON_COLUMNS.keySet())values.add(personValue(body.get(key),key));
            values.add(resolveHrId(org,Objects.toString(body.get("owner"),"")));values.add(row.get("id"));
            db.update("UPDATE person SET "+assignments+",owner_employee_id=? WHERE id=?",values.toArray());
        }
        db.execute("ALTER TABLE person DROP COLUMN body");
        if(db.queryForObject("SELECT COUNT(*) FROM migration WHERE version='person-columns-v2'",Integer.class)==0)
            db.update("INSERT INTO migration(version,applied_at) VALUES('person-columns-v2',?)",now());
    }
    private Object personValue(Object value,String key) {
        if(value==null)return null;
        if(Set.of("assets","experiences","employments","compensations").contains(key))return value instanceof String?value:json.write(value);
        if(key.equals("tags")&&value instanceof Collection<?> items)return items.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("，"));
        if(Set.of("salaryMin","salaryMax").contains(key)&&!value.toString().isBlank())try{return new java.math.BigDecimal(value.toString());}catch(NumberFormatException ignored){return null;}
        return value.toString();
    }
    public long resolveHrId(long org,String name) {
        String wanted=name==null?"":name.strip();
        List<Long> match=db.query("SELECT id FROM employee WHERE org_id=? AND name=? AND role='HR' AND active=TRUE",(r,n)->r.getLong(1),org,wanted);
        if(!match.isEmpty())return match.get(0);
        List<Long> available=db.query("SELECT id FROM employee WHERE org_id=? AND role='HR' AND active=TRUE ORDER BY id LIMIT 1",(r,n)->r.getLong(1),org);
        if(available.isEmpty())throw ApiException.bad("请先创建并关联一个 HR 员工账号");
        return available.get(0);
    }
    public long insertPerson(long org,Map<String,Object> body) {
        String columns=String.join(",",PERSON_COLUMNS.values().stream().map(v->v.substring(0,v.indexOf(' '))).toList());
        String marks=String.join(",",Collections.nCopies(PERSON_COLUMNS.size()+3,"?"));GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO person(org_id,revision,owner_employee_id,"+columns+") VALUES("+marks+")",Statement.RETURN_GENERATED_KEYS);int i=1;p.setLong(i++,org);p.setLong(i++,1);p.setLong(i++,resolveHrId(org,Objects.toString(body.get("owner"),"")));for(String key:PERSON_COLUMNS.keySet())p.setObject(i++,personValue(body.get(key),key));return p;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private boolean tableExists(String name) {
        try(Connection c=source.getConnection(); ResultSet rs=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})) {
            while(rs.next()) if(name.equalsIgnoreCase(rs.getString("TABLE_NAME"))) return true;
            return false;
        } catch(SQLException e) { throw new IllegalStateException("无法检查旧数据库表",e); }
    }
    private void importLegacy() {
        if(tableExists("sys_user")) for(Map<String,Object> r : db.queryForList("SELECT * FROM sys_user")) {
            String username=string(r,"username");
            if(!username.isBlank() && db.queryForObject("SELECT COUNT(*) FROM user WHERE username=?",Integer.class,username)==0)
                db.update("INSERT INTO user(org_id,username,password,role) VALUES(1,?,?,?)",username,string(r,"password"),string(r,"role").isBlank()?"ADMIN":string(r,"role"));
        }
        Map<String,Long> positions=new LinkedHashMap<>();
        if(tableExists("job_position")) for(Map<String,Object> r : db.queryForList("SELECT * FROM job_position")) {
            String name=string(r,"name"); if(name.isBlank()) continue;
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("name",name); body.put("minSalary",value(r,"min_salary")); body.put("maxSalary",value(r,"max_salary"));
            body.put("status","招聘中"); body.put("version",1); body.put("createdAt",now()); body.put("versionNote","旧系统岗位迁移"); body.put("legacyId",value(r,"id"));
            long id=insert("position",1,body); positions.putIfAbsent(name,id);
            body.put("id",id);
        }
        if(tableExists("candidate")) for(Map<String,Object> r:db.queryForList("SELECT * FROM candidate")) {
            Map<String,Object> body=new LinkedHashMap<>();
            for(String key:List.of("name","gender","source","salary","status","result","experience","remark","phone","email","wechat")) body.put(key,string(r,key));
            body.put("applyTime",string(r,"apply_time")); body.put("legacyId",value(r,"id")); body.put("tags",List.of());
            String created=string(r,"create_time"); body.put("createdAt",created.isBlank()?now():created); body.put("version",1);
            String job=string(r,"job");
            if(!job.isBlank()) {
                if(!positions.containsKey(job)) {
                    Map<String,Object> p=new LinkedHashMap<>(); p.put("name",job);p.put("status","招聘中");p.put("version",1);p.put("createdAt",now());p.put("versionNote","从旧人才职位补全岗位");
                    long pid=insert("position",1,p);positions.put(job,pid);p.put("id",pid);
                }
                body.put("jobId",positions.get(job)); body.put("job",job);
            }
            Matcher salary=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-–~至]\\s*(\\d+(?:\\.\\d+)?)").matcher(string(r,"salary"));
            if(salary.find()){body.put("salaryMin",Double.valueOf(salary.group(1)));body.put("salaryMax",Double.valueOf(salary.group(2)));}
            long id=insertPerson(1,body);
            Map<String,Object> event=new LinkedHashMap<>(); event.put("type","import");event.put("title","旧系统档案迁移");event.put("summary","保留原始档案字段；旧系统未记录的面试和沟通历史尚待补录。"); event.put("occurredAt",body.get("createdAt"));event.put("createdAt",now());event.put("actor","系统迁移");event.put("revision",1);
            insertStructuredRecord(1,id,"events",event);
        }
    }
    public long insert(String table,long org,Map<String,Object> body) {
        if(table.equals("person"))return insertPerson(org,body);
        if(!table.equals("position")) throw new IllegalArgumentException();
        GeneratedKeyHolder keys=new GeneratedKeyHolder();String columns=StructuredColumns.POSITION.values().stream().map(StructuredColumns::column).collect(java.util.stream.Collectors.joining(","));String marks=String.join(",",Collections.nCopies(StructuredColumns.POSITION.size()+6,"?"));
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO `position`(org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,"+columns+") VALUES("+marks+")",Statement.RETURN_GENERATED_KEYS);int i=1;p.setLong(i++,org);p.setLong(i++,1);p.setObject(i++,null);p.setBoolean(i++,true);p.setLong(i++,1);p.setLong(i++,resolveHrId(org,Objects.toString(body.get("owner"),"")));for(String key:StructuredColumns.POSITION.keySet())p.setObject(i++,structuredValue(body.get(key),key));return p;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void insertStructuredRecord(long org,long personId,String type,Map<String,Object> body){String timestamp=Objects.toString(body.get("occurredAt"),now());db.update("INSERT INTO record(org_id,person_id,application_id,entity_type,entity_id,action,title,summary,result,reason,occurred_at,actor,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",org,personId,number(body.get("applicationId")),type,number(body.get("recordId")),Objects.toString(body.get("type"),"created"),body.get("title"),body.get("summary"),body.get("result"),body.get("reason"),timestamp,Objects.toString(body.get("actor"),"系统迁移"),Objects.toString(body.get("createdAt"),timestamp));}
    private static Object value(Map<String,Object> r,String key){for(Map.Entry<String,Object> e:r.entrySet())if(e.getKey().equalsIgnoreCase(key))return e.getValue();return null;}
    private static String string(Map<String,Object> r,String key){Object v=value(r,key);return v==null?"":v.toString();}
    static String now(){return OffsetDateTime.now().toString();}
}
