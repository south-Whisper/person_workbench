package com.example.demo.controller.workspace;

import com.example.demo.service.audit.AuditService;
import com.example.demo.service.notification.InboxService;
import com.example.demo.service.workspace.WorkspaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class WorkspaceController {
    private final WorkspaceService workspace;
    private final InboxService inbox;
    private final AuditService audits;

    public WorkspaceController(WorkspaceService workspace, InboxService inbox, AuditService audits) {
        this.workspace = workspace;
        this.inbox = inbox;
        this.audits = audits;
    }

    @GetMapping("/workspace")
    public Map<String, Object> workspace() {
        return workspace.load();
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return audits.listRecent();
    }

    @GetMapping("/inbox")
    public Map<String, Object> inbox() {
        return inbox.list();
    }

    @PutMapping("/inbox/{messageId}/read")
    public Map<String, Object> readInboxMessage(@PathVariable String messageId) {
        return inbox.markRead(messageId);
    }

    @PutMapping("/inbox/read-all")
    public Map<String, Object> readAllInboxMessages() {
        return inbox.markAllRead();
    }
}


