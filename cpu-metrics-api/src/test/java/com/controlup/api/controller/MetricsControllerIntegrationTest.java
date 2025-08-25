package com.controlup.api.controller;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import com.controlup.api.service.MetricsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("GET /metrics/devices/{deviceId} Tests")
    class GetDeviceMetricTests {

        @Test
        @DisplayName("Should return 200 with device metric when device exists")
        void shouldReturn200WithDeviceMetricWhenDeviceExists() throws Exception {
            // Given: Mock service response
            DeviceMetricResponse mockResponse = new DeviceMetricResponse(
                "api-test-device-001",
                78.9,
                1734567890000L,
                1734567920000L,
                1734567890000L
            );
            when(metricsService.getLatestMetricForDevice("api-test-device-001"))
                .thenReturn(mockResponse);

            // When/Then: GET request should return device metric
            mockMvc.perform(get("/metrics/devices/{deviceId}", "api-test-device-001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deviceId", is("api-test-device-001")))
                .andExpect(jsonPath("$.percentile95", is(78.9)))
                .andExpect(jsonPath("$.windowStart", is(1734567890000L)))
                .andExpect(jsonPath("$.windowEnd", is(1734567920000L)))
                .andExpect(jsonPath("$.lastUpdated", is(1734567890000L)))
                .andDo(print());
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenDeviceNotFound() throws Exception {
            // Given: Mock service returns null
            when(metricsService.getLatestMetricForDevice("non-existent-device"))
                .thenReturn(null);

            // When/Then: GET request for non-existent device should return 404
            mockMvc.perform(get("/metrics/devices/{deviceId}", "non-existent-device"))
                .andExpect(status().isNotFound())
                .andDo(print());
        }

        @Test
        @DisplayName("Should return 400 for invalid device ID")
        void shouldReturn400ForInvalidDeviceId() throws Exception {
            // Given: Mock service for whitespace-only device ID to trigger validation
            when(metricsService.getLatestMetricForDevice("   "))
                .thenReturn(null);

            // Test with whitespace-only device ID - should trigger @NotBlank validation
            mockMvc.perform(get("/metrics/devices/{deviceId}", "   "))
                .andExpect(status().isBadRequest())
                .andDo(print());
        }

        @Test
        @DisplayName("Should handle special characters in device ID")
        void shouldHandleSpecialCharactersInDeviceId() throws Exception {
            // Given: Mock response for device with special characters
            String specialDeviceId = "device-with-special_chars.123-test@domain";
            DeviceMetricResponse mockResponse = new DeviceMetricResponse(
                specialDeviceId,
                45.6,
                Instant.now().minusSeconds(60).toEpochMilli(),
                Instant.now().toEpochMilli(),
                Instant.now().toEpochMilli()
            );
            when(metricsService.getLatestMetricForDevice(specialDeviceId))
                .thenReturn(mockResponse);

            // When/Then: Should handle special characters correctly
            mockMvc.perform(get("/metrics/devices/{deviceId}", specialDeviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId", is(specialDeviceId)))
                .andExpect(jsonPath("$.percentile95", is(45.6)))
                .andDo(print());
        }

        @ParameterizedTest
        @CsvSource({
            "device-zero, 0.0",
            "device-max, 100.0",
            "device-precision, 99.999",
            "device-low-precision, 0.001"
        })
        @DisplayName("Should handle edge case percentile values")
        void shouldHandleEdgeCasePercentileValues(String deviceId, double percentile) throws Exception {
            // Given: Mock response for the specific edge case
            long currentTime = Instant.now().toEpochMilli();
            
            when(metricsService.getLatestMetricForDevice(deviceId))
                .thenReturn(new DeviceMetricResponse(deviceId, percentile, currentTime - 60000, currentTime, currentTime));

            // When/Then: Should handle the edge case
            mockMvc.perform(get("/metrics/devices/{deviceId}", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId", is(deviceId)))
                .andExpect(jsonPath("$.percentile95", is(percentile)))
                .andDo(print());
        }

        @Test
        @DisplayName("Should validate response structure and content types")
        void shouldValidateResponseStructureAndContentTypes() throws Exception {
            // Given: Mock response
            DeviceMetricResponse mockResponse = new DeviceMetricResponse(
                "structure-test-device",
                67.8,
                1734567890000L,
                1734567920000L,
                1734567890000L
            );
            when(metricsService.getLatestMetricForDevice("structure-test-device"))
                .thenReturn(mockResponse);

            // When: Making request
            MvcResult result = mockMvc.perform(get("/metrics/devices/{deviceId}", "structure-test-device"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            // Then: Response should have correct structure
            String jsonResponse = result.getResponse().getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            assertThat(jsonNode.has("deviceId")).isTrue();
            assertThat(jsonNode.has("percentile95")).isTrue();
            assertThat(jsonNode.has("windowStart")).isTrue();
            assertThat(jsonNode.has("windowEnd")).isTrue();
            assertThat(jsonNode.has("lastUpdated")).isTrue();

            // Verify data types
            assertThat(jsonNode.get("deviceId").isTextual()).isTrue();
            assertThat(jsonNode.get("percentile95").isNumber()).isTrue();
            assertThat(jsonNode.get("windowStart").isNumber()).isTrue();
            assertThat(jsonNode.get("windowEnd").isNumber()).isTrue();
            assertThat(jsonNode.get("lastUpdated").isNumber()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /metrics/top/{n} Tests")
    class GetTopDevicesTests {

        @Test
        @DisplayName("Should return 200 with top devices ordered by percentile")
        void shouldReturn200WithTopDevicesOrderedByPercentile() throws Exception {
            // Given: Mock service response with ordered devices
            List<TopDeviceMetricResponse> mockResponse = Arrays.asList(
                new TopDeviceMetricResponse("device-critical", 98.5, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-high", 87.2, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-medium", 56.7, 1734567890000L, 1734567920000L, 1734567890000L)
            );
            when(metricsService.getTopDevicesByPercentile(3))
                .thenReturn(mockResponse);

            // When/Then: GET top 3 should return devices in descending order
            mockMvc.perform(get("/metrics/top/{n}", 3))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].deviceId", is("device-critical")))
                .andExpect(jsonPath("$[0].percentile95", is(98.5)))
                .andExpect(jsonPath("$[1].deviceId", is("device-high")))
                .andExpect(jsonPath("$[1].percentile95", is(87.2)))
                .andExpect(jsonPath("$[2].deviceId", is("device-medium")))
                .andExpect(jsonPath("$[2].percentile95", is(56.7)))
                .andDo(print());
        }

        @Test
        @DisplayName("Should return empty array when no data exists")
        void shouldReturnEmptyArrayWhenNoDataExists() throws Exception {
            // Given: Mock service returns empty list
            when(metricsService.getTopDevicesByPercentile(5))
                .thenReturn(List.of());

            // When/Then: GET top devices with no data should return empty array
            mockMvc.perform(get("/metrics/top/{n}", 5))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)))
                .andDo(print());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 50, 100})
        @DisplayName("Should accept valid N parameter values")
        void shouldAcceptValidNParameterValues(int n) throws Exception {
            // Given: Mock service response
            when(metricsService.getTopDevicesByPercentile(n))
                .thenReturn(Arrays.asList(
                    new TopDeviceMetricResponse("device-1", 75.0, 1734567890000L, 1734567920000L, 1734567890000L),
                    new TopDeviceMetricResponse("device-2", 65.0, 1734567890000L, 1734567920000L, 1734567890000L)
                ));

            // When/Then: Valid N values should work
            mockMvc.perform(get("/metrics/top/{n}", n))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andDo(print());
        }

        @ParameterizedTest
        @CsvSource({
            "0, N must be at least 1",
            "-1, N must be at least 1",
            "101, N cannot exceed 100"
        })
        @DisplayName("Should return 400 for invalid N parameter values")
        void shouldReturn400ForInvalidNParameterValues(int n, String expectedMessage) throws Exception {
            // When/Then: Invalid N values should return 400
            mockMvc.perform(get("/metrics/top/{n}", n))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(expectedMessage)))
                .andDo(print());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.5", "null"})
        @DisplayName("Should handle non-numeric N parameter")
        void shouldHandleNonNumericNParameter(String invalidN) throws Exception {
            // When/Then: Non-numeric N should return 400
            mockMvc.perform(get("/metrics/top/{n}", invalidN))
                .andExpect(status().isBadRequest())
                .andDo(print());
        }

        @Test
        @DisplayName("Should handle empty N parameter as static resource request")
        void shouldHandleEmptyNParameterAsStaticResourceRequest() throws Exception {
            // When/Then: Empty N parameter results in static resource lookup, converted to 500 by global handler
            mockMvc.perform(get("/metrics/top/"))
                .andExpect(status().isInternalServerError())
                .andDo(print());
        }

        @Test
        @DisplayName("Should handle identical percentile values correctly")
        void shouldHandleIdenticalPercentileValuesCorrectly() throws Exception {
            // Given: Mock response with identical percentiles
            List<TopDeviceMetricResponse> mockResponse = Arrays.asList(
                new TopDeviceMetricResponse("device-higher", 85.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-identical-0", 75.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-identical-1", 75.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-identical-2", 75.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-identical-3", 75.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-identical-4", 75.0, 1734567890000L, 1734567920000L, 1734567890000L)
            );
            when(metricsService.getTopDevicesByPercentile(6))
                .thenReturn(mockResponse);

            // When/Then: Should handle identical values
            mockMvc.perform(get("/metrics/top/{n}", 6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].percentile95", is(85.0))) // Highest first
                .andExpect(jsonPath("$[1].percentile95", is(75.0))) // Then identical values
                .andExpect(jsonPath("$[2].percentile95", is(75.0)))
                .andExpect(jsonPath("$[3].percentile95", is(75.0)))
                .andExpect(jsonPath("$[4].percentile95", is(75.0)))
                .andExpect(jsonPath("$[5].percentile95", is(75.0)))
                .andDo(print());
        }

        @Test
        @DisplayName("Should validate response structure for top devices")
        void shouldValidateResponseStructureForTopDevices() throws Exception {
            // Given: Mock response
            List<TopDeviceMetricResponse> mockResponse = Arrays.asList(
                new TopDeviceMetricResponse("device-1", 85.0, 1734567890000L, 1734567920000L, 1734567890000L),
                new TopDeviceMetricResponse("device-2", 75.0, 1734567890000L, 1734567920000L, 1734567890000L)
            );
            when(metricsService.getTopDevicesByPercentile(2))
                .thenReturn(mockResponse);

            // When: Making request
            MvcResult result = mockMvc.perform(get("/metrics/top/{n}", 2))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            // Then: Response should have correct array structure
            String jsonResponse = result.getResponse().getContentAsString();
            JsonNode jsonArray = objectMapper.readTree(jsonResponse);

            assertThat(jsonArray.isArray()).isTrue();
            assertThat(jsonArray.size()).isEqualTo(2);

            // Each element should have correct structure
            for (JsonNode device : jsonArray) {
                assertThat(device.has("deviceId")).isTrue();
                assertThat(device.has("percentile95")).isTrue();
                assertThat(device.has("windowStart")).isTrue();
                assertThat(device.has("windowEnd")).isTrue();
                assertThat(device.has("lastUpdated")).isTrue();

                // Verify data types
                assertThat(device.get("deviceId").isTextual()).isTrue();
                assertThat(device.get("percentile95").isNumber()).isTrue();
                assertThat(device.get("windowStart").isNumber()).isTrue();
                assertThat(device.get("windowEnd").isNumber()).isTrue();
                assertThat(device.get("lastUpdated").isNumber()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("HTTP Headers and CORS Tests")
    class HttpHeadersAndCorsTests {

        @Test
        @DisplayName("Should set correct response headers")
        void shouldSetCorrectResponseHeaders() throws Exception {
            // Given: Mock responses
            DeviceMetricResponse deviceResponse = new DeviceMetricResponse(
                "headers-test-device", 55.5, 1734567890000L, 1734567920000L, 1734567890000L
            );
            when(metricsService.getLatestMetricForDevice("headers-test-device"))
                .thenReturn(deviceResponse);
                
            List<TopDeviceMetricResponse> topResponse = Arrays.asList(
                new TopDeviceMetricResponse("headers-test-device", 55.5, 1734567890000L, 1734567920000L, 1734567890000L)
            );
            when(metricsService.getTopDevicesByPercentile(1))
                .thenReturn(topResponse);

            // When/Then: Check response headers
            mockMvc.perform(get("/metrics/devices/{deviceId}", "headers-test-device"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andDo(print());

            mockMvc.perform(get("/metrics/top/{n}", 1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andDo(print());
        }

        @Test
        @DisplayName("Should allow GET method")
        void shouldAllowGetMethod() throws Exception {
            // Given: Mock response for successful GET
            DeviceMetricResponse mockResponse = new DeviceMetricResponse(
                "method-test-device", 44.4, 1734567890000L, 1734567920000L, 1734567890000L
            );
            when(metricsService.getLatestMetricForDevice("method-test-device"))
                .thenReturn(mockResponse);

            // When/Then: GET should work
            mockMvc.perform(get("/metrics/devices/{deviceId}", "method-test-device"))
                .andExpect(status().isOk())
                .andDo(print());
        }

        @ParameterizedTest
        @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
        @DisplayName("Should reject non-GET HTTP methods")
        void shouldRejectNonGetHttpMethods(String method) throws Exception {
            // When/Then: Non-GET methods should return 500 (Global exception handler converts 405 to 500)
            switch (method) {
                case "POST" -> mockMvc.perform(post("/metrics/devices/{deviceId}", "method-test-device"))
                    .andExpect(status().isInternalServerError())
                    .andDo(print());
                case "PUT" -> mockMvc.perform(put("/metrics/devices/{deviceId}", "method-test-device"))
                    .andExpect(status().isInternalServerError())
                    .andDo(print());
                case "DELETE" -> mockMvc.perform(delete("/metrics/devices/{deviceId}", "method-test-device"))
                    .andExpect(status().isInternalServerError())
                    .andDo(print());
                case "PATCH" -> mockMvc.perform(patch("/metrics/devices/{deviceId}", "method-test-device"))
                    .andExpect(status().isInternalServerError())
                    .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed requests gracefully")
        void shouldHandleMalformedRequestsGracefully() throws Exception {
            // When/Then: Invalid endpoints return 500 (Global exception handler converts resource not found to 500)
            mockMvc.perform(get("/metrics/invalid-endpoint"))
                .andExpect(status().isInternalServerError())
                .andDo(print());
                
            mockMvc.perform(get("/invalid-path"))
                .andExpect(status().isInternalServerError())
                .andDo(print());
        }

        @Test
        @DisplayName("Should provide meaningful error responses")
        void shouldProvideMeaningfulErrorResponses() throws Exception {
            // When/Then: Error responses should be informative
            MvcResult result = mockMvc.perform(get("/metrics/top/{n}", 0))
                .andExpect(status().isBadRequest())
                .andReturn();

            String errorResponse = result.getResponse().getContentAsString();
            assertThat(errorResponse).isNotEmpty();
            
            // Should contain validation error information
            assertThat(errorResponse).containsAnyOf("must be at least 1", "validation", "error");
        }

        @Test
        @DisplayName("Should handle service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() throws Exception {
            // Given: Service throws exception
            when(metricsService.getLatestMetricForDevice("error-device"))
                .thenThrow(new RuntimeException("Database connection failed"));

            // When/Then: Should handle exceptions gracefully
            mockMvc.perform(get("/metrics/devices/{deviceId}", "error-device"))
                .andExpect(status().isInternalServerError())
                .andDo(print());
        }
    }
}