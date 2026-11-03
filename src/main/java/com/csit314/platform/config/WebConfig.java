package com.csit314.platform.config;

import com.csit314.platform.model.Role;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.session.AuthInterceptor;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.NoSniffHeaderInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginSession loginSession;
    private final String uploadDir;
    private final UserAccountRepository userAccountRepository;

    public WebConfig(LoginSession loginSession, @Value("${app.upload-dir:uploads}") String uploadDir,
                     UserAccountRepository userAccountRepository) {
        this.loginSession = loginSession;
        this.uploadDir = uploadDir;
        this.userAccountRepository = userAccountRepository;
    }

    // serves IDR-05 photo uploads - saved outside the classpath so a rebuild doesn't wipe them
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IDR-15 - stops uploaded files from being sniffed/rendered as HTML or SVG
        registry.addInterceptor(new NoSniffHeaderInterceptor())
                .addPathPatterns("/uploads/**");

        registry.addInterceptor(new AuthInterceptor(loginSession, Role.ADMIN, "/admin/login", userAccountRepository))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");

        // every /platform-manager/** route, including PM-05/PM-07
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.PLATFORM_MANAGER, "/platform-manager/login",
                        userAccountRepository))
                .addPathPatterns("/platform-manager/**")
                .excludePathPatterns("/platform-manager/login");

        registry.addInterceptor(new AuthInterceptor(loginSession, Role.INTERIOR_DESIGNER, "/designer/login",
                        userAccountRepository))
                .addPathPatterns("/designer/**")
                .excludePathPatterns("/designer/login", "/designer/register");

        // the customer login is a modal on every page, so send them to browse where the navbar has it
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.CUSTOMER, "/search", userAccountRepository))
                .addPathPatterns("/favourites", "/favourites/**");

        // IDR-13/CUS-11/CUS-13/ADM-10/ADM-11 - only a logged-in customer can message, book,
        // rate, complain about a designer, or manage their own bookings
        registry.addInterceptor(new AuthInterceptor(loginSession, Role.CUSTOMER, "/search", userAccountRepository))
                .addPathPatterns("/designers/*/contact", "/designers/*/book", "/designers/*/rate",
                        "/designers/*/complaint", "/bookings", "/bookings/**");
    }
}
