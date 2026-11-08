package com.csit314.platform.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// IDR-07 - see UploadSizeLimitFilter's own comment for why this filter exists: without
// it, an oversized multipart upload gets misdiagnosed by Spring Security's CsrfFilter as
// an expired CSRF token instead of surfacing as a clear "too large" message
@ExtendWith(MockitoExtension.class)
class UploadSizeLimitFilterUnitTests {

    @Mock MultipartResolver multipartResolver;
    @Mock FilterChain chain;

    UploadSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UploadSizeLimitFilter(multipartResolver);
    }

    @Test
    void passesNonMultipartRequestsThroughUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(multipartResolver.isMultipart(request)).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(multipartResolver, never()).resolveMultipart(any());
    }

    @Test
    void passesTheResolvedMultipartRequestThroughWhenWithinTheSizeLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MultipartHttpServletRequest resolved = mock(MultipartHttpServletRequest.class);
        when(multipartResolver.isMultipart(request)).thenReturn(true);
        when(multipartResolver.resolveMultipart(request)).thenReturn(resolved);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(resolved, response);
    }

    @Test
    void redirectsWithTheRealReasonInsteadOfLettingCsrfFilterMisdiagnoseAnOversizedUpload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(multipartResolver.isMultipart(request)).thenReturn(true);
        when(multipartResolver.resolveMultipart(request))
                .thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/search?uploadTooLarge");
        verifyNoInteractions(chain);
    }
}
