package com.example.demo.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Component
public class JwtUtil {
    private final SecretKey key;
    public JwtUtil(@Value("${sethub.jwt-secret:}") String configured,@Value("${sethub.data-dir:./data}") String dataDir) throws Exception {
        byte[] secret;
        if(!configured.isBlank()) {
            secret=configured.getBytes(StandardCharsets.UTF_8);
            if(secret.length<32) throw new IllegalStateException("SETHUB_JWT_SECRET 至少需要 32 字节");
        } else {
            Path path=Path.of(dataDir).toAbsolutePath().normalize().resolve("jwt.key");
            Files.createDirectories(path.getParent());
            if(!Files.exists(path)) { byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);try {Files.writeString(path,Base64.getEncoder().encodeToString(bytes),StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){} }
            secret=Base64.getDecoder().decode(Files.readString(path).trim());
        }
        key=Keys.hmacShaKeyFor(secret);
    }
    public String createToken(long userId) {
        return Jwts.builder().subject(Long.toString(userId)).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+8*60*60*1000L)).signWith(key).compact();
    }
    public long getUserId(String token) {
        if(token.startsWith("Bearer ")) token=token.substring(7);
        return Long.parseLong(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
