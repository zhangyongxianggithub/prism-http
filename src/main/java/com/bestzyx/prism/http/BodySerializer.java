package com.bestzyx.prism.http;

import java.io.IOException;

/**
 * Created by zhangyongxiang on 2025/7/17 10:34
 *
 * @author zhangyongxiang
 */
public interface BodySerializer<T> {

    byte[] serialize(T t) throws IOException;
    
    T deserialize(byte[] bytes) throws IOException;
}
