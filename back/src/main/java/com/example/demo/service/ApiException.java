package com.example.demo.service;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static ApiException bad(String message) { return new ApiException(400, "VALIDATION_ERROR", message); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "记录不存在或无权访问"); }
    public static ApiException conflict() { return new ApiException(409, "VERSION_CONFLICT", "记录已被其他操作更新，请刷新后重试"); }
}
