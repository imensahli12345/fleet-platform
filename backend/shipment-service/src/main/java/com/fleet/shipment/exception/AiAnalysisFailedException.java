package com.fleet.shipment.exception;

/**
 * Raised whenever ai-service fails: non-2xx status, timeout, network error,
 * or a severity/category value outside the known taxonomy.
 * Caught in ShipmentService to trigger Fallback Mode — never a raw 500.
 */
public class AiAnalysisFailedException extends RuntimeException {

    public AiAnalysisFailedException(String message) {
        super(message);
    }

    public AiAnalysisFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}