package com.bestzyx.prism.http;

/**
 * Created by zhangyongxiang on 2025/7/17 10:35
 *
 * @author zhangyongxiang
 */
public class HttpResponseNotOKException extends Exception {
    
    private static final long serialVersionUID = -6131962693312629206L;
    
    private final int statusCode;
    
    private final String message;
    
    public HttpResponseNotOKException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.message = message;
    }
    
    public int getStatusCode() {
        return this.statusCode;
    }
    
    @Override
    public String getMessage() {
        return this.message;
    }
}
