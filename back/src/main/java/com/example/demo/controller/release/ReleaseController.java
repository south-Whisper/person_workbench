package com.example.demo.controller.release;

import com.example.demo.service.release.ReleaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

@RestController
public class ReleaseController {
    private final ReleaseService releases;

    public ReleaseController(ReleaseService releases) {
        this.releases = releases;
    }

    @GetMapping("/releases")
    public List<Map<String,Object>> releases() {
        return releases.releases();
    }
}

