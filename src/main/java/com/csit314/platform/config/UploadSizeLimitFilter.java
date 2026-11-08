package com.csit314.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartResolver;

import java.io.IOException;

// IDR-07 - Spring Security's CsrfFilter reads the CSRF token with
// request.getParameter(...), which on a multipart request silently triggers the servlet
// container's own multipart parsing. When that parsing fails because the upload is over
// spring.servlet.multipart's max-file-size/max-request-size, Tomcat just leaves the
// parameters empty instead of throwing there and then - so CsrfFilter sees no "_csrf"
// parameter, concludes the token is missing, and redirects to /search?csrfExpired. The
// designer never finds out their upload was actually too large.
//
// Resolving the multipart request here - in a filter registered to run before Spring
// Security's filter chain, see SecurityConfig.uploadSizeLimitFilterRegistration - makes
// the real MaxUploadSizeExceededException surface before CsrfFilter ever calls
// getParameter, so we can redirect with the real reason instead.
public class UploadSizeLimitFilter extends OncePerRequestFilter {

    private final MultipartResolver multipartResolver;

    public UploadSizeLimitFilter(MultipartResolver multipartResolver) {
        this.multipartResolver = multipartResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!multipartResolver.isMultipart(request)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest resolved;
        try {
            resolved = multipartResolver.resolveMultipart(request);
        } catch (MaxUploadSizeExceededException e) {
            response.sendRedirect(request.getContextPath() + "/search?uploadTooLarge");
            return;
        }
        chain.doFilter(resolved, response);
    }
}
