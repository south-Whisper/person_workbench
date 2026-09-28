package com.example.demo.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] origins;
    public CorsConfig(@Value("${sethub.cors-origins}") String origins){this.origins=origins.split(",");}
    @Override public void addCorsMappings(CorsRegistry registry){registry.addMapping("/**").allowedOriginPatterns(origins).allowedMethods("GET","POST","PUT","OPTIONS","HEAD").allowedHeaders("Authorization","Content-Type","Idempotency-Key").exposedHeaders("Content-Disposition").allowCredentials(false);}
}
