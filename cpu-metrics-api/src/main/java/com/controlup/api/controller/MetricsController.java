package com.controlup.api.controller;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import com.controlup.api.dto.ErrorResponse;
import com.controlup.api.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/metrics")
@Validated
@Tag(name = "CPU Metrics API", description = "RESTful API for retrieving CPU usage percentile metrics")
public class MetricsController {

    private final MetricsService metricsService;

    @Autowired
    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(
            summary = "Get latest percentile metric for a specific device",
            description = "Retrieves the most recent 95th percentile CPU usage metric for the specified device"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved device metric",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DeviceMetricResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Device not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid device ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<DeviceMetricResponse> getDeviceMetric(
            @Parameter(description = "Unique identifier of the device", example = "device-123")
            @PathVariable
            @NotBlank(message = "Device ID cannot be blank")
            String deviceId) {
        
        DeviceMetricResponse response = metricsService.getLatestMetricForDevice(deviceId);
        
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top/{n}")
    @Operation(
            summary = "Get top N devices by 95th percentile",
            description = "Retrieves the top N devices ranked by their 95th percentile CPU usage from the most recent complete window"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved top devices",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TopDeviceMetricResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid parameter value",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<List<TopDeviceMetricResponse>> getTopDevices(
            @Parameter(description = "Number of top devices to retrieve (1-100)", example = "10")
            @PathVariable
            @Min(value = 1, message = "N must be at least 1")
            @Max(value = 100, message = "N cannot exceed 100")
            Integer n) {
        
        List<TopDeviceMetricResponse> response = metricsService.getTopDevicesByPercentile(n);
        return ResponseEntity.ok(response);
    }
}