package com.example.spring.xhs.console;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class XhsConsoleWebConfiguration implements WebMvcConfigurer {

    static final HandlerInterceptor NO_CACHE_INTERCEPTOR = new HandlerInterceptor() {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setDateHeader(HttpHeaders.EXPIRES, 0);
            return true;
        }
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(NO_CACHE_INTERCEPTOR)
                .addPathPatterns("/xhs-console/**");
    }
}
