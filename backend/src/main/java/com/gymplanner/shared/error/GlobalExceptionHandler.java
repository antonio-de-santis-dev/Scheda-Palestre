package com.gymplanner.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single translation point from exceptions to RFC 9457 Problem Details. Every response carries
 * an application {@code code}; validation errors also carry an {@code errors} array.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://gymplanner/errors/";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.status(), ex.code(), titleFor(ex.status()), ex.getMessage(), ex.errors(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Conflict",
                "The resource was modified by someone else. Reload and retry.", List.of(), request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Conflict",
                "The resource is being modified. Retry.", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Constraint names are safe to log, values are not logged.
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), rootCauseType(ex));
        return build(HttpStatus.CONFLICT, "CONFLICT", "Conflict",
                "The request conflicts with the current state of the data", List.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed",
                "One or more fields are invalid", errors, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden",
                "You are not allowed to perform this operation", List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unauthorized",
                "Authentication is required", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
                "An unexpected error occurred", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        List<FieldViolation> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> new FieldViolation(ge.getObjectName(), ge.getDefaultMessage()))
                .toList();
        return validation(concat(errors, global), request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(parameterName(r), e.getDefaultMessage())))
                .toList();
        return validation(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Framework errors (malformed JSON, wrong method, missing params...) keep Spring's
        // Problem Detail but receive an application code as well.
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd
                : ProblemDetail.forStatusAndDetail(statusCode, "Request could not be processed");
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", defaultCodeFor(statusCode));
        }
        if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
            problem.setType(URI.create(TYPE_PREFIX + kebab(defaultCodeFor(statusCode))));
        }
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            problem.setDetail("Malformed request body");
        }
        setInstance(problem, request);
        return new ResponseEntity<>(problem, headers, statusCode);
    }

    private ResponseEntity<Object> validation(List<FieldViolation> errors, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("Validation failed");
        problem.setType(URI.create(TYPE_PREFIX + "validation"));
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        setInstance(problem, request);
        return ResponseEntity.badRequest().body(problem);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String title, String detail,
            List<FieldViolation> errors, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + ("VALIDATION_ERROR".equals(code) ? "validation" : kebab(code))));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private static void setInstance(ProblemDetail problem, WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            problem.setInstance(URI.create(swr.getRequest().getRequestURI()));
        }
    }

    private static String titleFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Validation failed";
            default -> status.getReasonPhrase();
        };
    }

    private static String defaultCodeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
        };
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "parameter" : name;
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static String kebab(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String rootCauseType(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String message = t.getMessage() == null ? "" : t.getMessage();
        int constraintIdx = message.indexOf("constraint \"");
        if (constraintIdx >= 0) {
            int end = message.indexOf('"', constraintIdx + 12);
            if (end > constraintIdx) {
                return t.getClass().getSimpleName() + " " + message.substring(constraintIdx, end + 1);
            }
        }
        return t.getClass().getSimpleName();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (b.isEmpty()) {
            return a;
        }
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
