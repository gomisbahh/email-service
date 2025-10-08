package com.mycompany.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.mycompany.app.model.EmailMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(EmailMessage emailMessage) throws MailException {
        logger.info("Attempting to send email to: {}", emailMessage.getTo());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailMessage.getTo());
            message.setSubject(emailMessage.getSubject());
            message.setFrom("notification@mycompany.com");
            // Assuming EmailMessage has a getBody() method for the email content.
            message.setText(emailMessage.getBody());

            mailSender.send(message);

            logger.info("Successfully sent email to: {}", emailMessage.getTo());
        } catch (MailException e) {
            logger.error("Failed to send email to: {}", emailMessage.getTo(), e);
            throw e;
        }
    }
}