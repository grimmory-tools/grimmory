package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/healthcheck")
@Tag(name = "Healthcheck", description = "Endpoints for checking the healch of the application")
public class HealthcheckController {

  @Operation(summary = "Get a ping response", description = "Check if the application is responding to requests")
  @ApiResponse(responseCode = "200", description = "Health status returned successfully")
  @GetMapping
  public ResponseEntity<?> getPing() {
    return ResponseEntity.ok(new SuccessResponse<>(200, "Pong"));
  }
}