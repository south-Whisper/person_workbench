package com.example.demo.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/** Adds a coherent, one-time sample recruitment journey to a new local database. */
public class DemoDataInitializer implements ApplicationRunner {
    private static final long ORG = 1L;
    private static final String MARKER = "local-demo-data-v1";
    private final JdbcTemplate db;
    private final SchemaMigration schema;

    public DemoDataInitializer(JdbcTemplate db, SchemaMigration schema) {
        this.db = db;
        this.schema = schema;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (count("SELECT COUNT(*) FROM migration WHERE version=?", MARKER) > 0) return;
        if (count("SELECT COUNT(*) FROM position WHERE org_id=?", ORG) > 0
                || count("SELECT COUNT(*) FROM person WHERE org_id=?", ORG) > 0) {
            markComplete();
            return;
        }

        long yuNing = employee("昱宁");
        long liLandi = employee("李岚笛");
        long companyId = company("南通宣通文化投资");
        String now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        long backend = position(companyId, yuNing, "Java后端开发工程师", "001", "技术研发部", 18, 28,
                "负责业务系统后端接口、数据库设计和稳定性建设。", "熟悉 Java、Spring Boot 和关系型数据库，有良好的沟通能力。", now);
        long designer = position(companyId, liLandi, "品牌设计师", "002", "品牌创意部", 12, 18,
                "负责品牌视觉、活动物料和产品宣传设计。", "熟练使用主流设计工具，具备完整作品集和品牌意识。", now);
        long photographer = position(companyId, yuNing, "摄影师", "003", "创意制作部", 10, 16,
                "负责人物、活动和宣传内容的拍摄及基础后期。", "能独立完成拍摄方案、现场执行和后期交付。", now);

        long chen = person(yuNing, backend, "陈晨", "13800001001", "chenchen@example.com", "BOSS直聘", "沟通中", "5年", now);
        long chenApp = application(chen, backend, yuNing, "Java后端开发工程师", "001", "沟通中", "较高", "有兴趣", today.minusDays(3), now);
        communication(chen, chenApp, backend, yuNing, "首次电话沟通", "候选人关注技术成长和团队协作方式。", "有效沟通", "较高", "有兴趣", today.minusDays(2), now);
        compensation(chen, chenApp, yuNing, 20, 25, today.minusDays(3), now);

        long lin = person(liLandi, designer, "林晓", "13800001002", "linxiao@example.com", "内推", "面试中", "4年", now);
        long linApp = application(lin, designer, liLandi, "品牌设计师", "002", "面试中", "很高", "积极", today.minusDays(8), now);
        communication(lin, linApp, designer, liLandi, "作品集沟通", "作品风格与品牌调性匹配，已约初面。", "有效沟通", "很高", "积极", today.minusDays(7), now);
        interview(lin, linApp, designer, liLandi, "品牌设计师", "002", "初面", "视频", "已完成", 4.5,
                "推荐", "作品完整，能清楚说明设计思路与业务取舍。", today.minusDays(4), now);

        long wang = person(yuNing, backend, "王小蓝", "13800001003", "wangxiaolan@example.com", "猎聘", "待入职", "6年", now);
        long wangApp = application(wang, backend, yuNing, "Java后端开发工程师", "001", "待入职", "很高", "强烈", today.minusDays(15), now);
        communication(wang, wangApp, backend, yuNing, "薪资与到岗沟通", "薪资方案已确认，候选人同意按计划到岗。", "有效沟通", "很高", "强烈", today.minusDays(3), now);
        interview(wang, wangApp, backend, yuNing, "Java后端开发工程师", "001", "终面", "现场", "已完成", 4.8,
                "强烈推荐", "技术基础扎实，项目经验与岗位要求匹配。", today.minusDays(7), now);
        offer(wang, wangApp, backend, yuNing, "Java后端开发工程师", "001", "wangxiaolan@example.com", 18, 28, 25, 13,
                "已完成", today.plusDays(12), today.minusDays(1), now);

        long zhou = person(liLandi, photographer, "周宁", "13800001004", "zhouning@example.com", "智联招聘", "已入职", "5年", now);
        long zhouApp = application(zhou, photographer, liLandi, "摄影师", "003", "已入职", "很高", "强烈", today.minusDays(30), now);
        interview(zhou, zhouApp, photographer, liLandi, "摄影师", "003", "终面", "现场", "已完成", 4.6,
                "推荐", "拍摄经验丰富，现场组织和交付意识较好。", today.minusDays(20), now);
        offer(zhou, zhouApp, photographer, liLandi, "摄影师", "003", "zhouning@example.com", 10, 16, 15, 12,
                "已完成", today.minusDays(5), today.minusDays(12), now);
        employment(zhou, zhouApp, photographer, liLandi, "摄影师", "003", today.minusDays(5), now);

        markComplete();
    }

