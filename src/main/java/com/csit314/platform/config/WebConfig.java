package com.csit314.platform.config;

import com.csit314.platform.model.Role;
import com.csit314.platform.web.session.AuthInterceptor;
import com.csit314.platform.web.session.LoginSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginSession loginSession;
    private final String uploadDir;

    public WebConfig(LoginSession loginSession, @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.loginSession = loginSession;
        this.uploadDir = uploadDir;
    }

    // serves IDR-05 photo uploads - saved outside the classpath so a rebuild doesn't wipe them
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.ADMIN, "/admin/login"))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");

        registry.addInterceptor(new AuthInterceptor(loginSession, Role.INTERIOR_DESIGNER, "/designer/login"))
                .addPathPatterns("/designer/**")
                .excludePathPatterns("/designer/login", "/designer/register");

        // the customer login is a modal on every page, so send them to browse where the navbar has it
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.CUSTOMER, "/search"))
                .addPathPatterns("/favourites", "/favourites/**");

        // IDR-13/CUS-11 - only a logged-in customer can message or book a designer
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.CUSTOMER, "/search"))
                .addPathPatterns("/designers/*/contact", "/designers/*/book");
    }
}
