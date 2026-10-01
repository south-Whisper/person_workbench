package com.example.demo.controller;

import com.example.demo.service.TalentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrganizationController {
    private final TalentService talents;

    public OrganizationController(TalentService talents) {
        this.talents = talents;
    }

    @GetMapping("/employee/list")
    public List<Map<String, Object>> employees() {
        return talents.employees();
    }

    @GetMapping("/hr/list")
    public List<Map<String, Object>> hrs() {
        return talents.hrs();
    }

    @PutMapping("/employee/{id}/role")
    public Map<String, Object> changeEmployeeRole(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return talents.changeEmployeeRole(id, input);
    }

    @PutMapping("/employee/{id}/account")
    public Map<String, Object> updateEmployeeAccount(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return talents.updateEmployeeAccount(id, input);
    }

    @GetMapping("/company/list")
    public List<Map<String, Object>> companies() {
        return talents.companies();
    }
}
