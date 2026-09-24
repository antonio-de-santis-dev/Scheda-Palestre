package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 409: duplicates or concurrency/state conflicts. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String detail) {
        super(HttpStatus.CONFLICT, code, detail, List.of());
    }

    public ConflictException(String code, String detail, List<FieldViolation> errors) {
        super(HttpStatus.CONFLICT, code, detail, errors);
    }
}
