package com.bestzyx.prism.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bestzyx.prism.http.HttpMethod.GET;
import static com.bestzyx.prism.http.HttpMethod.POST;
import static java.util.Objects.nonNull;

/**
 * Created by zhangyongxiang on 2025/7/17 10:30
 *
 * @author zhangyongxiang
 */
@SuppressWarnings({ "unused", "FieldCanBeLocal" })
public final class SynchronousHttpClient {
    
    private static final Logger log = LoggerFactory
            .getLogger(SynchronousHttpClient.class);
    
    private final HttpClient client;
    
    private final String baseUrl;
    
    private final Duration connectTimeout;
    
    private final Duration readTimeout;
    
    private final ExecutorService executor;
    
    private final HttpClient.Redirect redirect;
    
    private final HttpClient.Version version;
    
    private final List<RequestBuilderInterceptor> interceptors;
    
    private String defaultContentType;
    
    public SynchronousHttpClient(String baseUrl) {
        this(baseUrl, new ArrayList<>());
    }
    
    public SynchronousHttpClient(String baseUrl,
            List<RequestBuilderInterceptor> interceptors) {
        this(baseUrl, Duration.ofMinutes(1), Duration.ofMinutes(1),
                Executors.newWorkStealingPool(), HttpClient.Redirect.ALWAYS,
                HttpClient.Version.HTTP_1_1, interceptors);
    }
    
    public SynchronousHttpClient(String baseUrl, Duration connectTimeout,
            Duration readTimeout, ExecutorService executor,
            HttpClient.Redirect redirect, HttpClient.Version version,
            List<RequestBuilderInterceptor> interceptors) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.executor = executor;
        this.redirect = redirect;
        this.version = version;
        this.interceptors = interceptors;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .executor(executor).followRedirects(redirect).version(version)
                .build();
        
    }
    
    public String getDefaultContentType() {
        return this.defaultContentType;
    }
    
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }
    
    public <T> T get(final String pathTemplate,
            final Map<String, String> pathParams,
            final Map<String, List<String>> queryParams,
            final Map<String, String> headers,
            final BodySerializer<T> bodySerializer) throws IOException,
            InterruptedException, HttpResponseNotOKException {
        return request(GET, pathTemplate, pathParams, queryParams, headers,
                null, null, bodySerializer);
    }
    
    public <R, T> T post(final String pathTemplate,
            final Map<String, String> pathParams,
            final Map<String, List<String>> queryParams,
            final Map<String, String> headers, final R body,
            final BodySerializer<R> requestBodySerializer,
            final BodySerializer<T> responseBodySerializer)
            throws HttpResponseNotOKException, IOException,
            InterruptedException {
        return request(POST, pathTemplate, pathParams, queryParams, headers,
                body, requestBodySerializer, responseBodySerializer);
    }
    
    public <R, T> T request(HttpMethod method, final String pathTemplate,
            final Map<String, String> pathParams,
            final Map<String, List<String>> queryParams,
            final Map<String, String> headers, final R body,
            final BodySerializer<R> requestBodySerializer,
            final BodySerializer<T> responseBodySerializer) throws IOException,
            InterruptedException, HttpResponseNotOKException {
        // 1. 填充 Path 参数
        String path = pathTemplate;
        if (pathParams != null) {
            for (final var entry : pathParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}",
                        encode(entry.getValue()));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "rendered path params. method: {}, path: {}, path template: {}, params: {}",
                    method, path, pathTemplate, pathParams);
        }
        // 2. 拼接 Query 参数
        String url = path;
        if (!path.startsWith("http")) {
            url = this.baseUrl + path;
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "rendered query params. method: {}, url: {}, query params: {}",
                    method, url, queryParams);
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            final StringJoiner joiner = new StringJoiner("&", "?", "");
            for (final var entry : queryParams.entrySet()) {
                final String key = encode(entry.getKey());
                for (final String value : entry.getValue()) {
                    joiner.add(key + "=" + encode(value));
                }
            }
            url += joiner.toString();
        }
        log.info("rendered url. method: {}, url: {}", method, url);
        
        final var requestBuilder = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofMinutes(1)).method(method.name(),
                        body != null
                                ? HttpRequest.BodyPublishers.ofByteArray(
                                        requestBodySerializer.serialize(body))
                                : HttpRequest.BodyPublishers.noBody());
        if (log.isDebugEnabled()) {
            log.info("created request. url: {}, body: {}", url, body);
        }
        if (nonNull(headers)) {
            headers.forEach(requestBuilder::header);
            if (log.isDebugEnabled()) {
                log.debug("set headers. url: {}, headers: {}", url, headers);
            }
        }
        this.interceptors
                .forEach(interceptor -> interceptor.intercept(requestBuilder));
        var request = requestBuilder.build();
        if (request.headers().map().keySet().stream().map(String::toLowerCase)
                .noneMatch(header -> header.equalsIgnoreCase("content-type"))) {
            if (nonNull(this.defaultContentType)) {
                requestBuilder.setHeader("Content-Type",
                        this.defaultContentType);
                request = requestBuilder.build();
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Sending request to {}. method: {}, url: {}, headers: {}",
                    this.baseUrl, method, url, request.headers().map());
        } else {
            log.info("Sending request to {}. method: {}, url: {}", this.baseUrl,
                    method, url);
        }
        final HttpResponse<byte[]> response = this.client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());
        if (log.isDebugEnabled()) {
            log.debug(
                    "Send request to {}. url: {}, status code: {}, response headers: {}, response body: {}",
                    this.baseUrl, url, response.statusCode(),
                    response.headers().map(), response.body());
        }
        if (response.statusCode() != 200) {
            throw new HttpResponseNotOKException(response.statusCode(),
                    new String(response.body()));
        } else {
            log.info("Sending request to {}. response ok", this.baseUrl);
        }
        return responseBodySerializer.deserialize(response.body());
        
    }
    
    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
}