    private long position(long companyId, long ownerId, String name, String code, String department,
                          int minSalary, int maxSalary, String description, String requirements, String now) {
        long id = insert("INSERT INTO `position`(org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,name,min_salary,max_salary,company_id,company,recruitment_code,department,location,base_location,description,status,headcount,employment_type,requirements,created_at,updated_at) VALUES(?,1,NULL,TRUE,1,?,?,?,?,?,'南通宣通文化投资',?,?,?,'南通',?,'招聘中',2,'全职',?,?,?)",
                ORG, ownerId, name, minSalary, maxSalary, companyId, code, department,
                "南通 · 崇川区 · 文峰街道 · 青年中路", description, requirements, now, now);
        return id;
    }

    private long person(long ownerId, long jobId, String name, String phone, String email, String source,
                        String status, String experience, String now) {
        return insert("INSERT INTO person(org_id,revision,owner_employee_id,name,gender,phone,email,source,job_id,apply_time,salary_min,salary_max,status,location,work_status,company,current_position,tags,experience,company_intent,talent_intent,education,created_at,updated_at) VALUES(?,1,?,?,?,?,?,?,?,?,18,28,?,'南通','在职','示例公司',?,? ,?,'未判断','未知','本科',?,?)",
                ORG, ownerId, name, "男", phone, email, source, jobId, LocalDate.now().toString(), status,
                "在职岗位", "演示数据", experience, now, now);
    }

    private long application(long personId, long jobId, long ownerId, String jobName, String jobCode,
                             String status, String companyIntent, String talentIntent, LocalDate startedAt, String now) {
        return insert("INSERT INTO application(org_id,person_id,revision,owner_employee_id,job_id,job_name,job_version,company,job_code,base_location,status,company_intent,talent_intent,started_at,created_at,updated_at,created_by) VALUES(?,?,1,?,?,?,1,'南通宣通文化投资',?,'南通',?,?,?,?,?,?,'系统初始化')",
                ORG, personId, ownerId, jobId, jobName, jobCode, status, companyIntent, talentIntent,
                startedAt.toString(), now, now);
    }

    private void communication(long personId, long applicationId, long jobId, long ownerId, String title,
                               String summary, String result, String companyIntent, String talentIntent,
                               LocalDate occurredAt, String now) {
        db.update("INSERT INTO record(org_id,person_id,record_type,revision,owner_employee_id,event_type,title,summary,status,result,job_id,application_id,company_intent,talent_intent,next_step,next_contact_at,occurred_at,created_at,updated_at,actor,created_by) VALUES(?,?,'events',1,?,'communication',?,?,'已记录',?,?,?,?,?,'按约定继续跟进',?,?,?,?,?,'系统初始化')",
                ORG, personId, ownerId, title, summary, result, jobId, applicationId, companyIntent, talentIntent,
                occurredAt.plusDays(3).atTime(10, 0).toString(), occurredAt.atTime(14, 30).toString(), now, now, employeeName(ownerId));
    }

