package com.csit314.platform.config;

import com.csit314.platform.model.Role;
import com.csit314.platform.web.session.AuthInterceptor;
import com.csit314.platform.web.session.LoginSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginSession loginSession;

    public WebConfig(LoginSession loginSession) {
        this.loginSession = loginSession;
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
    }
}
