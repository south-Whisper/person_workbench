package com.example.demo.controller;
import com.example.demo.service.*;
import com.example.demo.utils.JwtUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JdbcTemplate db;private final BCryptPasswordEncoder passwords;private final JwtUtil jwt;
    public AuthController(JdbcTemplate db,BCryptPasswordEncoder passwords,JwtUtil jwt){this.db=db;this.passwords=passwords;this.jwt=jwt;}
    @GetMapping("/setup/status") public Map<String,Object> setupStatus(){return Map.of("needsSetup",db.queryForObject("SELECT COUNT(*) FROM sh_user",Integer.class)==0);}
    @PostMapping("/setup") @Transactional
    public Map<String,Object> setup(@RequestBody Map<String,Object> input){
        db.queryForObject("SELECT version FROM sh_migration WHERE version='schema-v1' FOR UPDATE",String.class);
        if(db.queryForObject("SELECT COUNT(*) FROM sh_user",Integer.class)>0)throw new ApiException(409,"SETUP_COMPLETE","系统已有账号，请使用现有账号登录");
        String username=Objects.toString(input.get("username"),"").trim(),password=Objects.toString(input.get("password"),"");
        if(username.length()<2||username.length()>80)throw ApiException.bad("用户名需为 2 至 80 个字符");
        if(password.length()<8||password.length()>72)throw ApiException.bad("密码需为 8 至 72 个字符");
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO sh_user(org_id,username,password,role) VALUES(1,?,?,'ADMIN')",Statement.RETURN_GENERATED_KEYS);p.setString(1,username);p.setString(2,passwords.encode(password));return p;},key);
        audit("initial_setup",username);
        return result(Objects.requireNonNull(key.getKey()).longValue(),username,"ADMIN");
    }
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,Object> input){
        String username=Objects.toString(input.get("username"),"").trim(),password=Objects.toString(input.get("password"),"");
        if(username.isBlank()||password.isBlank()||username.length()>120||password.length()>72)throw new ApiException(401,"INVALID_CREDENTIALS","用户名或密码错误");
        List<Map<String,Object>> users=db.queryForList("SELECT id,username,password,role FROM sh_user WHERE username=?",username);
        if(users.isEmpty()||!passwords.matches(password,users.get(0).get("password").toString()))throw new ApiException(401,"INVALID_CREDENTIALS","用户名或密码错误");
        Map<String,Object> user=users.get(0);audit("login",username);return result(((Number)user.get("id")).longValue(),username,user.get("role").toString());
    }
    private Map<String,Object> result(long id,String username,String role){return Map.of("token",jwt.createToken(id),"username",username,"role",role,"orgId",1);}
    private void audit(String action,String username){db.update("INSERT INTO sh_audit(org_id,actor,action,created_at,body) VALUES(1,?,?,?,?)",username,action,java.time.OffsetDateTime.now().toString(),"{}");}
}
