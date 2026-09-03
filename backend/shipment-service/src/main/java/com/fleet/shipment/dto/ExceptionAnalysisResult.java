package com.fleet.shipment.dto;



public record ExceptionAnalysisResult(
        boolean fallback,
        String reason,
        ShipmentExceptionResponse exception
) {
    public static ExceptionAnalysisResult success(ShipmentExceptionResponse exception) {
        return new ExceptionAnalysisResult(false, null, exception);
    }

    public static ExceptionAnalysisResult fallbackMode(String reason) {
        return new ExceptionAnalysisResult(true, reason, null);
    }
}
