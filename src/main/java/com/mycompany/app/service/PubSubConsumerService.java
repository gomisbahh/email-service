package com.mycompany.app.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.FieldMask;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.mycompany.app.model.EmailMessage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class PubSubConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(PubSubConsumerService.class);

    private final String projectId;
    private final String subscriptionId;
    private final String dltTopicId;
    private final int maxDeliveryAttempts;
    private final Long maxOutstandingMessages;

    private final boolean autoStart;
    private final EmailService applicationIntegrationService;
    private final EmailServiceChecker emailChecker;
    private volatile Subscriber subscriber;
    private final Gson gson = new Gson();

    public PubSubConsumerService(
            @Value("${gcp.project-id}") String projectId,
            @Value("${gcp.pubsub.email-subscription-id}") String subscriptionId,
            @Value("${gcp.pubsub.email-dlt-topic-id}") String dltTopicId,
            @Value("${gcp.pubsub.consumer.max-delivery-attempts:5}") int maxDeliveryAttempts,
            @Value("${gcp.pubsub.consumer.flow-control.max-messages:#{null}}") Long maxOutstandingMessages,
            EmailService applicationIntegrationService,
            @Value("${gcp.pubsub.consumer.auto-start:true}") boolean autoStart,
            EmailServiceChecker emailChecker) {
        this.projectId = projectId;
        this.subscriptionId = subscriptionId;
        this.dltTopicId = dltTopicId;
        this.maxOutstandingMessages = maxOutstandingMessages;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.applicationIntegrationService = applicationIntegrationService;
        this.autoStart = autoStart;
        this.emailChecker = emailChecker;
    }

    @PostConstruct
    public void init() {
        if (autoStart) {
            start();
        } else {
            logger.info("Pub/Sub consumer is not started automatically. Use the API to start it.");
        }
    }

    public synchronized String start() {
        if (isRunning()) {
            String message = "Consumer is already running.";
            logger.warn(message);
            return message;
        }

        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            String jsonMessage = message.getData().toStringUtf8();
            logger.info("Received Pub/Sub message with ID: {}", message.getMessageId());
            try {
                if (emailChecker.isMailServiceHealthy()) {
                    logger.info("Mail service is UP. Processing email request from Pub/Sub message.");
                    EmailMessage emailMessage = gson.fromJson(jsonMessage, EmailMessage.class);
                    logger.info("Successfully parsed email message for: {}", emailMessage.getTo());
                    applicationIntegrationService.sendEmail(emailMessage);
                    consumer.ack();
                    logger.info("Message {} processed and acknowledged.", message.getMessageId());
                } else {
                    // Mail service is down.
                    // Let the message be redelivered by Pub/Sub.
                    // After maxDeliveryAttempts, it will be sent to the DLT.
                    logger.warn("Mail service is DOWN. nack message {} to allow redelivery or DLT processing.", message.getMessageId());
                    consumer.nack();
     
                }

            } catch (JsonSyntaxException e) {
                // This is a non-recoverable error for this message.
                // Acknowledge the message to prevent it from being redelivered and flooding the DLT.
                // Log it for manual inspection.
                // if schema is provided on Pub/Sub topic, this code will not happen as the publisher will reject malformed messages
                logger.error("Error parsing message {}. The message is malformed and will be acknowledged to prevent retries.", message.getMessageId(), e);
                consumer.ack();
            } catch (MailException e) {
                // A transient error sending email. nack message to allow redelivery up to max retries
                logger.warn("Failed to send email for message {}. nack message to allow redelivery.", message.getMessageId(), e);
                consumer.nack();
            } catch (Exception e) {
                logger.error("Unexpected error processing message {}. Letting message expire to retry, will be sent to DLT after max retries.", message.getMessageId(), e);
                consumer.nack();
            }
        };

        try {
            if (dltTopicId != null && !dltTopicId.isBlank()) {
                updateSubscriptionWithDeadLetterPolicy(subscriptionName);
            }
        } catch (IOException e) {
            logger.error("Failed to update subscription with dead-letter policy. The consumer will start without it.", e);
            // Depending on requirements, you might want to prevent startup here.
            // For now, we'll just log the error and continue.
        }

        Subscriber.Builder subscriberBuilder = Subscriber.newBuilder(subscriptionName, receiver);

        if (maxOutstandingMessages != null && maxOutstandingMessages > 0) {
            FlowControlSettings flowControlSettings = FlowControlSettings.newBuilder()
                    .setMaxOutstandingElementCount(maxOutstandingMessages)
                    .build();
            subscriberBuilder.setFlowControlSettings(flowControlSettings);
            logger.info("Applying flow control with max outstanding messages: {}", maxOutstandingMessages);
        }

        subscriber = subscriberBuilder.build();
        subscriber.startAsync().awaitRunning();
        logger.info("Pub/Sub consumer started and listening on subscription: {}", subscriptionName);
        return "Consumer started successfully.";
    }

    private void updateSubscriptionWithDeadLetterPolicy(ProjectSubscriptionName subscriptionName) throws IOException {
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            ProjectTopicName deadLetterTopicName = ProjectTopicName.of(projectId, dltTopicId);
            DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.newBuilder().setDeadLetterTopic(deadLetterTopicName.toString()).setMaxDeliveryAttempts(maxDeliveryAttempts).build();
            Subscription subscription = Subscription.newBuilder().setName(subscriptionName.toString()).setDeadLetterPolicy(deadLetterPolicy).build();
            FieldMask updateMask = FieldMask.newBuilder().addPaths("dead_letter_policy").build();
            subscriptionAdminClient.updateSubscription(subscription, updateMask);
            logger.info("Successfully updated subscription {} with dead-letter policy pointing to topic {}.", subscriptionName.getSubscription(), deadLetterTopicName.getTopic());
        }
    }

    public synchronized String stop() {
        if (!isRunning()) {
            String message = "Consumer is not running.";
            logger.warn(message);
            return message;
        }
        try {
            subscriber.stopAsync().awaitTerminated(1, TimeUnit.MINUTES);
            logger.info("Pub/Sub consumer shut down.");
            return "Consumer stopped successfully.";
        } catch (TimeoutException e) {
            logger.warn("Timeout waiting for Pub/Sub consumer to shut down.", e);
            return "Failed to stop consumer gracefully due to a timeout.";
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
