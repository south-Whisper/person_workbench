package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 访问后端根地址时进入前端首页。
 */
@Controller
public class SpaController {
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
