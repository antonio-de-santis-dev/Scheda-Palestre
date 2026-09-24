package com.gymplanner.shared.error;

/** A single invalid field reported inside a Problem Details {@code errors} array. */
public record FieldViolation(String field, String message) {
}
