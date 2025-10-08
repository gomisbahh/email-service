package com.mycompany.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "gcp.pubsub.consumer.stop-when-mailserver-down", havingValue = "true")
public class ConsumerHealthMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerHealthMonitor.class);

    private final PubSubConsumerService consumerService;
    private final EmailServiceChecker emailChecker;

    // This flag tracks if the consumer was stopped by this monitor.
    // It prevents the monitor from starting a consumer that was stopped manually via the API.
    private volatile boolean stoppedByMonitor = false;

    public ConsumerHealthMonitor(PubSubConsumerService consumerService, EmailServiceChecker emailChecker) {
        this.consumerService = consumerService;
        this.emailChecker = emailChecker;
        logger.info("ConsumerHealthMonitor is active. It will automatically manage the Pub/Sub consumer based on mail server health.");
    }

    @Scheduled(fixedDelayString = "${gcp.pubsub.consumer.health-check-delay-ms:30000}")
    public void checkMailServiceAndControlConsumer() {
        boolean isMailHealthy = emailChecker.isMailServiceHealthy();
        boolean isConsumerRunning = consumerService.isRunning();

        if (isMailHealthy) {
            // If mail service is healthy and the consumer was previously stopped by this monitor, restart it.
            if (stoppedByMonitor && !isConsumerRunning) {
                logger.info("Mail service is back online. Restarting Pub/Sub consumer.");
                consumerService.start();
                stoppedByMonitor = false; // Reset the flag
            }
        } else {
            // If mail service is down and the consumer is running, stop it.
            if (isConsumerRunning) {
                logger.warn("Mail service is down. Stopping Pub/Sub consumer to pause message processing.");
                consumerService.stop();
                stoppedByMonitor = true; // Set the flag
            }
        }
    }
}