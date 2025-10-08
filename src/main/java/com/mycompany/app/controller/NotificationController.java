package com.mycompany.app.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycompany.app.model.EmailMessage;
import com.mycompany.app.service.PubSubConsumerService;
import com.mycompany.app.service.PubSubDltConsumerService;
import com.mycompany.app.service.PubSubPublisherService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "API for sending and processing notifications / emails")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private final PubSubPublisherService publisherService;
    private final PubSubConsumerService consumerService;
    private final PubSubDltConsumerService dltConsumerService;

    public NotificationController(PubSubPublisherService publisherService, PubSubConsumerService consumerService, PubSubDltConsumerService dltConsumerService) {
        this.publisherService = publisherService;
        this.consumerService = consumerService;
        this.dltConsumerService = dltConsumerService;
    }

    @PostMapping("/publish")
    @Operation(summary = "publish email message to messaging service (Google Pub/Sub)",
            description = "Accepts an email JSON object and publishes it to a Google Cloud Pub/Sub topic for asynchronous processing.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Email queued successfully. The message ID is returned.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"messageId\": \"123456789\"}"))),
                    @ApiResponse(responseCode = "500", description = "Internal server error while trying to queue the email.")
            })
    public ResponseEntity<Map<String, String>> queueEmailForSending(@Valid @RequestBody EmailMessage emailMessage) {
        try {
            String messageId = publisherService.publishEmailMessage(emailMessage);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("messageId", messageId));
        } catch (Exception e) {
            logger.error("Failed to publish email message to messaging service", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to queue email for sending."));
        }
    }

    @PostMapping("/consumer/start")
    @Operation(summary = "Starts the email consumer",
            description = "Manually starts the background service that listens for and processes email messages from the Pub/Sub subscription. Does nothing if the consumer is already running.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provides the status of the start operation.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"Consumer started successfully.\"}")))
            })
    public ResponseEntity<Map<String, String>> startConsumer() {
        String result = consumerService.start();
        return ResponseEntity.ok(Map.of("status", result));
    }

    @PostMapping("/consumer/stop")
    @Operation(summary = "Stops the email consumer",
            description = "Manually stops the background service that listens for email messages. Does nothing if the consumer is already stopped.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provides the status of the stop operation.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"Consumer stopped successfully.\"}")))
            })
    public ResponseEntity<Map<String, String>> stopConsumer() {
        String result = consumerService.stop();
        return ResponseEntity.ok(Map.of("status", result));
    }

    @GetMapping("/consumer/status")
    @Operation(summary = "Gets the status of the email consumer",
            description = "Returns the current running status of the email consumer service.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current status of the consumer.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"running\"}")))
            })
    public ResponseEntity<Map<String, String>> getConsumerStatus() {
        boolean isRunning = consumerService.isRunning();
        return ResponseEntity.ok(Map.of("status", isRunning ? "running" : "stopped"));
    }

    @PostMapping("/dlt-consumer/start")
    @Operation(summary = "Starts the dead-letter email consumer",
            description = "Manually starts the background service that listens for and processes messages from the dead-letter subscription.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provides the status of the start operation.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"DLT Consumer started successfully.\"}")))
            })
    public ResponseEntity<Map<String, String>> startDltConsumer() {
        String result = dltConsumerService.start();
        return ResponseEntity.ok(Map.of("status", result));
    }

    @PostMapping("/dlt-consumer/stop")
    @Operation(summary = "Stops the dead-letter email consumer",
            description = "Manually stops the background service that listens for dead-letter messages.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provides the status of the stop operation.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"DLT Consumer stopped successfully.\"}")))
            })
    public ResponseEntity<Map<String, String>> stopDltConsumer() {
        String result = dltConsumerService.stop();
        return ResponseEntity.ok(Map.of("status", result));
    }

    @GetMapping("/dlt-consumer/status")
    @Operation(summary = "Gets the status of the dead-letter email consumer",
            description = "Returns the current running status of the dead-letter email consumer service.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current status of the DLT consumer.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\": \"running\"}")))
            })
    public ResponseEntity<Map<String, String>> getDltConsumerStatus() {
        boolean isRunning = dltConsumerService.isRunning();
        return ResponseEntity.ok(Map.of("status", isRunning ? "running" : "stopped"));
    }
}