    private void interview(long personId, long applicationId, long jobId, long ownerId, String jobName,
                           String jobCode, String round, String method, String status, double score,
                           String result, String feedback, LocalDate scheduledAt, String now) {
        db.update("INSERT INTO record(org_id,person_id,record_type,revision,owner_employee_id,status,result,feedback,job_id,job_name,job_version,company,job_code,base_location,application_id,scheduled_at,interview_round,method,score,created_at,updated_at,created_by) VALUES(?,?,'interviews',1,?,?,?,?,?,?,1,'南通宣通文化投资',?,'南通',?,?,?,?,?,?,?,'系统初始化')",
                ORG, personId, ownerId, status, result, feedback, jobId, jobName, jobCode, applicationId,
                scheduledAt.atTime(14, 0).toString(), round, method, score, now, now);
    }

    private void offer(long personId, long applicationId, long jobId, long ownerId, String jobName,
                       String jobCode, String email, int minSalary, int maxSalary, int actualSalary,
                       int salaryMonths, String status, LocalDate expectedStart, LocalDate sentAt, String now) {
        db.update("INSERT INTO record(org_id,person_id,record_type,revision,owner_employee_id,status,job_id,job_name,job_version,company,job_code,base_location,application_id,salary_min,salary_max,salary_mode,actual_salary,annual_salary,salary_months,validity_days,probation_months,social_insurance,recipient_email,current_record,sent_at,expected_start_date,expires_at,business_version,created_at,updated_at,created_by) VALUES(?,?,'offers',1,?,?,?, ?,1,'南通宣通文化投资',?,'南通',?,?,?,'monthly',?,?,?,7,3,'五险一金',?,TRUE,?,?,?,1,?,?,'系统初始化')",
                ORG, personId, ownerId, status, jobId, jobName, jobCode, applicationId, minSalary, maxSalary,
                actualSalary, actualSalary * salaryMonths, salaryMonths, email, sentAt.atTime(10, 0).toString(),
                expectedStart.toString(), sentAt.plusDays(7).toString(), now, now);
    }

    private void employment(long personId, long applicationId, long jobId, long ownerId, String jobName,
                            String jobCode, LocalDate startDate, String now) {
        long employmentRecordId=insert("INSERT INTO record(org_id,person_id,record_type,revision,owner_employee_id,status,remark,job_id,job_name,job_version,company,job_code,base_location,application_id,start_date,created_at,updated_at,created_by,employment_category,hire_channel,pre_employment_status,employee_name,employee_phone,employee_email,identity_number,bank_name,bank_account,residential_address,household_registration) VALUES(?,?,'employments',1,?,'试用期','由初始化的已入职应聘记录建立',?,?,1,'南通宣通文化投资',?,'南通',?,?,?,?,'系统初始化','全职','社招','在职','周宁','13800001004','zhouning@example.com','320600199501010018','中国工商银行','6222020000000000000','南通市崇川区青年中路','江苏省南通市')",
                ORG, personId, ownerId, jobId, jobName, jobCode, applicationId, startDate.toString(), now, now);
    }

    private void compensation(long personId, long applicationId, long ownerId, int min, int max,
                              LocalDate occurredAt, String now) {
        db.update("INSERT INTO record(org_id,person_id,record_type,revision,owner_employee_id,event_type,remark,application_id,salary_min,salary_max,currency,period,source,occurred_at,created_at,updated_at,created_by) VALUES(?,?,'compensations',1,?,'候选人公开期望','与人才档案共用同一应聘编号',?,?,?,'CNY','月','首次沟通',?,?,?,'系统初始化')",
                ORG, personId, ownerId, applicationId, min, max, occurredAt.toString(), now, now);
    }

    private long employee(String name) {
        return Objects.requireNonNull(db.queryForObject("SELECT id FROM employee WHERE org_id=? AND name=?", Long.class, ORG, name));
    }

    private String employeeName(long id) {
        return db.queryForObject("SELECT name FROM employee WHERE id=?", String.class, id);
    }

    private long company(String name) {
        return Objects.requireNonNull(db.queryForObject("SELECT id FROM company WHERE org_id=? AND name=?", Long.class, ORG, name));
    }

    private int count(String sql, Object... args) {
        return Objects.requireNonNull(db.queryForObject(sql, Integer.class, args));
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void markComplete() {
        db.update("INSERT INTO migration(version,applied_at) VALUES(?,?)", MARKER,
                OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
    }
}

