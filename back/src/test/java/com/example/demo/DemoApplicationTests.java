package com.example.demo;

import com.example.demo.service.JsonStore;
import com.example.demo.service.SchemaMigration;
import com.example.demo.service.OfferMailService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${SETHUB_TEST_DB_URL}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","spring.datasource.username=${SETHUB_TEST_DB_USER}","spring.datasource.password=${SETHUB_TEST_DB_PASSWORD}",
    "sethub.jwt-secret=test-only-secret-is-at-least-thirty-two-bytes-long","sethub.data-dir=./target/test-data","sethub.mail.capture-only=true"})
@EnabledIfEnvironmentVariable(named="SETHUB_TEST_DB_URL",matches=".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoApplicationTests {
    @LocalServerPort int port;
    @Autowired JsonStore json;
    @Autowired JdbcTemplate db;
    @Autowired SchemaMigration schema;
    @Autowired BCryptPasswordEncoder passwords;
    @Autowired OfferMailService offerMail;
    static String token;
    static long personId,jobId,applicationId,offerId;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> request(String method,String path,Map<String,Object> body,String auth) throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(auth!=null)b.header("Authorization","Bearer "+auth);
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.write(body),StandardCharsets.UTF_8));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    Map<String,Object> ok(String method,String path,Map<String,Object> body) throws Exception {HttpResponse<String> r=request(method,path,body,token);assertEquals(200,r.statusCode(),r.body());return json.read(r.body());}
    long num(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
    @SuppressWarnings("unchecked") List<Map<String,Object>> array(Map<String,Object> m,String key){return (List<Map<String,Object>>)m.get(key);}
    Map<String,Object> validCandidate(String name,String phone){Map<String,Object> body=new LinkedHashMap<>();body.put("name",name);body.put("phone",phone);body.put("source","内推");body.put("gender","男");body.put("experience","3年");body.put("jobId",jobId);body.put("salaryMin",10);body.put("salaryMax",15);body.put("status","沟通中");return body;}

    @Test @Order(1) void setupAndAuthentication() throws Exception {
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM employee WHERE name='昱宁' AND role='HR'",Integer.class));
        for(String table:List.of("person","record","application","onboarding"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),table+" 首次启动不得写入虚构业务数据");
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM company WHERE org_id=1 AND name='南通宣通文化投资'",Integer.class));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM company_location WHERE org_id=1",Integer.class)>=5);
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM position WHERE org_id=1 AND parent_position_id IS NULL",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'sh\\_%' ESCAPE '\\'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='import_key'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('profile_record','inbox_read')",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='message'",Integer.class));
        assertEquals(401,request("GET","/candidate/list",null,null).statusCode());
        assertEquals(401,request("POST","/auth/login",Map.of("username","missing","password","invalid!"),null).statusCode());
        assertEquals(Boolean.FALSE,ok("GET","/auth/setup/status",null).get("needsSetup"));
        token=ok("POST","/auth/login",Map.of("username","admin","password","123456")).get("token").toString();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user u JOIN employee e ON e.id=u.employee_id AND e.user_id=u.id",Integer.class));
        assertEquals(409,request("POST","/auth/setup",Map.of("username","other-admin","password","Testing!12345"),null).statusCode());
        assertEquals(200,request("POST","/auth/login",Map.of("username","admin","password","123456"),null).statusCode());
        assertEquals(404,request("GET","/password/encode",null,token).statusCode());
        assertEquals(404,request("GET","/audit",null,token).statusCode());
        long companyId=db.queryForObject("SELECT id FROM company WHERE org_id=1 AND name='南通宣通文化投资'",Long.class);
        assertTrue(companyId>0);
    }
    @Test @Order(2) void realPaginationSalaryAndOptimisticLock() throws Exception {
        Map<String,Object> job=ok("POST","/position",Map.of("company","南通宣通文化投资","name","摄影师","baseLocation","南通","location","南通 · 崇川区 · 文峰街道 · 青年中路","minSalary",10,"maxSalary",20,"status","招聘中"));jobId=num(job,"id");
        assertEquals(400,request("POST","/candidate",Map.of("name","无效薪资","salaryMin",20,"salaryMax",10),token).statusCode());
        assertEquals(400,request("POST","/candidate",Map.of("name","错误手机","phone","12345"),token).statusCode());
        assertEquals(400,request("POST","/candidate",Map.of("name","错误微信","wechat","中文微信号"),token).statusCode());
        assertEquals(400,request("POST","/candidate",Map.of("name","错误邮箱","email","not-an-email"),token).statusCode());
        assertEquals(400,request("POST","/candidate",Map.of("name","薪资倒置","email","reversed@example.com","salaryMin",10,"salaryMax",9),token).statusCode());
        for(int i=0;i<13;i++){Map<String,Object> p=ok("POST","/candidate",validCandidate("分页人才"+i,"139000000"+String.format("%02d",i)));personId=num(p,"id");}
        Map<String,Object> list=ok("GET","/candidate/list?size=999&page=1",null);assertEquals(10,array(list,"items").size());assertEquals(13,num(list,"total"));
        assertEquals(3,array(ok("GET","/candidate/list?page=2",null),"items").size());
        assertEquals(0,array(ok("GET","/candidate/list?search=missing",null),"items").size());
        HttpResponse<String> duplicateByPhone=request("GET","/candidate/duplicates?phone=13900000012",null,token);assertEquals(200,duplicateByPhone.statusCode());assertTrue(duplicateByPhone.body().contains("分页人才12"));
        HttpResponse<String> duplicateByName=request("GET","/candidate/duplicates?name="+java.net.URLEncoder.encode("分页人才12",StandardCharsets.UTF_8),null,token);assertEquals(200,duplicateByName.statusCode());assertTrue(duplicateByName.body().contains("13900000012"));
        Map<String,Object> automaticallyLinked=ok("GET","/candidate/"+personId,null);assertFalse(array(automaticallyLinked,"applications").isEmpty());assertFalse(array(automaticallyLinked,"compensations").isEmpty());
        assertEquals(List.of("昱宁"),db.queryForList("SELECT name FROM employee WHERE org_id=1 AND role='HR' ORDER BY id",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE column_name='body' AND table_name LIKE '%'",Integer.class));
        long currentVersion=num(automaticallyLinked,"version");Map<String,Object> updated=ok("PUT","/candidate/"+personId,Map.of("version",currentVersion,"name","2021年来面试的人"));assertEquals(currentVersion+1,num(updated,"version"));
        assertEquals(409,request("PUT","/candidate/"+personId,Map.of("version",currentVersion,"name","冲突"),token).statusCode());
        assertEquals(400,request("PUT","/candidate/"+personId,Map.of("version",currentVersion+1,"result","未入职"),token).statusCode());
    }
    @Test @Order(3) void historicalTimelineTaskAndImmutableJobSnapshot() throws Exception {
        Map<String,Object> app=ok("POST","/candidate/"+personId+"/records/applications",Map.of("jobId",jobId,"startedAt","2021-03-15","status","沟通中"));applicationId=num(app,"id");
        assertEquals(1,num(app,"jobVersion"));
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/applications/"+applicationId,Map.of("revision",num(app,"revision"),"status","面试中"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/interviews",Map.of("scheduledAt","2027-01-08T10:00:00","round","初面","owner","昱宁","status","待面试"),token).statusCode());
        Map<String,Object> interview=ok("POST","/candidate/"+personId+"/records/interviews",Map.of("applicationId",applicationId,"scheduledAt","2027-01-08T10:00:00","round","初面","owner","昱宁","method","视频","status","待面试"));
        assertEquals("摄影师",interview.get("jobName"));assertEquals("昱宁",interview.get("owner"));
        assertEquals(400,request("POST","/candidate/"+personId+"/records/offers",Map.ofEntries(Map.entry("applicationId",applicationId),Map.entry("recipientEmail","candidate@example.com"),Map.entry("actualSalary",14),Map.entry("salaryMonths",13),Map.entry("probationMonths",3),Map.entry("socialInsurance","五险一金"),Map.entry("status","已完成"),Map.entry("expectedStartDate","2027-02-01")),token).statusCode());
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/interviews/"+num(interview,"id"),Map.of("revision",1,"status","已完成"),token).statusCode());
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/interviews/"+num(interview,"id"),Map.of("revision",1,"status","已完成","score",6,"result","推荐","feedback","能力符合要求"),token).statusCode());
        Map<String,Object> evaluated=ok("PUT","/candidate/"+personId+"/records/interviews/"+num(interview,"id"),Map.of("revision",1,"status","已完成","score",4.5,"result","推荐","feedback","回答具体，能说明取舍与风险"));assertEquals("已完成",evaluated.get("status"));
        ok("PUT","/position/"+jobId,Map.of("version",1,"name","高级摄影师","minSalary",20,"maxSalary",30,"versionNote","职责及薪资升级"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='position_version'",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM position WHERE parent_position_id=? AND current_record=FALSE",Integer.class,jobId));
        Map<String,Object> person=ok("GET","/candidate/"+personId,null);assertEquals("高级摄影师",person.get("job"));
        Map<String,Object> snapshot=array(person,"applications").get(0);assertEquals("摄影师",snapshot.get("jobName"));assertEquals(1,num(snapshot,"jobVersion"));
        ok("POST","/candidate/"+personId+"/records/events",Map.of("type","communication","title","2021 年面试后回访","summary","候选人因家庭原因无法入职","result","未入职","reason","家庭原因","occurredAt","2021-03-21T14:00:00+08:00","nextStep","再次了解求职意愿","nextContactAt","2027-01-10T10:00:00","applicationId",applicationId));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM communication WHERE person_id=? AND application_id=?",Integer.class,personId,applicationId));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM record WHERE person_id=? AND entity_type='communication'",Integer.class,personId));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='profile_record'",Integer.class));
        person=ok("GET","/candidate/"+personId,null);assertTrue(array(person,"events").stream().anyMatch(e->Objects.toString(e.get("occurredAt"),"").startsWith("2021-03-21")&&e.get("reason").equals("家庭原因")&&!Objects.toString(e.get("createdAt"),"").startsWith("2021")));
        assertFalse(person.containsKey("tasks"));
        long eventId=num(array(person,"events").get(0),"id");assertEquals(400,request("PUT","/candidate/"+personId+"/records/events/"+eventId,Map.of("revision",1,"title","篡改历史"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/experiences",Map.of("type","工作","organization","公司","startDate","2022-01-01","endDate","2021-01-01"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/opportunities",Map.of("title","已移除模块"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/collaborations",Map.of("projectName","已移除模块"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/tasks",Map.of("title","已移除模块"),token).statusCode());
    }
    @Test @Order(4) void offersVersionedAndRecordRelationsValidated() throws Exception {
        Map<String,Object> offer=ok("POST","/candidate/"+personId+"/records/offers",Map.ofEntries(Map.entry("applicationId",applicationId),Map.entry("recipientEmail","candidate@example.com"),Map.entry("salaryMin",12),Map.entry("salaryMax",15),Map.entry("actualSalary",14),Map.entry("salaryMonths",13),Map.entry("probationMonths",3),Map.entry("socialInsurance","五险一金"),Map.entry("status","已创建"),Map.entry("expectedStartDate","2027-02-01"),Map.entry("expiresAt","2027-01-20")));offerId=num(offer,"id");assertEquals(1,num(offer,"version"));
        Map<String,Object> second=ok("PUT","/candidate/"+personId+"/records/offers/"+offerId,Map.of("revision",1,"salaryMin",14,"salaryMax",17,"actualSalary",16,"status","已创建"));assertEquals(2,num(second,"version"));assertNotEquals(offerId,num(second,"id"));
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/offers/"+offerId,Map.of("revision",1,"status","已完成"),token).statusCode());
        Map<String,Object> completed=ok("PUT","/candidate/"+personId+"/records/offers/"+num(second,"id"),Map.of("revision",1,"status","已完成"));assertEquals(3,num(completed,"version"));assertEquals("已完成",completed.get("status"));assertEquals("待回复",completed.get("responseStatus"));
        String responseUrl=offerMail.lastCaptured().orElseThrow().responseUrl(),offerToken=responseUrl.substring(responseUrl.indexOf("token=")+6);Map<String,Object> publicOffer=json.read(request("GET","/public/offers/"+offerToken,null,null).body());assertEquals("待回复",publicOffer.get("responseStatus"));Map<String,Object> accepted=json.read(request("POST","/public/offers/"+offerToken+"/response",Map.of("decision","ACCEPTED"),null).body());assertEquals("已接受",accepted.get("responseStatus"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM message WHERE category='Offer 回复' AND person_id IS NULL AND interview_id IS NULL AND status='已接受'",Integer.class));
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/offers/"+num(completed,"id"),Map.of("revision",1,"remark","完成后不允许修改"),token).statusCode());
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM offer",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='offer'",Integer.class));assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM record WHERE entity_type='offer'",Integer.class));
        Map<String,Object> beforeHire=ok("GET","/candidate/"+personId,null);Map<String,Object> currentApplication=array(beforeHire,"applications").stream().filter(item->num(item,"id")==applicationId).findFirst().orElseThrow();
        Map<String,Object> onboarding=new LinkedHashMap<>();onboarding.put("revision",num(currentApplication,"revision"));onboarding.put("status","已入职");onboarding.put("employmentCategory","全职");onboarding.put("hireChannel","社招");onboarding.put("preEmploymentStatus","在职");onboarding.put("employeeName","测试员工");onboarding.put("employeePhone","13800138000");onboarding.put("employeeEmail","employee@example.com");onboarding.put("identityNumber","320102199001011234");onboarding.put("startDate","2027-02-01");onboarding.put("bankName","中国工商银行");onboarding.put("bankAccount","6222021234567890123");onboarding.put("address","南通市崇川区现住址");onboarding.put("householdRegistration","江苏省南通市");onboarding.put("emergencyContact","测试家属");onboarding.put("emergencyPhone","13900139000");onboarding.put("onboardedAt",java.time.OffsetDateTime.now().toString());onboarding.put("identityFrontAssetId",1);onboarding.put("identityBackAssetId",2);ok("PUT","/candidate/"+personId+"/records/applications/"+applicationId,onboarding);
        Map<String,Object> hired=ok("GET","/candidate/"+personId,null);assertEquals(1,array(hired,"employments").size());assertEquals("试用期",array(hired,"employments").get(0).get("status"));
        long employmentItemId=num(array(hired,"employments").get(0),"id");assertTrue(employmentItemId>0);assertEquals("2027-02-01",array(hired,"employments").get(0).get("startDate"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM onboarding WHERE person_id=? AND application_id=? AND employee_email='employee@example.com'",Integer.class,personId,applicationId));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='profile_record'",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM employee e JOIN onboarding o ON o.id=e.onboarding_id WHERE e.person_id=? AND e.application_id=? AND e.role='EMPLOYEE' AND e.user_id IS NULL AND e.employee_number IS NOT NULL",Integer.class,personId,applicationId));
        long hiredEmployeeId=db.queryForObject("SELECT id FROM employee WHERE person_id=? AND application_id=?",Long.class,personId,applicationId);Map<String,Object> promoted=ok("PUT","/employee/"+hiredEmployeeId+"/role",Map.of("role","HR"));assertEquals(Boolean.TRUE,promoted.get("accountCreated"));assertNotNull(promoted.get("username"));assertNotNull(promoted.get("initialPassword"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user WHERE employee_id=? AND active=TRUE",Integer.class,hiredEmployeeId));ok("PUT","/employee/"+hiredEmployeeId+"/role",Map.of("role","EMPLOYEE"));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user WHERE employee_id=?",Integer.class,hiredEmployeeId));
        Map<String,Object> otherBody=validCandidate("另一个人","13800138001");otherBody.put("email","other@example.com");Map<String,Object> other=ok("POST","/candidate",otherBody);long otherId=num(other,"id");
        assertEquals(404,request("POST","/candidate/"+otherId+"/records/interviews",Map.of("applicationId",applicationId,"scheduledAt","2027-01-01T10:00:00"),token).statusCode());
        long otherApplicationId=num(array(other,"applications").get(0),"id");Map<String,Object> rejectedInterview=ok("POST","/candidate/"+otherId+"/records/interviews",Map.of("applicationId",otherApplicationId,"scheduledAt","2027-01-08T10:00:00","round","初面","owner","昱宁","method","视频","status","待面试"));ok("PUT","/candidate/"+otherId+"/records/interviews/"+num(rejectedInterview,"id"),Map.of("revision",1,"status","已完成","score",2,"result","不推荐","feedback","核心能力与岗位要求不匹配"));Map<String,Object> rejectedPerson=ok("GET","/candidate/"+otherId,null);Map<String,Object> rejectedApplication=array(rejectedPerson,"applications").stream().filter(item->num(item,"id")==otherApplicationId).findFirst().orElseThrow();assertEquals("公司淘汰",rejectedApplication.get("status"));assertTrue(Objects.toString(rejectedApplication.get("reason"),"").contains("核心能力与岗位要求不匹配"));assertEquals("已关闭",rejectedPerson.get("status"));
    }
    @Test @Order(5) void attachmentPersistenceAndTenantIsolation() throws Exception {
        String boundary="SetHubTestBoundary";byte[] bytes=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"resume.txt\"\r\nContent-Type: text/plain\r\n\r\nA real resume file\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest upload=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/candidate/"+personId+"/assets")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        HttpResponse<String> uploaded=http.send(upload,HttpResponse.BodyHandlers.ofString());assertEquals(200,uploaded.statusCode(),uploaded.body());Map<String,Object> asset=json.read(uploaded.body());assertFalse(asset.containsKey("storageName"));
        HttpResponse<String> download=request("GET",asset.get("downloadUrl").toString(),null,token);assertEquals(200,download.statusCode());assertEquals("A real resume file",download.body());
        HttpResponse<String> preview=request("GET","/candidate/"+personId+"/assets/"+num(asset,"id")+"/preview",null,token);assertEquals(200,preview.statusCode(),preview.body());assertTrue(preview.body().contains("A real resume file"));
        db.update("INSERT INTO employee(org_id,name,department,title,role,active,created_at) VALUES(2,'tenant-two','人才管理','HR','HR',TRUE,?)",java.time.OffsetDateTime.now().toString());
        long tenantEmployeeId=db.queryForObject("SELECT id FROM employee WHERE org_id=2 AND name='tenant-two'",Long.class);
        db.update("INSERT INTO user(org_id,username,password,role,employee_id) VALUES(2,?,?,?,?)","tenant-two",passwords.encode("Another!123"),"ADMIN",tenantEmployeeId);
        long tenantUserId=db.queryForObject("SELECT id FROM user WHERE username='tenant-two'",Long.class);db.update("UPDATE employee SET user_id=? WHERE id=?",tenantUserId,tenantEmployeeId);
        HttpResponse<String> login=request("POST","/auth/login",Map.of("username","tenant-two","password","Another!123"),null);String otherToken=json.read(login.body()).get("token").toString();
        assertEquals(404,request("GET","/candidate/"+personId,null,otherToken).statusCode());assertEquals(404,request("GET",asset.get("downloadUrl").toString(),null,otherToken).statusCode());
        assertEquals(0,num(json.read(request("GET","/candidate/list",null,otherToken).body()),"total"));assertEquals(404,request("GET","/position/"+jobId,null,otherToken).statusCode());
        assertEquals(0,array(json.read(request("GET","/workspace",null,otherToken).body()),"events").size());
    }
    @Test @Order(6) void migrationRestartAndImportIdempotency() throws Exception {
        Map<String,Object> data=validCandidate("CSV导入人才","13800138002");data.put("email","csv@example.com");data.put("importBatch","batch-for-test-123");data.put("importRow",2);Map<String,Object> first=ok("POST","/candidate",data),retry=ok("POST","/candidate",data);assertEquals(first.get("id"),retry.get("id"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM record WHERE import_batch='batch-for-test-123' AND import_row=2",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='person' AND column_name IN ('import_batch','import_row')",Integer.class));
        int count=db.queryForObject("SELECT COUNT(*) FROM person",Integer.class);schema.initialize();assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM person",Integer.class));
        Map<String,Object> workspace=ok("GET","/workspace",null);assertTrue(workspace.containsKey("stats"));assertTrue(workspace.containsKey("sources"));
        assertEquals(400,request("POST","/public/questionnaire",Map.of("name","缺少联系方式","source","内推"),null).statusCode());
        HttpResponse<String> questionnaire=request("POST","/public/questionnaire",Map.of("name","问卷人才","phone","13800138000","source","朋友推荐","gender","女","experience","3—5年","jobId",jobId,"salaryMin",12,"salaryMax",18),null);assertEquals(200,questionnaire.statusCode(),questionnaire.body());assertNotNull(json.read(questionnaire.body()).get("applicationId"));
    }
    @Test @Order(7) void inboxAndDedicatedPositionStatusFlow() throws Exception {
        Map<String,Object> inbox=ok("GET","/inbox",null);assertTrue(num(inbox,"count")>0);assertTrue(num(inbox,"unreadCount")>0);assertTrue(array(inbox,"items").stream().anyMatch(item->"人才问卷".equals(item.get("category"))));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM message WHERE category='人才问卷' AND person_id IS NOT NULL AND interview_id IS NULL",Integer.class));assertTrue(db.queryForObject("SELECT COUNT(*) FROM message WHERE category='面试安排' AND person_id IS NULL AND interview_id IS NOT NULL",Integer.class)>0);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM message WHERE category NOT IN ('人才问卷','面试安排') AND (person_id IS NOT NULL OR interview_id IS NOT NULL)",Integer.class));
        String firstMessageId=String.valueOf(array(inbox,"items").get(0).get("id"));ok("PUT","/inbox/"+firstMessageId+"/read",Map.of());Map<String,Object> afterRead=ok("GET","/inbox",null);assertTrue(array(afterRead,"items").stream().anyMatch(item->firstMessageId.equals(String.valueOf(item.get("id")))&&Boolean.TRUE.equals(item.get("read"))));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM message WHERE id=? AND is_read=TRUE AND read_at IS NOT NULL",Integer.class,Long.parseLong(firstMessageId)));assertEquals(num(inbox,"unreadCount")-1,num(afterRead,"unreadCount"));
        ok("PUT","/inbox/read-all",Map.of());assertEquals(0,num(ok("GET","/inbox",null),"unreadCount"));
        Map<String,Object> candidate=ok("POST","/candidate",validCandidate("停招流程人才","13800138999"));long activePersonId=num(candidate,"id");
        Map<String,Object> currentJob=ok("GET","/position/"+jobId,null);
        Map<String,Object> paused=ok("PUT","/position/"+jobId+"/status",Map.of("version",num(currentJob,"version"),"status","暂停招聘","reason","岗位预算调整"));assertEquals("暂停招聘",paused.get("status"));
        Map<String,Object> closedPerson=ok("GET","/candidate/"+activePersonId,null);assertEquals("公司淘汰",closedPerson.get("result"));assertEquals("已关闭",closedPerson.get("status"));
    }
}
