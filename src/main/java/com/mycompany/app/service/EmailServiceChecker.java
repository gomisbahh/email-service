package com.mycompany.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceChecker {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceChecker.class);
    private final HealthEndpoint healthEndpoint;

    public EmailServiceChecker(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public boolean isMailServiceHealthy() {
        try {
            // Query the health endpoint for the 'mail' component
            HealthComponent health = healthEndpoint.healthForPath("mail");

            // If the component doesn't exist or is null, treat as unhealthy
            if (health == null) {
                logger.warn("Mail health component not found. Assuming service is down.");
                return false;
            }
            // Check if the status is UP
            return health.getStatus().equals(Status.UP);
        } catch (Exception e) {
            logger.error("Error fetching mail health status. Assuming service is down.", e.getMessage());
            return false;
        }
    }
}