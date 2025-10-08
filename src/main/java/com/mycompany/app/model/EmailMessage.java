package com.mycompany.app.model;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Represents an email to be sent.")
public class EmailMessage {

    @Schema(description = "Recipient's email address.", example = "recipient@example.com")
    @NotEmpty(message = "Recipient email address cannot be empty.")
    @Email(message = "Recipient must be a valid email address.")
    private String to;

    @Schema(description = "Subject of the email.", example = "Hello from the application!")
    @NotEmpty(message = "Email subject cannot be empty.")
    private String subject;

    @Schema(description = "Body content of the email.", example = "This is the body of the email.")
    @NotEmpty(message = "Email body cannot be empty.")
    private String body;

    public EmailMessage() {
    }

    public EmailMessage(String to, String subject, String body) {
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}