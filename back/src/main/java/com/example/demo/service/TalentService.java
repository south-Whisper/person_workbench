package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TalentService {
    public static final List<String> TYPES=List.of("events","applications","interviews","offers","assets","experiences","employments","collaborations","tasks","opportunities","compensations");
    private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("applications","应聘"),Map.entry("interviews","面试"),Map.entry("offers","Offer"),Map.entry("assets","附件"),Map.entry("experiences","经历"),Map.entry("employments","任职"),Map.entry("collaborations","项目合作"),Map.entry("tasks","跟进任务"),Map.entry("opportunities","寻访机会"),Map.entry("compensations","薪资事实"));
    private static final Set<String> PERSON_FIELDS=Set.of("name","nickname","gender","phone","email","wechat","source","owner","jobId","applyTime","salaryMin","salaryMax","status","result","reason","location","company","currentRole","tags","experience","remark","nextStep","nextContactAt","companyIntent","talentIntent","birthday","education","website");
    private static final Set<String> POSITION_FIELDS=Set.of("name","minSalary","maxSalary","department","location","description","status","owner","versionNote","headcount","employmentType","requirements");
    private static final Set<String> CLOSED=Set.of("候选人退出","公司淘汰","岗位暂停","岗位取消","转其他岗位","长期无响应","流程异常关闭","已结束","未通过","未入职","候选人拒绝","主动放弃","已拒绝","已撤回","已取消","已关闭","离职","已离职","公司放弃","对方拒绝","明确拒绝","放弃","终止","已终止");
    private static final Set<String> PERSON_STATUSES=Set.of("新线索","待联系","待沟通","已创建","初筛","沟通","沟通中","已沟通","面试安排","面试中","内部决策","谈薪","Offer","Offer中","待入职","已入职","已结束","人才储备","候选人退出","公司淘汰","岗位暂停","岗位取消","转其他岗位","长期无响应","流程异常关闭","未入职","已离职","离职","在职","已归档","已淘汰","已拒绝","已关闭","新建");
    private final JdbcTemplate db;private final JsonStore json;private final CurrentUser user;private final SchemaMigration schema;
    public TalentService(JdbcTemplate db,JsonStore json,CurrentUser user,SchemaMigration schema){this.db=db;this.json=json;this.user=user;this.schema=schema;}

    public Map<String,Object> list(String search,String status,String source,Long jobId,int page,int size) {
        if(page<1||size<1)throw ApiException.bad("页码和每页数量必须为正整数"); size=Math.min(size,10);
        List<Map<String,Object>> all=persons();
        String query=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> matched=all.stream().filter(p->query.isBlank()||searchText(p).contains(query))
            .filter(p->blank(status)||status.equals(str(p,"status"))).filter(p->blank(source)||source.equals(str(p,"source")))
            .filter(p->jobId==null||Objects.equals(id(p.get("jobId")),jobId)).toList();
        int from=(int)Math.min((long)(page-1)*size,matched.size()),to=Math.min(from+size,matched.size());
        Map<String,Object> stats=new LinkedHashMap<>(); stats.put("total",all.size());
        stats.put("communicating",all.stream().filter(p->Set.of("待联系","待沟通","沟通中","已沟通","沟通").contains(str(p,"status"))).count());
        stats.put("interviewing",all.stream().filter(p->str(p,"status").contains("面试")).count());
        stats.put("hired",all.stream().filter(p->Set.of("已入职","在职").contains(str(p,"status"))).count());
        stats.put("offers",recordsAcross("offers").stream().filter(p->!Set.of("已拒绝","已撤回","已过期","已替代").contains(str(p,"status"))).count());
        stats.put("tasks",recordsAcross("tasks").stream().filter(p->!Set.of("已完成","已取消").contains(str(p,"status"))).count());
        return Map.of("items",matched.subList(from,to),"total",matched.size(),"page",page,"size",size,"stats",stats);
    }
    private String searchText(Map<String,Object> p){return List.of("name","nickname","phone","email","wechat","job","source","location","company","currentRole","experience","remark","tags").stream().map(k->str(p,k)).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);}
    private List<Map<String,Object>> persons(){Map<Long,String> jobs=positions().stream().collect(Collectors.toMap(p->id(p.get("id")),p->str(p,"name")));return rows("SELECT id,revision,body FROM sh_person WHERE org_id=? ORDER BY id DESC",user.org()).stream().peek(p->p.put("job",jobs.getOrDefault(id(p.get("jobId")),""))).toList();}
    public Map<String,Object> person(long personId){Map<String,Object> p=basePerson(personId);if(id(p.get("jobId"))!=null)p.put("job",str(position(id(p.get("jobId"))),"name"));else p.put("job","");for(String type:TYPES)p.put(type,records(personId,type));return p;}
    private Map<String,Object> basePerson(long personId){return one("SELECT id,revision,body FROM sh_person WHERE id=? AND org_id=?",personId,user.org());}
    public List<Map<String,Object>> duplicates(String phone,String email,String wechat,String name){
        String nphone=normalize(phone),nemail=normalize(email),nwechat=normalize(wechat),nname=normalize(name);
        if(nphone.isBlank()&&nemail.isBlank()&&nwechat.isBlank()&&nname.isBlank())return List.of();
        return persons().stream().filter(p->same(p,"phone",nphone)||same(p,"email",nemail)||same(p,"wechat",nwechat)||same(p,"name",nname)).map(p->{Map<String,Object> match=new LinkedHashMap<>();for(String k:List.of("id","name","job","source","phone","email","wechat"))match.put(k,p.get(k));return match;}).toList();
    }
    private boolean same(Map<String,Object> p,String key,String value){return !value.isBlank()&&normalize(str(p,key)).equals(value);}
    private String normalize(String value){return value==null?"":value.replaceAll("[\\s-]","").toLowerCase(Locale.ROOT);}
    @Transactional public Map<String,Object> savePerson(Long personId,Map<String,Object> input){
        String importBatch=str(input,"importBatch");Long importRow=id(input.get("importRow"));
        if(personId==null&&!importBatch.isBlank()){
            if(!importBatch.matches("[a-zA-Z0-9_-]{8,80}")||importRow==null)throw ApiException.bad("导入批次或行号不正确");
            db.queryForObject("SELECT version FROM sh_migration WHERE version='schema-v1' FOR UPDATE",String.class);
            List<Long> existing=db.query("SELECT person_id FROM sh_import_key WHERE org_id=? AND batch_id=? AND row_number=?",(r,n)->r.getLong(1),user.org(),importBatch,importRow);
            if(!existing.isEmpty())return person(existing.get(0));
        }
        Map<String,Object> before=personId==null?new LinkedHashMap<>():basePerson(personId);
        Map<String,Object> body=json.copy(before);body.putAll(select(input,PERSON_FIELDS));
        required(body,"name","请填写姓名");limit(body,"name",120);limit(body,"remark",20000);
        if(!str(body,"gender").isBlank()&&!Set.of("男","女","未知","未填写").contains(str(body,"gender")))throw ApiException.bad("性别请选择男或女");
        salary(body,"salaryMin","salaryMax");validateDates(body);
        if(!str(body,"email").isBlank()&&!str(body,"email").matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw ApiException.bad("邮箱格式不正确");
        if(!str(body,"status").isBlank()&&!PERSON_STATUSES.contains(str(body,"status")))throw ApiException.bad("人才状态不正确");
        validateIntent(body);reason(body,before);
        Long jobId=id(body.get("jobId"));if(jobId!=null){Map<String,Object> job=position(jobId);body.put("jobId",jobId);body.put("job",job.get("name"));}else{body.put("jobId",null);body.put("job","");}
        if(body.get("salaryMin")!=null)body.put("salary",body.get("salaryMin")+"–"+body.get("salaryMax")+"K/月");else body.put("salary","");
        if(!body.containsKey("status")||blank(str(body,"status")))body.put("status","待联系");
        body.putIfAbsent("result","待定");body.putIfAbsent("tags",List.of());
        String now=now();body.put("updatedAt",now);
        if(personId==null){body.put("createdAt",now);body.put("version",1);if(!importBatch.isBlank()){body.put("importBatch",importBatch);body.put("importRow",importRow);}personId=schema.insert("sh_person",user.org(),body);body.put("id",personId);if(!importBatch.isBlank())db.update("INSERT INTO sh_import_key(org_id,batch_id,row_number,person_id) VALUES(?,?,?,?)",user.org(),importBatch,importRow,personId);}
        else{long expected=version(input,"version");body.put("version",expected+1);if(db.update("UPDATE sh_person SET body=?,revision=revision+1 WHERE id=? AND org_id=? AND revision=?",json.write(body),personId,user.org(),expected)==0)throw ApiException.conflict();}
        Map<String,Object> event=new LinkedHashMap<>();event.put("type",before.isEmpty()?"created":"profile");event.put("title",before.isEmpty()?"建立人才档案":"更新人才档案");event.put("summary",str(body,"name")+(before.isEmpty()?" 已加入人才库":" 的资料已更新"));event.put("before",before);event.put("after",body);event.put("reason",str(body,"reason"));event.put("occurredAt",now);appendEvent(personId,event);audit("person.save",Map.of("personId",personId,"before",before,"after",body));
        if(!str(body,"nextStep").isBlank()&&!str(body,"nextContactAt").isBlank()&&(!Objects.equals(body.get("nextStep"),before.get("nextStep"))||!Objects.equals(body.get("nextContactAt"),before.get("nextContactAt"))))createFollowup(personId,body);
        return person(personId);
    }
    public List<Map<String,Object>> positions(){return rows("SELECT id,revision,body FROM sh_position WHERE org_id=? ORDER BY id DESC",user.org());}
    public Map<String,Object> position(long id){return one("SELECT id,revision,body FROM sh_position WHERE id=? AND org_id=?",id,user.org());}
    public List<Map<String,Object>> positionVersions(long id){position(id);return db.query("SELECT body FROM sh_position_version WHERE position_id=? AND org_id=? ORDER BY version DESC",(r,n)->json.read(r.getString(1)),id,user.org());}
    @Transactional public Map<String,Object> savePosition(Long positionId,Map<String,Object> input){
        Map<String,Object> before=positionId==null?new LinkedHashMap<>():position(positionId),body=json.copy(before);body.putAll(select(input,POSITION_FIELDS));
        required(body,"name","请填写岗位名称");limit(body,"name",120);salary(body,"minSalary","maxSalary");
        body.putIfAbsent("status","招聘中");if(!Set.of("招聘中","暂停招聘","已关闭").contains(str(body,"status")))throw ApiException.bad("岗位状态不正确");
        if(positionId!=null)required(body,"versionNote","修改岗位需要填写版本变更原因");
        String now=now();body.put("updatedAt",now);body.put("createdAt",now);
        if(positionId==null){body.put("version",1);positionId=schema.insert("sh_position",user.org(),body);body.put("id",positionId);}
        else{long expected=version(input,"version");body.put("version",expected+1);if(db.update("UPDATE sh_position SET body=?,revision=revision+1 WHERE id=? AND org_id=? AND revision=?",json.write(body),positionId,user.org(),expected)==0)throw ApiException.conflict();}
        db.update("INSERT INTO sh_position_version(org_id,position_id,version,body) VALUES(?,?,?,?)",user.org(),positionId,body.get("version"),json.write(body));
        audit("position.save",Map.of("positionId",positionId,"before",before,"after",body));return position(positionId);
    }
    public List<Map<String,Object>> records(long personId,String type){validType(type);return recordRows("SELECT id,revision,body FROM sh_record WHERE person_id=? AND org_id=? AND record_type=? ORDER BY id DESC",personId,user.org(),type).stream().sorted((a,b)->sortDate(b).compareTo(sortDate(a))).toList();}
    private String sortDate(Map<String,Object> r){for(String key:List.of("occurredAt","scheduledAt","startedAt","startDate","createdAt"))if(!str(r,key).isBlank())return str(r,key);return "";}
    private Map<String,Object> record(long personId,String type,long recordId){return recordOne("SELECT id,revision,body FROM sh_record WHERE id=? AND person_id=? AND org_id=? AND record_type=?",recordId,personId,user.org(),type);}
    @Transactional public Map<String,Object> saveRecord(long personId,String type,Long recordId,Map<String,Object> input){
        final Long currentRecordId=recordId;
        validType(type);basePerson(personId);
        if(type.equals("events")&&recordId!=null)throw ApiException.bad("历史事件不可覆盖，请新增更正事件并注明原因");
        Map<String,Object> before=recordId==null?new LinkedHashMap<>():record(personId,type,recordId),body=json.copy(before);
        for(Map.Entry<String,Object> e:input.entrySet())if(!Set.of("id","personId","personName","orgId","createdAt","createdBy","updatedAt","revision","storageName","sha256","size","mimeType","downloadUrl","actor","before","after").contains(e.getKey()))body.put(e.getKey(),e.getValue());
        if(json.write(body).length()>150000)throw ApiException.bad("记录内容过长");
        if(type.equals("tasks")&&str(body,"reason").isBlank()&&!str(body,"result").isBlank())body.put("reason",body.get("result"));
        if(type.equals("collaborations")&&str(body,"reason").isBlank()&&!str(body,"feedback").isBlank())body.put("reason",body.get("feedback"));
        validateDates(body);validateIntent(body);reason(body,before);
        Long applicationId=id(body.get("applicationId"));if(applicationId!=null){Map<String,Object> app=record(personId,"applications",applicationId);body.put("applicationId",applicationId);body.put("jobId",app.get("jobId"));body.put("jobName",app.get("jobName"));body.put("jobVersion",app.get("jobVersion"));}
        if(id(body.get("jobId"))!=null)position(id(body.get("jobId")));
        if(type.equals("applications")) {
            Long jobId=id(body.get("jobId"));if(jobId==null)throw ApiException.bad("应聘必须选择关联岗位");
            if(!before.isEmpty()&&!Objects.equals(id(before.get("jobId")),jobId))throw ApiException.bad("历史应聘的岗位不能覆盖，请创建新的应聘记录");
            if(before.isEmpty()){Map<String,Object> job=position(jobId);body.put("jobId",jobId);body.put("jobName",job.get("name"));body.put("jobVersion",job.get("version"));body.put("jobSnapshot",json.copy(job));}
            else {body.put("jobName",before.get("jobName"));body.put("jobVersion",before.get("jobVersion"));body.put("jobSnapshot",before.get("jobSnapshot"));}
            body.putIfAbsent("status","已创建");if(!PERSON_STATUSES.contains(str(body,"status")))throw ApiException.bad("应聘阶段不正确");
            Long opportunityId=id(body.get("opportunityId"));if(opportunityId!=null){Map<String,Object> op=record(personId,"opportunities",opportunityId);if(!blank(str(op,"applicationId")))throw ApiException.bad("该机会已转化为应聘");}
        }
        if(type.equals("interviews")){if(applicationId==null)throw ApiException.bad("面试必须关联一条应聘记录");required(body,"scheduledAt","请选择面试时间");body.putIfAbsent("status","待安排");enumValue(body,"status",Set.of("待安排","已安排","待面试","进行中","已完成","已取消","未到场","未出席","已改期"));decimal(body,"score",BigDecimal.ZERO,new BigDecimal("100"));}
        if(type.equals("offers")){
            if(applicationId==null)throw ApiException.bad("Offer 必须关联应聘记录");salary(body,"salaryMin","salaryMax");if(body.get("salaryMin")==null)throw ApiException.bad("Offer 必须填写薪资上下限");
            body.putIfAbsent("status","草稿");enumValue(body,"status",Set.of("草稿","审批中","待审批","已批准","已审批","已发送","已查看","协商中","已接受","已拒绝","已过期","已撤回","已替代","已入职"));
            if(!before.isEmpty())for(String key:List.of("salaryMin","salaryMax","expectedStartDate","expiresAt","applicationId"))if(!equivalent(before.get(key),body.get(key)))throw ApiException.bad("Offer 条件变更需新增版本，不能覆盖历史方案");
            if(before.isEmpty()){
                // Lock the person to serialize Offer version allocation across simultaneous requests.
                db.queryForObject("SELECT id FROM sh_person WHERE id=? AND org_id=? FOR UPDATE",Long.class,personId,user.org());
                int next=records(personId,"offers").stream().filter(o->Objects.equals(id(o.get("applicationId")),applicationId)).mapToInt(o->Integer.parseInt(str(o,"version"))).max().orElse(0)+1;body.put("version",next);
            }else body.put("version",before.get("version"));
            if(str(body,"status").equals("已接受")&&records(personId,"offers").stream().anyMatch(o->Objects.equals(id(o.get("applicationId")),applicationId)&&str(o,"status").equals("已接受")&&!Objects.equals(id(o.get("id")),currentRecordId)))throw ApiException.bad("该应聘已接受其他 Offer 版本，请先更正原接受记录");
        }
        if(type.equals("experiences")){required(body,"organization","请填写学校、公司或项目名称");required(body,"type","请选择经历类型");}
        if(type.equals("employments")){if(id(body.get("jobId"))==null)throw ApiException.bad("任职必须选择岗位");required(body,"startDate","请选择入职日期");body.putIfAbsent("status","在职");enumValue(body,"status",Set.of("待入职","试用期","在职","已转正","已离职","离职","已取消"));}
        if(type.equals("collaborations")){required(body,"projectName","请填写合作项目名称");decimal(body,"amount",BigDecimal.ZERO,new BigDecimal("100000000000"));}
        if(type.equals("tasks")){required(body,"title","请填写任务标题");required(body,"dueAt","请选择任务截止时间");body.putIfAbsent("status","待完成");enumValue(body,"status",Set.of("待处理","待完成","进行中","已完成","已取消","逾期"));if(str(body,"status").equals("已完成")&&!str(before,"status").equals("已完成"))body.put("completedAt",now());}
        if(type.equals("events")){required(body,"title","请填写动态标题");required(body,"occurredAt","请选择实际发生时间");if(str(body,"type").equals("communication"))required(body,"result","请填写本次沟通结果");body.put("actor",user.name());}
        if(type.equals("assets")){required(body,"name","请填写附件名称");if(before.isEmpty()){required(body,"url","请上传文件或填写有效附件链接");if(!str(body,"url").matches("^https?://[^\\s]+$"))throw ApiException.bad("附件链接必须为 http 或 https 地址");body.put("external",true);}else if(before.containsKey("storageName")){body.put("url",before.get("url"));body.put("downloadUrl",before.get("downloadUrl"));}}
        if(type.equals("compensations")){salary(body,"salaryMin","salaryMax");if(body.get("salaryMin")==null)throw ApiException.bad("请填写薪资范围");required(body,"occurredAt","请选择薪资事实发生时间");body.putIfAbsent("currency","CNY");body.putIfAbsent("period","月");}
        if(type.equals("opportunities")){required(body,"title","请填写寻访机会标题");body.putIfAbsent("status","新发现");}
        if(type.equals("events"))body.putIfAbsent("type","other");
        body.put("updatedAt",now());body.put("personId",personId);
        if(recordId==null){body.put("createdAt",now());body.put("createdBy",user.name());body.put("revision",1);recordId=insertRecord(personId,type,body);body.put("id",recordId);}
        else{long expected=version(input,"revision");body.put("revision",expected+1);if(db.update("UPDATE sh_record SET body=?,revision=revision+1 WHERE id=? AND org_id=? AND person_id=? AND record_type=? AND revision=?",json.write(body),recordId,user.org(),personId,type,expected)==0)throw ApiException.conflict();}
        if(!type.equals("events"))recordEvent(personId,type,recordId,before,body);
        audit(type+".save",Map.of("personId",personId,"recordId",recordId,"before",before,"after",body));
        if(type.equals("events")&&!str(body,"nextStep").isBlank()&&!str(body,"nextContactAt").isBlank())createFollowup(personId,body);
        if(type.equals("applications")&&before.isEmpty()&&id(body.get("opportunityId"))!=null){long opId=id(body.get("opportunityId"));Map<String,Object> op=record(personId,"opportunities",opId);saveRecord(personId,"opportunities",opId,Map.of("revision",op.get("revision"),"status","已转化","applicationId",recordId));}
        return record(personId,type,recordId);
    }
    private boolean equivalent(Object a,Object b){if(a==null||b==null)return blank(Objects.toString(a,""))&&blank(Objects.toString(b,""));try{return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;}catch(NumberFormatException e){return a.toString().equals(b.toString());}}
    private void createFollowup(long personId,Map<String,Object> body){Map<String,Object> task=new LinkedHashMap<>();task.put("title",body.get("nextStep"));task.put("dueAt",body.get("nextContactAt"));task.put("owner",blank(str(body,"owner"))?user.name():body.get("owner"));task.put("status","待完成");if(body.get("applicationId")!=null)task.put("applicationId",body.get("applicationId"));saveRecord(personId,"tasks",null,task);}
    private void recordEvent(long personId,String type,long recordId,Map<String,Object> before,Map<String,Object> body){
        Map<String,Object> event=new LinkedHashMap<>();String singular=type.equals("opportunities")?"opportunity":type.equals("experiences")?"experience":type.substring(0,type.length()-1);
        event.put("type",singular);event.put("title",(before.isEmpty()?"新增":"更新")+LABELS.getOrDefault(type,type));event.put("recordType",type);event.put("recordId",recordId);event.put("summary",summary(type,body));event.put("result",str(body,"result").isBlank()?str(body,"status"):body.get("result"));event.put("reason",str(body,"reason"));event.put("before",before);event.put("after",body);
        for(String key:List.of("applicationId","jobId","nextStep","nextContactAt","owner"))if(body.get(key)!=null)event.put(key,body.get(key));
        String occurred=now();if(before.isEmpty())for(String key:List.of("occurredAt","startedAt","startDate","scheduledAt")){if(!str(body,key).isBlank()){occurred=str(body,key);break;}}
        event.put("occurredAt",occurred);appendEvent(personId,event);
    }
    private String summary(String type,Map<String,Object> body){List<String> parts=new ArrayList<>();for(String key:List.of("title","jobName","projectName","organization","name","round","status","feedback","remark")){String text=str(body,key);if(!text.isBlank())parts.add(text);}if(type.equals("offers"))parts.add("第 "+body.get("version")+" 版 · "+body.get("salaryMin")+"–"+body.get("salaryMax")+"K/月");return String.join(" · ",parts);}
    private void appendEvent(long personId,Map<String,Object> event){event.put("createdAt",now());event.put("actor",user.name());event.put("revision",1);event.put("personId",personId);insertRecord(personId,"events",event);}
    long insertRecord(long personId,String type,Map<String,Object> body){GeneratedKeyHolder keys=new GeneratedKeyHolder();db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO sh_record(org_id,person_id,record_type,revision,body) VALUES(?,?,?,1,?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,user.org());p.setLong(2,personId);p.setString(3,type);p.setString(4,json.write(body));return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    public Map<String,Object> workspace(){Map<String,Object> result=new LinkedHashMap<>();for(String type:TYPES)result.put(type,recordsAcross(type));List<Map<String,Object>> people=persons();result.put("stats",list("","","",null,1,10).get("stats"));result.put("sources",group(people,"source"));result.put("stages",group(people,"status"));result.put("outcomes",group(people,"result"));result.put("jobs",group(people,"job"));return result;}
    private Map<String,Long> group(List<Map<String,Object>> people,String key){return people.stream().collect(Collectors.groupingBy(p->str(p,key).isBlank()?"未填写":str(p,key),LinkedHashMap::new,Collectors.counting()));}
    private List<Map<String,Object>> recordsAcross(String type){return db.query("SELECT r.id,r.revision,r.body,r.person_id,p.body person_body FROM sh_record r JOIN sh_person p ON p.id=r.person_id AND p.org_id=r.org_id WHERE r.org_id=? AND r.record_type=? ORDER BY r.id DESC",(r,n)->{Map<String,Object> item=json.read(r.getString("body"));item.put("id",r.getLong("id"));item.put("revision",r.getLong("revision"));item.put("personId",r.getLong("person_id"));item.put("personName",json.read(r.getString("person_body")).get("name"));item.remove("storageName");return item;},user.org(),type);}
    public List<Map<String,Object>> auditLog(){return db.query("SELECT id,actor,action,created_at,body FROM sh_audit WHERE org_id=? ORDER BY id DESC LIMIT 200",(r,n)->Map.of("id",r.getLong("id"),"actor",r.getString("actor"),"action",r.getString("action"),"createdAt",r.getString("created_at"),"details",json.read(r.getString("body"))),user.org());}
    private void audit(String action,Map<String,Object> data){db.update("INSERT INTO sh_audit(org_id,actor,action,created_at,body) VALUES(?,?,?,?,?)",user.org(),user.name(),action,now(),json.write(data));}
    private List<Map<String,Object>> rows(String sql,Object... args){return db.query(sql,(r,n)->{Map<String,Object> b=json.read(r.getString("body"));b.put("id",r.getLong("id"));b.put("version",r.getLong("revision"));return b;},args);}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> list=rows(sql,args);if(list.isEmpty())throw ApiException.missing();return list.get(0);}
    private List<Map<String,Object>> recordRows(String sql,Object...args){return db.query(sql,(r,n)->{Map<String,Object> b=json.read(r.getString("body"));b.put("id",r.getLong("id"));b.put("revision",r.getLong("revision"));return b;},args);}
    private Map<String,Object> recordOne(String sql,Object...args){List<Map<String,Object>> list=recordRows(sql,args);if(list.isEmpty())throw ApiException.missing();return list.get(0);}
    private void validType(String type){if(!TYPES.contains(type))throw ApiException.bad("不支持的记录类型");}
    private Map<String,Object> select(Map<String,Object> body,Set<String> fields){Map<String,Object> result=new LinkedHashMap<>();body.forEach((k,v)->{if(fields.contains(k))result.put(k,v instanceof String?((String)v).strip():v);});return result;}
    private static String now(){return OffsetDateTime.now().toString();}
    private static String str(Map<String,Object> body,String key){return Objects.toString(body.get(key),"").strip();}
    private static boolean blank(String text){return text==null||text.isBlank();}
    private static Long id(Object value){if(value==null||value.toString().isBlank())return null;try{long n=Long.parseLong(value.toString());if(n<=0)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw ApiException.bad("关联记录编号不正确");}}
    private static long version(Map<String,Object> body,String field){Long v=id(body.get(field));if(v==null)throw new ApiException(409,"VERSION_REQUIRED","请刷新后重试，更新记录需要 "+field);return v;}
    private void required(Map<String,Object> body,String key,String message){if(str(body,key).isBlank())throw ApiException.bad(message);}
    private void limit(Map<String,Object> body,String key,int max){if(str(body,key).length()>max)throw ApiException.bad(key+" 内容过长");}
    private void enumValue(Map<String,Object> body,String key,Set<String> allowed){if(!str(body,key).isBlank()&&!allowed.contains(str(body,key)))throw ApiException.bad(key+" 选项不正确");}
    private void reason(Map<String,Object> body,Map<String,Object> before){boolean closed=(CLOSED.contains(str(body,"status"))&&!Objects.equals(body.get("status"),before.get("status")))||(CLOSED.contains(str(body,"result"))&&!Objects.equals(body.get("result"),before.get("result")));if(closed&&str(body,"reason").isBlank())throw ApiException.bad("结束、拒绝或未入职时必须填写原因，以便保留完整历史");}
    private void validateIntent(Map<String,Object> body){enumValue(body,"companyIntent",Set.of("未判断","未知","低","一般","较高","很高","放弃","有兴趣","积极"));enumValue(body,"talentIntent",Set.of("未知","未判断","明确拒绝","暂不考虑","可以了解","有兴趣","积极","强烈"));}
    private void salary(Map<String,Object> body,String min,String max){
        if(blank(str(body,min)))body.put(min,null);if(blank(str(body,max)))body.put(max,null);
        if((body.get(min)==null)!=(body.get(max)==null))throw ApiException.bad("薪资最小值和最大值需要同时填写");
        decimal(body,min,BigDecimal.ZERO,new BigDecimal("100000"));decimal(body,max,BigDecimal.ZERO,new BigDecimal("100000"));
        if(body.get(min)!=null&&new BigDecimal(body.get(min).toString()).compareTo(new BigDecimal(body.get(max).toString()))>0)throw ApiException.bad("薪资最小值不能大于最大值");
    }
    private void decimal(Map<String,Object> body,String key,BigDecimal min,BigDecimal max){if(blank(str(body,key)))return;try{BigDecimal n=new BigDecimal(str(body,key));if(n.compareTo(min)<0||n.compareTo(max)>0||n.scale()>4)throw new NumberFormatException();body.put(key,n);}catch(NumberFormatException e){throw ApiException.bad(key+" 必须是合理范围内的非负数字");}}
    private void validateDates(Map<String,Object> body){for(String key:List.of("applyTime","occurredAt","startedAt","scheduledAt","expectedStartDate","expiresAt","startDate","endDate","dueAt","nextContactAt","birthday")){String v=str(body,key);if(!v.isBlank())parseDate(v,key);}for(List<String> pair:List.of(List.of("startDate","endDate"))){if(!str(body,pair.get(0)).isBlank()&&!str(body,pair.get(1)).isBlank()&&parseDate(str(body,pair.get(0)),pair.get(0)).isAfter(parseDate(str(body,pair.get(1)),pair.get(1))))throw ApiException.bad("结束日期不能早于开始日期");}}
    private Instant parseDate(String text,String key){try{if(text.length()==10)return LocalDate.parse(text).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();try{return OffsetDateTime.parse(text).toInstant();}catch(DateTimeParseException e){return LocalDateTime.parse(text.replace(' ','T')).atZone(ZoneId.of("Asia/Shanghai")).toInstant();}}catch(DateTimeParseException e){throw ApiException.bad(key+" 日期格式不正确");}}

    @Transactional public Map<String,Object> addUploadedAsset(long personId,Map<String,Object> asset){basePerson(personId);asset.put("createdAt",now());asset.put("createdBy",user.name());asset.put("revision",1);asset.put("personId",personId);asset.put("version",records(personId,"assets").stream().filter(a->str(a,"name").equals(str(asset,"name"))).count()+1);long id=insertRecord(personId,"assets",asset);asset.put("id",id);asset.put("downloadUrl","/candidate/"+personId+"/assets/"+id+"/download");asset.put("url",asset.get("downloadUrl"));db.update("UPDATE sh_record SET body=? WHERE id=? AND org_id=?",json.write(asset),id,user.org());recordEvent(personId,"assets",id,Map.of(),asset);audit("asset.upload",Map.of("personId",personId,"assetId",id,"name",asset.get("name")));Map<String,Object> result=json.copy(asset);result.remove("storageName");return result;}
    public Map<String,Object> downloadAsset(long personId,long assetId){basePerson(personId);Map<String,Object> asset=record(personId,"assets",assetId);if(!asset.containsKey("storageName"))throw ApiException.bad("此记录为外部链接，请使用原始链接访问");audit("asset.download",Map.of("personId",personId,"assetId",assetId));return asset;}
}
