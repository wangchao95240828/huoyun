package com.xqt.saas.health;

import com.xqt.saas.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(healthService.health());
    }
}
