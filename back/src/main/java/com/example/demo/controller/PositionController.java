package com.example.demo.controller;

import com.example.demo.service.TalentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PositionController {
    private final TalentService talents;

    public PositionController(TalentService talents) {
        this.talents = talents;
    }

    @GetMapping("/position/list")
    public List<Map<String, Object>> positions() {
        return talents.positions();
    }

    @GetMapping("/position/{id}")
    public Map<String, Object> position(@PathVariable long id) {
        return talents.position(id);
    }

    @GetMapping("/position/{id}/versions")
    public List<Map<String, Object>> positionVersions(@PathVariable long id) {
        return talents.positionVersions(id);
    }

    @PostMapping({"/position", "/position/list"})
    public Map<String, Object> addPosition(@RequestBody Map<String, Object> input) {
        return talents.savePosition(null, input);
    }

    @PutMapping("/position/{id}")
    public Map<String, Object> updatePosition(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return talents.savePosition(id, input);
    }

    @PutMapping("/position/{id}/status")
    public Map<String, Object> updatePositionStatus(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return talents.changePositionStatus(id, input);
    }
}
