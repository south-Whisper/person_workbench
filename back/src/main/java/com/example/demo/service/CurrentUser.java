package com.example.demo.service;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
@Component
public class CurrentUser {
    private final HttpServletRequest request;
    public CurrentUser(HttpServletRequest request){this.request=request;}
    public long org(){Object v=request.getAttribute("orgId");if(v==null)throw new ApiException(401,"UNAUTHORIZED","请先登录");return ((Number)v).longValue();}
    public String name(){Object v=request.getAttribute("username");return v==null?"系统":v.toString();}
}
