package com.example.demo.config;
import com.example.demo.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor interceptor;
    public WebConfig(JwtInterceptor interceptor){this.interceptor=interceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).addPathPatterns("/**").excludePathPatterns("/auth/login","/auth/setup","/auth/setup/status","/public/questionnaires/**","/public/offers/**","/error","/","/index.html","/assets/**","/favicon.ico","/favicon.svg");}
}
