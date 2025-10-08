package com.mycompany.app.service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.mycompany.app.model.EmailMessage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class PubSubPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(PubSubPublisherService.class);

    private final String topicId;
    private final String projectId;
    private Publisher publisher;
    private final Gson gson = new Gson();

    public PubSubPublisherService(
            @Value("${gcp.project-id}") String projectId,
            @Value("${gcp.pubsub.email-topic-id}") String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    @PostConstruct
    public void init() throws IOException {
        TopicName topicName = TopicName.of(projectId, topicId);
        publisher = Publisher.newBuilder(topicName).build();
        logger.info("Pub/Sub publisher initialized for topic: {}", topicName);
    }

    public String publishEmailMessage(EmailMessage emailMessage) throws ExecutionException, InterruptedException {
        String messageJson = gson.toJson(emailMessage);
        ByteString data = ByteString.copyFromUtf8(messageJson);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
        String messageId = messageIdFuture.get();
        logger.info("Published message with ID: {}", messageId);
        return messageId;
    }

    @PreDestroy
    public void tearDown() {
        if (publisher != null) {
            try {
                publisher.shutdown();
                publisher.awaitTermination(1, TimeUnit.MINUTES);
                logger.info("Pub/Sub publisher  shut down.");
            } catch (Exception e) {
                logger.error("Error shutting down Pub/Sub publisher", e);
            }
        }
    }
}