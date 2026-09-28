package com.example.demo.controller;

import com.example.demo.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.*;

@RestController
public class CandidateController {
    private final TalentService service;private final Path uploads;
    private static final Set<String> ALLOWED=Set.of("pdf","doc","docx","png","jpg","jpeg","webp","ppt","pptx","xls","xlsx","csv","txt","zip","rtf","odt");
    public CandidateController(TalentService service,@Value("${sethub.data-dir:./data}") String dataDir){this.service=service;this.uploads=Path.of(dataDir).toAbsolutePath().normalize().resolve("uploads");}
    @GetMapping("/candidate/list") public Map<String,Object> list(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String source,@RequestParam(required=false) Long jobId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return service.list(search,status,source,jobId,page,size);}
    @GetMapping("/candidate/duplicates") public List<Map<String,Object>> duplicates(@RequestParam(defaultValue="") String phone,@RequestParam(defaultValue="") String email,@RequestParam(defaultValue="") String wechat,@RequestParam(defaultValue="") String name){return service.duplicates(phone,email,wechat,name);}
    @GetMapping("/candidate/{id}") public Map<String,Object> person(@PathVariable long id){return service.person(id);}
    @PostMapping("/candidate") public Map<String,Object> create(@RequestBody Map<String,Object> input){return service.savePerson(null,input);}
    @PutMapping("/candidate/{id}") public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object> input){return service.savePerson(id,input);}
    @PostMapping("/candidate/{id}/records/{type}") public Map<String,Object> addRecord(@PathVariable long id,@PathVariable String type,@RequestBody Map<String,Object> input){return service.saveRecord(id,type,null,input);}
    @PutMapping("/candidate/{id}/records/{type}/{recordId}") public Map<String,Object> updateRecord(@PathVariable long id,@PathVariable String type,@PathVariable long recordId,@RequestBody Map<String,Object> input){return service.saveRecord(id,type,recordId,input);}
    @GetMapping("/position/list") public List<Map<String,Object>> positions(){return service.positions();}
    @GetMapping("/position/{id}") public Map<String,Object> position(@PathVariable long id){return service.position(id);}
    @GetMapping("/position/{id}/versions") public List<Map<String,Object>> positionVersions(@PathVariable long id){return service.positionVersions(id);}
    @PostMapping({"/position","/position/list"}) public Map<String,Object> addPosition(@RequestBody Map<String,Object> input){return service.savePosition(null,input);}
    @PutMapping("/position/{id}") public Map<String,Object> updatePosition(@PathVariable long id,@RequestBody Map<String,Object> input){return service.savePosition(id,input);}
    @GetMapping("/workspace") public Map<String,Object> workspace(){return service.workspace();}
    @GetMapping("/audit") public List<Map<String,Object>> audit(){return service.auditLog();}
    @PostMapping(value="/candidate/{id}/assets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(@PathVariable long id,@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="简历") String type) throws Exception {
        service.person(id);
        if(file.isEmpty()||file.getSize()>20*1024*1024)throw ApiException.bad("文件不能为空且不得超过 20 MB");
        String original=Objects.requireNonNullElse(file.getOriginalFilename(),"附件").replace('\\','/');original=original.substring(original.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(original.length()>180)throw ApiException.bad("文件名过长");String extension=original.contains(".")?original.substring(original.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        if(!ALLOWED.contains(extension))throw ApiException.bad("支持 PDF、Office 文档、图片、CSV、TXT 和 ZIP 文件");
        String stored=UUID.randomUUID()+"."+extension;Files.createDirectories(uploads);Path target=uploads.resolve(stored).normalize();
        if(!target.startsWith(uploads))throw ApiException.bad("文件名不正确");
        byte[] bytes=file.getBytes();Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        try{Map<String,Object> asset=new LinkedHashMap<>();asset.put("name",original);asset.put("type",type);asset.put("storageName",stored);asset.put("size",file.getSize());asset.put("mimeType",Objects.requireNonNullElse(file.getContentType(),"application/octet-stream"));asset.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));asset.put("scanStatus","未扫描");return service.addUploadedAsset(id,asset);}catch(Exception e){Files.deleteIfExists(target);throw e;}
    }
    @GetMapping("/candidate/{id}/assets/{assetId}/download")
    public ResponseEntity<Resource> download(@PathVariable long id,@PathVariable long assetId) throws IOException {
        Map<String,Object> asset=service.downloadAsset(id,assetId);Path target=uploads.resolve(asset.get("storageName").toString()).normalize();
        if(!target.startsWith(uploads)||!Files.isRegularFile(target))throw new ApiException(404,"FILE_NOT_FOUND","附件文件不存在，请联系管理员检查存储目录");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(asset.get("name").toString(),StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(Files.size(target)).body(new FileSystemResource(target));
    }
}
