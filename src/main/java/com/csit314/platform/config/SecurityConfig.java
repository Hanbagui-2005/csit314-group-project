package com.csit314.platform.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfig {

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
