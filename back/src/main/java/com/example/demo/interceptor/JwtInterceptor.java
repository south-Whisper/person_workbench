package com.example.demo.interceptor;
import com.example.demo.utils.JwtUtil;
import com.example.demo.service.ApiException;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.*;
@Component
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtUtil jwt;private final JdbcTemplate db;
    public JwtInterceptor(JwtUtil jwt,JdbcTemplate db){this.jwt=jwt;this.db=db;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        if("OPTIONS".equals(request.getMethod()))return true;
        String token=request.getHeader("Authorization");
        long id;
        try {if(token==null)throw new IllegalArgumentException();id=jwt.getUserId(token);}catch(Exception e){throw new ApiException(401,"UNAUTHORIZED","登录已过期，请重新登录");}
        List<Map<String,Object>> users=db.queryForList("SELECT org_id,username,role FROM user WHERE id=? AND active=TRUE",id);
        if(users.isEmpty())throw new ApiException(401,"UNAUTHORIZED","账号不存在，请重新登录");
        Map<String,Object> u=users.get(0);request.setAttribute("orgId",u.get("org_id"));request.setAttribute("username",u.get("username"));request.setAttribute("userId",id);request.setAttribute("role",u.get("role"));
        if("AUDITOR".equalsIgnoreCase(String.valueOf(u.get("role")))&&!List.of("GET","HEAD","OPTIONS").contains(request.getMethod()))throw new ApiException(403,"FORBIDDEN","审计账号仅可查看数据");
        return true;
    }
}
