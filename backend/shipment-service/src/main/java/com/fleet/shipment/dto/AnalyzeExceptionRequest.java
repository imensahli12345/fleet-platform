package com.fleet.shipment.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeExceptionRequest(@NotBlank String rawText) {}
