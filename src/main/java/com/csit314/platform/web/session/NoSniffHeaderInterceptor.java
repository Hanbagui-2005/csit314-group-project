package com.csit314.platform.web.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// IDR-15 - stops a browser from sniffing an uploaded file's content and rendering it as
// HTML/SVG regardless of its stored type, so a file that slipped past FileStorageService's
// checks still can't execute as a script when it's opened from /uploads/**
public class NoSniffHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        return true;
    }
}
