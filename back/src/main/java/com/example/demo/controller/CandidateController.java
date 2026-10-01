package com.example.demo.controller;

import com.example.demo.service.AssetFileService;
import com.example.demo.service.TalentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class CandidateController {
    private final TalentService talents;
    private final AssetFileService files;

    public CandidateController(TalentService talents, AssetFileService files) {
        this.talents = talents;
        this.files = files;
    }

    @GetMapping("/candidate/list")
    public Map<String, Object> list(
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "") String source,
        @RequestParam(required = false) Long jobId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return talents.list(search, status, source, jobId, page, size);
    }

    @GetMapping("/candidate/duplicates")
    public List<Map<String, Object>> duplicates(
        @RequestParam(defaultValue = "") String phone,
        @RequestParam(defaultValue = "") String email,
        @RequestParam(defaultValue = "") String wechat,
        @RequestParam(defaultValue = "") String name
    ) {
        return talents.duplicates(phone, email, wechat, name);
    }

    @GetMapping("/candidate/{id}")
    public Map<String, Object> person(@PathVariable long id) {
        return talents.person(id);
    }

    @PostMapping("/candidate")
    public Map<String, Object> create(@RequestBody Map<String, Object> input) {
        return talents.savePerson(null, input);
    }

    @PutMapping("/candidate/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> input) {
        return talents.savePerson(id, input);
    }

    @PostMapping("/candidate/{id}/records/{type}")
    public Map<String, Object> addRecord(
        @PathVariable long id,
        @PathVariable String type,
        @RequestBody Map<String, Object> input
    ) {
        return talents.saveRecord(id, type, null, input);
    }

    @PutMapping("/candidate/{id}/records/{type}/{recordId}")
    public Map<String, Object> updateRecord(
        @PathVariable long id,
        @PathVariable String type,
        @PathVariable long recordId,
        @RequestBody Map<String, Object> input
    ) {
        return talents.saveRecord(id, type, recordId, input);
    }

    @PostMapping(value = "/candidate/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
        @PathVariable long id,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "简历") String type
    ) throws IOException {
        talents.person(id);
        AssetFileService.StoredAsset stored = files.store(file, type);
        try {
            Map<String, Object> result = talents.addUploadedAsset(id, stored.toMap());
            Object replaced = result.remove("_replacedStorageNames");
            if (replaced instanceof Collection<?> names) files.deleteAll(names);
            return result;
        } catch (Exception exception) {
            files.delete(stored.storageName());
            throw exception;
        }
    }

    @DeleteMapping("/candidate/{id}/assets/{assetId}")
    public Map<String, Object> deleteAsset(@PathVariable long id, @PathVariable long assetId) throws IOException {
        Map<String, Object> asset = talents.deleteAsset(id, assetId);
        files.delete(Objects.toString(asset.get("storageName"), ""));
        return Map.of("deleted", true, "assetId", assetId);
    }

    @GetMapping("/candidate/{id}/assets/{assetId}/download")
    public ResponseEntity<Resource> download(@PathVariable long id, @PathVariable long assetId) throws IOException {
        Map<String, Object> asset = talents.downloadAsset(id, assetId);
        Path target = files.requireFile(Objects.toString(asset.get("storageName"), ""));
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(Objects.toString(asset.get("name"), "附件"), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(target))
            .body(new FileSystemResource(target));
    }

    @GetMapping("/candidate/{id}/assets/{assetId}/preview")
    public Map<String, Object> preview(@PathVariable long id, @PathVariable long assetId) throws IOException {
        return files.preview(talents.downloadAsset(id, assetId));
    }
}
