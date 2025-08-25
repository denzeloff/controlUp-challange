package com.controlup.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Error response for API exceptions")
public record ErrorResponse(
        
        @Schema(description = "HTTP status code", example = "404")
        @JsonProperty("status")
        Integer status,
        
        @Schema(description = "Error message", example = "Device not found")
        @JsonProperty("message")
        String message,
        
        @Schema(description = "Error timestamp", example = "2023-12-19T10:30:00Z")
        @JsonProperty("timestamp")
        Instant timestamp,
        
        @Schema(description = "Request path that caused the error", example = "/metrics/devices/invalid-device")
        @JsonProperty("path")
        String path
) {
}