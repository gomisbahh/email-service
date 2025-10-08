package com.mycompany.app.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class PubSubDltConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(PubSubDltConsumerService.class);

    private final String projectId;
    private final String dltSubscriptionId;
    private final boolean autoStart;
    private volatile Subscriber subscriber;

    public PubSubDltConsumerService(
            @Value("${gcp.project-id}") String projectId,
            @Value("${gcp.pubsub.email-dlt-subscription-id}") String dltSubscriptionId,
            @Value("${gcp.pubsub.dlt-consumer.auto-start:false}") boolean autoStart) {
        this.projectId = projectId;
        this.dltSubscriptionId = dltSubscriptionId;
        this.autoStart = autoStart;
    }

    @PostConstruct
    public void init() {
        if (autoStart) {
            start();
        } else {
            logger.info("DLT Pub/Sub consumer is not started automatically. Use the API to start it.");
        }
    }

    public synchronized String start() {
        if (isRunning()) {
            String message = "DLT Consumer is already running.";
            logger.warn(message);
            return message;
        }

        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, dltSubscriptionId);
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            logger.warn("Received dead-letter message with ID: {}. Content: {}", message.getMessageId(), message.getData().toStringUtf8());
            // Acknowledge the message to remove it from the DLT subscription.
            // This prevents it from being redelivered. It is now logged for manual review.
            consumer.ack();
        };

        subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        subscriber.startAsync().awaitRunning();
        logger.info("DLT Pub/Sub consumer started and listening on subscription: {}", subscriptionName);
        return "DLT Consumer started successfully.";
    }

    public synchronized String stop() {
        if (!isRunning()) {
            String message = "DLT Consumer is not running.";
            logger.warn(message);
            return message;
        }
        try {
            subscriber.stopAsync().awaitTerminated(1, TimeUnit.MINUTES);
            logger.info("DLT Pub/Sub consumer shut down.");
            return "DLT Consumer stopped successfully.";
        } catch (TimeoutException e) {
            logger.warn("Timeout waiting for DLT Pub/Sub consumer to shut down.", e);
            return "Failed to stop DLT consumer gracefully due to a timeout.";
        }
    }

    public boolean isRunning() {
        return subscriber != null && subscriber.isRunning();
    }

    @PreDestroy
    public void tearDown() {
        if (isRunning()) {
            stop();
        }
    }
}