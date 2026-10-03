package com.example.demo.controller.organization;

import com.example.demo.service.organization.EmployeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrganizationController {
    private final EmployeeService employees;

    public OrganizationController(EmployeeService employees) {
        this.employees = employees;
    }

    @GetMapping("/employee/list")
    public List<Map<String, Object>> employees() {
        return employees.employees();
    }

    @GetMapping("/hr/list")
    public List<Map<String, Object>> hrs() {
        return employees.hrs();
    }

    @PutMapping("/employee/{id}/role")
    public Map<String, Object> changeEmployeeRole(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return employees.changeRole(id, input);
    }

    @PutMapping("/employee/{id}/account")
    public Map<String, Object> updateEmployeeAccount(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return employees.updateAccount(id, input);
    }

    @GetMapping("/company/list")
    public List<Map<String, Object>> companies() {
        return employees.companies();
    }
}

