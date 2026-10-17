package com.barbershop.common.error;

import com.barbershop.common.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into a localized problem detail with a stable {@code code}.
 * Business errors come in as {@link ApiException}; framework errors (validation,
 * malformed JSON, wrong parameters, 404/405) are mapped to their own codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problems;
    private final MessageSource messageSource;

    public GlobalExceptionHandler(ProblemFactory problems, MessageSource messageSource) {
        this.problems = problems;
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail p = problem(ex.status(), ex.code(), request, ex.args());
        ex.properties().forEach(p::setProperty);
        if (!ex.fieldErrors().isEmpty()) {
            Locale locale = LocaleContextHolder.getLocale();
            Map<String, String> errors = new LinkedHashMap<>();
            ex.fieldErrors().forEach((field, code) -> errors.put(field,
                    messageSource.getMessage(code, null, code, locale)));
            problems.withErrors(p, errors);
        }
        return respond(p);
    }

    /** Two writers on the same booking/payment at the same moment (row lock timeout). */
    @ExceptionHandler({PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleLocking(RuntimeException ex, HttpServletRequest request) {
        log.info("Concurrent modification on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(problem(HttpStatus.CONFLICT, "request.concurrent_update", request));
    }

    /** Integrity violations that slip past the services (the booking overlap is handled in BookingService). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(problem(HttpStatus.CONFLICT, "request.conflict", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", request));
    }

    // ── framework exceptions (ResponseEntityExceptionHandler hooks) ──────────────

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), messageSource.getMessage(fe, locale));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), messageSource.getMessage(ge, locale)));
        ProblemDetail p = problems.withErrors(problem(HttpStatus.BAD_REQUEST, "validation.failed", request), errors);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err -> errors.putIfAbsent(
                r.getMethodParameter().getParameterName(), messageSource.getMessage(err, locale))));
        ProblemDetail p = problems.withErrors(problem(HttpStatus.BAD_REQUEST, "validation.failed", request), errors);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "request.invalid_parameter", request, name));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "request.invalid_body", request));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "request.missing_parameter", request, ex.getParameterName()));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, "request.not_found", request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers, HttpStatusCode status,
                                                                         WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(problem(HttpStatus.METHOD_NOT_ALLOWED, "request.method_not_allowed", request));
    }

    /** Any other framework exception: keep its status, localize title/detail generically. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers)
                .body(problem(statusCode, "http." + statusCode.value() + ".detail", request));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ProblemDetail problem(HttpStatusCode status, String code, HttpServletRequest request, Object... args) {
        ProblemDetail p = problems.create(status, code, LocaleContextHolder.getLocale(), args);
        p.setInstance(URI.create(request.getRequestURI()));
        return p;
    }

    private ProblemDetail problem(HttpStatusCode status, String code, WebRequest request, Object... args) {
        return problem(status, code, ((ServletWebRequest) request).getRequest(), args);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail p) {
        return ResponseEntity.status(p.getStatus())
                .header("Content-Language", Messages.currentLang().code())
                .body(p);
    }
}
