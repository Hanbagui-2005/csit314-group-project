package com.csit314.platform.web;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

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
    void logsTheExceptionAndRedirectsWithAFlashMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/designer/projects/new");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = advice.handleUnexpectedException(new NullPointerException("boom"), request, redirect);

        assertThat(view).isEqualTo("redirect:/search");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Something went wrong on our end. Please try again.");

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(systemErrorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestPath()).isEqualTo("/designer/projects/new");
        assertThat(captor.getValue().getDetails()).contains("NullPointerException").contains("boom");
    }

    @Test
    void truncatesAVeryLongStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/search");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String longMessage = "x".repeat(10000);

        advice.handleUnexpectedException(new RuntimeException(longMessage), request, redirect);

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(systemErrorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails().length()).isLessThanOrEqualTo(4000);
    }
}
