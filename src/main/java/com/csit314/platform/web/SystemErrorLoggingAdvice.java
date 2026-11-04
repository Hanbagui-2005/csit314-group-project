package com.csit314.platform.web;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

// PM-15 - every route already catches its own ServiceException locally and turns it
// into a flash message, so anything that reaches here is a genuinely unexpected error
// (a bug, not a user-facing validation failure). Logged to the database so Platform
// Management can see it on /platform-manager/error-log, not just in the console
@ControllerAdvice
public class SystemErrorLoggingAdvice {

    private static final Logger log = LoggerFactory.getLogger(SystemErrorLoggingAdvice.class);
    private static final int MAX_DETAIL_LENGTH = 4000;
    // matches SystemErrorLog.requestPath's own @Column(length = 300)
    private static final int MAX_REQUEST_PATH_LENGTH = 300;

    // PM-09/PM-12 - the only endpoints that return a file instead of a page. Matched by
    // path rather than return type (safer/simpler than resolving the original
    // HandlerMethod from inside a global exception handler) - add a new entry here if
    // another download/ResponseEntity endpoint is added later
    private static final Pattern DOWNLOAD_ENDPOINT = Pattern.compile(
            "^/platform-manager/reports/(export\\.csv|generated/.+)$");

    private final SystemErrorLogRepository systemErrorLogRepository;

    public SystemErrorLoggingAdvice(SystemErrorLogRepository systemErrorLogRepository) {
        this.systemErrorLogRepository = systemErrorLogRepository;
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpectedException(Exception exception, HttpServletRequest request,
                                            RedirectAttributes redirect) throws Exception {
        // ErrorResponse covers framework/client errors with their own correct status -
        // a missing static file (NoResourceFoundException, e.g. the browser's automatic
        // /favicon.ico request), a bad method, a type mismatch in a path variable, and
        // so on. None of those are bugs, so let Spring's own handling deal with them
        // instead of logging every one as a "system error" and bouncing the visitor to
        // /search with a false "something went wrong" message.
        //
        // Anthony's review - MaxUploadSizeExceededException (an over-the-limit photo
        // upload), HttpMessageNotReadableException and MissingServletRequestPartException
        // are the same kind of "the client did something invalid", but don't implement
        // ErrorResponse, so they were falling through to the "unexpected bug" branch below
        if (exception instanceof ErrorResponse
                || exception instanceof MaxUploadSizeExceededException
                || exception instanceof HttpMessageNotReadableException
                || exception instanceof MissingServletRequestPartException) {
            throw exception;
        }
        log.error("Unhandled exception on {}", request.getRequestURI(), exception);
        // Anthony's review - this used to be unguarded. If the save itself fails (the
        // DB being the actual problem, or a request path longer than the column's
        // 300-character limit under MySQL strict mode), the handler throws, the user
        // gets a raw 500, and the real exception is only ever in the log line above,
        // never in system_error_logs where Platform Management would see it
        try {
            systemErrorLogRepository.save(
                    new SystemErrorLog(truncatedRequestPath(request.getRequestURI()), stackTraceOf(exception)));
        } catch (Exception saveFailure) {
            log.error("Also failed to record the exception above to system_error_logs", saveFailure);
        }

        // Anthony's review - a download endpoint's caller expects a file or a plain
        // status back, not an HTML redirect with a flash message it'll never see
        if (DOWNLOAD_ENDPOINT.matcher(request.getRequestURI()).matches()) {
            return ResponseEntity.internalServerError().build();
        }

        // Anthony's review - redirecting to /search here is itself a request that can
        // fail (the DB being down, a bug in search), which would redirect right back
        // here and then to /search again - an endless loop, hammering
        // system_error_logs on every hop, until the browser gives up with
        // ERR_TOO_MANY_REDIRECTS. If /search is what actually failed, break the loop
        // with a plain error response instead of bouncing back into it
        if ("/search".equals(request.getRequestURI())) {
            return ResponseEntity.internalServerError().body("Something went wrong on our end. Please try again.");
        }

        redirect.addFlashAttribute("errorMessage", "Something went wrong on our end. Please try again.");
        return "redirect:/search";
    }

    private String truncatedRequestPath(String requestPath) {
        return requestPath.length() > MAX_REQUEST_PATH_LENGTH
                ? requestPath.substring(0, MAX_REQUEST_PATH_LENGTH)
                : requestPath;
    }

    private String stackTraceOf(Exception exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        String full = writer.toString();
        return full.length() > MAX_DETAIL_LENGTH ? full.substring(0, MAX_DETAIL_LENGTH) : full;
    }
}
