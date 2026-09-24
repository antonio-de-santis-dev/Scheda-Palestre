package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 400: semantic validation failures detected by services (after Bean Validation). */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String detail) {
        super(HttpStatus.BAD_REQUEST, code, detail, List.of());
    }

    public BadRequestException(String code, String detail, List<FieldViolation> errors) {
        super(HttpStatus.BAD_REQUEST, code, detail, errors);
    }

    public static BadRequestException field(String field, String message) {
        return new BadRequestException("VALIDATION_ERROR", "One or more fields are invalid",
                List.of(new FieldViolation(field, message)));
    }
}
