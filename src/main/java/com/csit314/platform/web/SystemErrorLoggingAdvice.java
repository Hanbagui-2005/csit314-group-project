package com.csit314.platform.web;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;

// PM-15 - every route already catches its own ServiceException locally and turns it
// into a flash message, so anything that reaches here is a genuinely unexpected error
// (a bug, not a user-facing validation failure). Logged to the database so Platform
// Management can see it on /platform-manager/error-log, not just in the console
@ControllerAdvice
public class SystemErrorLoggingAdvice {

    private static final Logger log = LoggerFactory.getLogger(SystemErrorLoggingAdvice.class);
    private static final int MAX_DETAIL_LENGTH = 4000;

    private final SystemErrorLogRepository systemErrorLogRepository;

    public SystemErrorLoggingAdvice(SystemErrorLogRepository systemErrorLogRepository) {
        this.systemErrorLogRepository = systemErrorLogRepository;
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpectedException(Exception exception, HttpServletRequest request,
                                            RedirectAttributes redirect) {
        log.error("Unhandled exception on {}", request.getRequestURI(), exception);
        systemErrorLogRepository.save(new SystemErrorLog(request.getRequestURI(), stackTraceOf(exception)));
        redirect.addFlashAttribute("errorMessage", "Something went wrong on our end. Please try again.");
        return "redirect:/search";
    }

    private String stackTraceOf(Exception exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        String full = writer.toString();
        return full.length() > MAX_DETAIL_LENGTH ? full.substring(0, MAX_DETAIL_LENGTH) : full;
    }
}
