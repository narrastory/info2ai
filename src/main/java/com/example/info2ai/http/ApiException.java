package com.example.info2ai.http;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final String subject;
    private final Integer resetInSeconds;
    public ApiException(int status,String code) { this(status,code,null); }
    public ApiException(int status,String code,String subject) { this(status,code,subject,null); }
    public ApiException(int status,String code,String subject,Throwable cause) {
        this(status,code,subject,cause,null);
    }
    public ApiException(int status, String code, int resetInSeconds) {
        this(status,code,null,null,resetInSeconds);
    }
    private ApiException(int status,String code,String subject,Throwable cause,Integer resetInSeconds) {
        super(code,cause);this.status=status;this.code=code;this.subject=subject;this.resetInSeconds=resetInSeconds;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String subject() { return subject; }
    public Integer resetInSeconds() { return resetInSeconds; }
}
