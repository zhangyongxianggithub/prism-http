package com.bestzyx.prism.http;

import java.net.http.HttpRequest;

/**
 * Created by zhangyongxiang on 2025/7/17 11:50
 *
 * @author zhangyongxiang
 */
@FunctionalInterface
public interface RequestBuilderInterceptor {
    
    void intercept(HttpRequest.Builder requestBuilder);
    
}
