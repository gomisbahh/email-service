# E-Mail Service

This service is implemented as a Spring Boot application used to send email messages using SMTP client reliably through Google Cloud Pub/Sub in a queue fashion. The application utilizes Spring Cloud GCP libraries to seamlessly integrate with Google Cloud services.

## Overview

The application subscribes to a specified Google Cloud Pub/Sub topic and consumes messages. Each message contains an email, which is then added to a queue for processing.  A separate queue processor handles the emails, ensuring they are sent reliably. The system is designed for scalability and fault tolerance.

## Key Features

*   **Google Cloud Pub/Sub Integration:**  Consumes messages from a Google Cloud Pub/Sub topic.
*   **Email Sending:**  Sends emails using existing corportate SMTP server.


## Prerequisites

*   Java 11 or higher
*   Maven
*   Google Cloud SDK (gcloud) installed and configured
*   A Google Cloud project with Pub/Sub enabled
*   Service account with necessary permissions to subscribe to Pub/Sub topics
*   Email Sending credentials (SMTP server, username, password)

## Getting Started

1.  **Clone the Repository:**
    ```bash
    git clone <repository_url>
    ```

2.  **Configure Application Properties:**
    *   Update `application.properties` or `application.yml` with your Google Cloud project ID, Pub/Sub topic name, and email sending credentials. Refer to the comments in the file for specific configuration options.
    *   Example:
        ```properties
        google.cloud.pubsub.project-id=your-gcp-project-id
        google.cloud.pubsub.topic=your-pubsub-topic
        # ... email sending credentials ...
        ```

3.  **Build and Run the Application:**
    ```bash
    mvn clean install
    mvn spring-boot:run
    ```
## Relevant Articles:

- [Documenting a Spring REST API Using OpenAPI 3.0](https://www.baeldung.com/spring-rest-openapi-documentation) (Useful for understanding OpenAPI specification and documentation)
- [Spring Cloud GCP](https://spring.io/projects/spring-cloud-gcp) (Official Spring Cloud GCP documentation)
- [Google Cloud Pub/Sub](https://cloud.google.com/pubsub) (Google Cloud Pub/Sub documentation)

## Troubleshooting

*   **Pub/Sub Subscription Issues:** Ensure the service account has the `Pub/Sub Subscriber` role on the Pub/Sub topic.
*   **Email Sending Errors:** Verify email credentials and SMTP server settings.
*   **Configuration Errors:** Double-check the configuration properties in `application.properties` or `application.yml`.
```

