package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 404 for resources that either do not exist or are not visible to the caller. The two cases
 * are deliberately indistinguishable (spec section 3).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found", List.of());
    }
}
