package com.example.demo.controller;

import com.example.demo.service.TalentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class WorkspaceController {
    private final TalentService talents;

    public WorkspaceController(TalentService talents) {
        this.talents = talents;
    }

    @GetMapping("/workspace")
    public Map<String, Object> workspace() {
        return talents.workspace();
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return talents.auditLog();
    }

    @GetMapping("/inbox")
    public Map<String, Object> inbox() {
        return talents.inbox();
    }

    @PutMapping("/inbox/{messageId}/read")
    public Map<String, Object> readInboxMessage(@PathVariable String messageId) {
        return talents.markInboxRead(messageId);
    }

    @PutMapping("/inbox/read-all")
    public Map<String, Object> readAllInboxMessages() {
        return talents.markAllInboxRead();
    }
}
