package com.csit314.platform.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.multipart.MultipartResolver;

@Configuration
public class SecurityConfig {

    // IDR-07 - must run before Spring Security's own filter chain (registered at
    // SecurityProperties.DEFAULT_FILTER_ORDER = -100) - see UploadSizeLimitFilter's own
    // comment for why
    @Bean
    public FilterRegistrationBean<UploadSizeLimitFilter> uploadSizeLimitFilterRegistration(
            MultipartResolver multipartResolver) {
        FilterRegistrationBean<UploadSizeLimitFilter> registration =
                new FilterRegistrationBean<>(new UploadSizeLimitFilter(multipartResolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth ->
                auth.anyRequest().permitAll()
            )
            .csrf(Customizer.withDefaults())
            .exceptionHandling(ex -> ex.accessDeniedHandler(
                (request, response, exception) -> {
                    if (exception instanceof CsrfException) {
                        response.sendRedirect(
                            request.getContextPath()
                                + "/search?csrfExpired"
                        );
                    } else {
                        response.sendError(
                            HttpServletResponse.SC_FORBIDDEN
                        );
                    }
                }
            ))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
