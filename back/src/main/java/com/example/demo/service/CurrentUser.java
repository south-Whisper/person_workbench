package com.example.demo.service;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
@Component
public class CurrentUser {
    private final HttpServletRequest request;
    public CurrentUser(HttpServletRequest request){this.request=request;}
    public long org(){Object v=request.getAttribute("orgId");if(v==null)throw new ApiException(401,"UNAUTHORIZED","请先登录");return ((Number)v).longValue();}
    public long id(){Object v=request.getAttribute("userId");if(v==null)throw new ApiException(401,"UNAUTHORIZED","请先登录");return ((Number)v).longValue();}
    public String name(){Object v=request.getAttribute("username");return v==null?"系统":v.toString();}
    public String role(){Object v=request.getAttribute("role");return v==null?"":v.toString();}
    public void requireAdmin(){if(!"ADMIN".equalsIgnoreCase(role()))throw new ApiException(403,"ADMIN_REQUIRED","只有管理员可以执行这个操作");}
}
