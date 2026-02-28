package com.jcpineda.filestore.common.api;

public record ApiErrorResponse(ErrorBody error) {

    public static ApiErrorResponse of(String code, String message, String correlationId) {
        return new ApiErrorResponse(new ErrorBody(code, message, correlationId));
    }

    public record ErrorBody(String code, String message, String correlationId) {
    }
}
