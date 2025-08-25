package com.controlup.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing CPU usage percentile data for top N devices ranking")
public record TopDeviceMetricResponse(
        
        @Schema(description = "Unique identifier for the device", example = "device-456")
        @JsonProperty("deviceId")
        String deviceId,
        
        @Schema(description = "95th percentile of CPU usage", example = "95.2")
        @JsonProperty("percentile95")
        Double percentile95,
        
        @Schema(description = "Window start time in milliseconds (epoch)", example = "1734567890000")
        @JsonProperty("windowStart")
        Long windowStart,
        
        @Schema(description = "Window end time in milliseconds (epoch)", example = "1734567920000")
        @JsonProperty("windowEnd")
        Long windowEnd,
        
        @Schema(description = "Timestamp when this record was last updated (epoch milliseconds)", example = "1734567890000")
        @JsonProperty("lastUpdated")
        Long lastUpdated
) {
}