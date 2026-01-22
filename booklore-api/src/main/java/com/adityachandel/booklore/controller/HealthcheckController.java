package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.HealthcheckResponse;
import com.adityachandel.booklore.model.dto.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/healthcheck")
@Tag(name = "Healthcheck", description = "Endpoints for checking the healch of the application")
public class HealthcheckController {

  @Value("${app.version}")
  private String appVersion;

  @Operation(summary = "Get a ping response", description = "Check if the application is responding")
  @ApiResponse(responseCode = "200", description = "Health status returned successfully")
  @GetMapping
  public ResponseEntity<SuccessResponse<HealthcheckResponse>> getPing() {

    HealthcheckResponse healthData = HealthcheckResponse.builder()
            .status("UP")
            .message("Application is running smoothly.")
            .version(appVersion) // ex) 'development' Insert
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.ok(new SuccessResponse<>(200, "Pong", healthData));
  }
}