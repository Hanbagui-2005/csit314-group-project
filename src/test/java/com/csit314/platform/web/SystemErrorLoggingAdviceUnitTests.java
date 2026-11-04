package com.csit314.platform.web;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// PM-15 - every route already catches its own ServiceException locally, so this only
// ever sees genuinely unexpected exceptions
@ExtendWith(MockitoExtension.class)
class SystemErrorLoggingAdviceUnitTests {

    @Mock SystemErrorLogRepository systemErrorLogRepository;
    SystemErrorLoggingAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new SystemErrorLoggingAdvice(systemErrorLogRepository);
    }

    @Test
    void logsTheExceptionAndRedirectsWithAFlashMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/designer/projects/new");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        Object view = advice.handleUnexpectedException(new NullPointerException("boom"), request, redirect);

        assertThat(view).isEqualTo("redirect:/search");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Something went wrong on our end. Please try again.");

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(systemErrorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestPath()).isEqualTo("/designer/projects/new");
        assertThat(captor.getValue().getDetails()).contains("NullPointerException").contains("boom");
    }

    @Test
    void truncatesAVeryLongStackTrace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/search");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String longMessage = "x".repeat(10000);

        advice.handleUnexpectedException(new RuntimeException(longMessage), request, redirect);

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(systemErrorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails().length()).isLessThanOrEqualTo(4000);
    }

    // a missing static file (the browser's automatic /favicon.ico request, a broken
    // /uploads/... link, a typo'd URL) is a normal 404, not a bug - it shouldn't fill
    // up the error log or bounce the visitor to /search with a false error message
    @Test
    void rethrowsFrameworkErrorsInsteadOfLoggingThem() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/favicon.ico");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        NoResourceFoundException notFound = new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");

        assertThatThrownBy(() -> advice.handleUnexpectedException(notFound, request, redirect))
                .isSameAs(notFound);

        verifyNoInteractions(systemErrorLogRepository);
    }

    // Anthony's review - these are all "the client did something invalid" the same way
    // a missing static file is, but none of them implement ErrorResponse, so they used
    // to fall through to the "unexpected bug" branch and get logged + redirected
    @Test
    void rethrowsAnOversizedUploadInsteadOfLoggingItAsABug() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/designer/projects");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MaxUploadSizeExceededException tooLarge = new MaxUploadSizeExceededException(10_000_000L);

        assertThatThrownBy(() -> advice.handleUnexpectedException(tooLarge, request, redirect))
                .isSameAs(tooLarge);

        verifyNoInteractions(systemErrorLogRepository);
    }

    @Test
    void rethrowsAnUnreadableRequestBodyInsteadOfLoggingItAsABug() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        HttpMessageNotReadableException malformed = new HttpMessageNotReadableException("bad body", (Throwable) null);

        assertThatThrownBy(() -> advice.handleUnexpectedException(malformed, request, redirect))
                .isSameAs(malformed);

        verifyNoInteractions(systemErrorLogRepository);
    }

    @Test
    void rethrowsAMissingRequestPartInsteadOfLoggingItAsABug() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MissingServletRequestPartException missingPart = new MissingServletRequestPartException("photos");

        assertThatThrownBy(() -> advice.handleUnexpectedException(missingPart, request, redirect))
                .isSameAs(missingPart);

        verifyNoInteractions(systemErrorLogRepository);
    }

    // Anthony's review - a download endpoint's caller (the CSV export / generated
    // report download) expects a file or a plain status back, not a 302 to an HTML
    // page with a flash message that request will never render
    @Test
    void returnsAPlainServerErrorInsteadOfRedirectingADownloadEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform-manager/reports/export.csv");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        Object result = advice.handleUnexpectedException(new RuntimeException("boom"), request, redirect);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result).getStatusCode().is5xxServerError()).isTrue();
        assertThat(redirect.getFlashAttributes()).isEmpty();
        verify(systemErrorLogRepository).save(any());
    }

    @Test
    void returnsAPlainServerErrorForAGeneratedReportDownloadToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform-manager/reports/generated/platform-activity-2026-11-03.csv");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        Object result = advice.handleUnexpectedException(new RuntimeException("boom"), request, redirect);

        assertThat(result).isInstanceOf(ResponseEntity.class);
    }
}
