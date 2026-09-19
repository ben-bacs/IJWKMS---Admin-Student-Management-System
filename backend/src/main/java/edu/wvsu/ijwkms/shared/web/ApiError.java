package edu.wvsu.ijwkms.shared.web;

import java.util.List;

public record ApiError(String code, String message, String correlationId, List<FieldViolation> violations) {

    public record FieldViolation(String field, String code, String message) {}
}
