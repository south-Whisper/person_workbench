package com.example.demo;

import com.example.demo.service.JsonStore;
import com.example.demo.service.SchemaMigration;
import org.junit.jupiter.api.*;
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
    "spring.datasource.url=jdbc:h2:mem:sethub-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "sethub.jwt-secret=test-only-secret-is-at-least-thirty-two-bytes-long","sethub.data-dir=./target/test-data"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoApplicationTests {
    @LocalServerPort int port;
    @Autowired JsonStore json;
    @Autowired JdbcTemplate db;
    @Autowired SchemaMigration schema;
    @Autowired BCryptPasswordEncoder passwords;
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

    @Test @Order(1) void setupAndAuthentication() throws Exception {
        assertEquals(401,request("GET","/candidate/list",null,null).statusCode());
        assertEquals(401,request("POST","/auth/login",Map.of("username","missing","password","invalid!"),null).statusCode());
        assertEquals(Boolean.TRUE,ok("GET","/auth/setup/status",null).get("needsSetup"));
        token=ok("POST","/auth/setup",Map.of("username","admin-test","password","Testing!12345")).get("token").toString();
        assertEquals(Boolean.FALSE,ok("GET","/auth/setup/status",null).get("needsSetup"));
        assertEquals(409,request("POST","/auth/setup",Map.of("username","other-admin","password","Testing!12345"),null).statusCode());
        db.update("UPDATE sh_user SET password=? WHERE username='admin-test'",passwords.encode("123456"));
        assertEquals(200,request("POST","/auth/login",Map.of("username","admin-test","password","123456"),null).statusCode());
        assertEquals(404,request("GET","/password/encode",null,token).statusCode());
    }
    @Test @Order(2) void realPaginationSalaryAndOptimisticLock() throws Exception {
        Map<String,Object> job=ok("POST","/position",Map.of("name","摄影师","minSalary",10,"maxSalary",20,"status","招聘中"));jobId=num(job,"id");
        assertEquals(400,request("POST","/candidate",Map.of("name","无效薪资","salaryMin",20,"salaryMax",10),token).statusCode());
        for(int i=0;i<13;i++){Map<String,Object> p=ok("POST","/candidate",Map.of("name","分页人才"+i,"jobId",jobId,"salaryMin",10,"salaryMax",15,"phone","139000000"+String.format("%02d",i),"status","沟通中"));personId=num(p,"id");}
        Map<String,Object> list=ok("GET","/candidate/list?size=999&page=1",null);assertEquals(10,array(list,"items").size());assertEquals(13,num(list,"total"));
        assertEquals(3,array(ok("GET","/candidate/list?page=2",null),"items").size());
        assertEquals(0,array(ok("GET","/candidate/list?search=missing",null),"items").size());
        Map<String,Object> updated=ok("PUT","/candidate/"+personId,Map.of("version",1,"name","2021年来面试的人"));assertEquals(2,num(updated,"version"));
        assertEquals(409,request("PUT","/candidate/"+personId,Map.of("version",1,"name","冲突"),token).statusCode());
        assertEquals(400,request("PUT","/candidate/"+personId,Map.of("version",2,"result","未入职"),token).statusCode());
    }
    @Test @Order(3) void historicalTimelineTaskAndImmutableJobSnapshot() throws Exception {
        Map<String,Object> app=ok("POST","/candidate/"+personId+"/records/applications",Map.of("jobId",jobId,"startedAt","2021-03-15","status","面试中"));applicationId=num(app,"id");
        assertEquals(1,num(app,"jobVersion"));
        ok("PUT","/position/"+jobId,Map.of("version",1,"name","高级摄影师","minSalary",20,"maxSalary",30,"versionNote","职责及薪资升级"));
        Map<String,Object> person=ok("GET","/candidate/"+personId,null);assertEquals("高级摄影师",person.get("job"));
        Map<String,Object> snapshot=array(person,"applications").get(0);assertEquals("摄影师",snapshot.get("jobName"));assertEquals(1,num(snapshot,"jobVersion"));
        ok("POST","/candidate/"+personId+"/records/events",Map.of("type","communication","title","2021 年面试后回访","summary","候选人因家庭原因无法入职","result","未入职","reason","家庭原因","occurredAt","2021-03-21T14:00:00+08:00","nextStep","再次了解求职意愿","nextContactAt","2027-01-10T10:00:00","applicationId",applicationId));
        person=ok("GET","/candidate/"+personId,null);assertTrue(array(person,"events").stream().anyMatch(e->Objects.toString(e.get("occurredAt"),"").startsWith("2021-03-21")&&e.get("reason").equals("家庭原因")&&!Objects.toString(e.get("createdAt"),"").startsWith("2021")));
        assertEquals(1,array(person,"tasks").size());
        long eventId=num(array(person,"events").get(0),"id");assertEquals(400,request("PUT","/candidate/"+personId+"/records/events/"+eventId,Map.of("revision",1,"title","篡改历史"),token).statusCode());
        assertEquals(400,request("POST","/candidate/"+personId+"/records/experiences",Map.of("type","工作","organization","公司","startDate","2022-01-01","endDate","2021-01-01"),token).statusCode());
    }
    @Test @Order(4) void offersVersionedAndRecordRelationsValidated() throws Exception {
        Map<String,Object> offer=ok("POST","/candidate/"+personId+"/records/offers",Map.of("applicationId",applicationId,"salaryMin",12,"salaryMax",15,"status","已发送","expectedStartDate","2027-02-01","expiresAt","2027-01-20"));offerId=num(offer,"id");assertEquals(1,num(offer,"version"));
        Map<String,Object> second=ok("POST","/candidate/"+personId+"/records/offers",Map.of("applicationId",applicationId,"salaryMin",14,"salaryMax",17,"status","草稿","expectedStartDate","2027-02-01","expiresAt","2027-01-20"));assertEquals(2,num(second,"version"));
        assertEquals(400,request("PUT","/candidate/"+personId+"/records/offers/"+offerId,Map.of("revision",1,"salaryMin",13),token).statusCode());
        Map<String,Object> accepted=ok("PUT","/candidate/"+personId+"/records/offers/"+offerId,Map.of("revision",1,"status","已接受"));assertEquals(2,num(accepted,"revision"));assertEquals(1,num(accepted,"version"));
        Map<String,Object> other=ok("POST","/candidate",Map.of("name","另一个人"));long otherId=num(other,"id");
        assertEquals(404,request("POST","/candidate/"+otherId+"/records/interviews",Map.of("applicationId",applicationId,"scheduledAt","2027-01-01T10:00:00"),token).statusCode());
    }
    @Test @Order(5) void attachmentPersistenceAndTenantIsolation() throws Exception {
        String boundary="SetHubTestBoundary";byte[] bytes=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"resume.txt\"\r\nContent-Type: text/plain\r\n\r\nA real resume file\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest upload=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/candidate/"+personId+"/assets")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        HttpResponse<String> uploaded=http.send(upload,HttpResponse.BodyHandlers.ofString());assertEquals(200,uploaded.statusCode(),uploaded.body());Map<String,Object> asset=json.read(uploaded.body());assertFalse(asset.containsKey("storageName"));
        HttpResponse<String> download=request("GET",asset.get("downloadUrl").toString(),null,token);assertEquals(200,download.statusCode());assertEquals("A real resume file",download.body());
        db.update("INSERT INTO sh_user(org_id,username,password,role) VALUES(2,?,?,?)","tenant-two",passwords.encode("Another!123"),"ADMIN");
        HttpResponse<String> login=request("POST","/auth/login",Map.of("username","tenant-two","password","Another!123"),null);String otherToken=json.read(login.body()).get("token").toString();
        assertEquals(404,request("GET","/candidate/"+personId,null,otherToken).statusCode());assertEquals(404,request("GET",asset.get("downloadUrl").toString(),null,otherToken).statusCode());
        assertEquals(0,num(json.read(request("GET","/candidate/list",null,otherToken).body()),"total"));assertEquals(404,request("GET","/position/"+jobId,null,otherToken).statusCode());
        assertEquals(0,array(json.read(request("GET","/workspace",null,otherToken).body()),"events").size());
    }
    @Test @Order(6) void migrationRestartAndImportIdempotency() throws Exception {
        Map<String,Object> data=Map.of("name","CSV导入人才","importBatch","batch-for-test-123","importRow",2);Map<String,Object> first=ok("POST","/candidate",data),retry=ok("POST","/candidate",data);assertEquals(first.get("id"),retry.get("id"));
        int count=db.queryForObject("SELECT COUNT(*) FROM sh_person",Integer.class);schema.initialize();assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM sh_person",Integer.class));
        Map<String,Object> workspace=ok("GET","/workspace",null);assertTrue(workspace.containsKey("stats"));assertTrue(workspace.containsKey("sources"));
    }
}
