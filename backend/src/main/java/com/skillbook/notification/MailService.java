package com.skillbook.notification;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Best-effort email channel. Disabled by default ({@code mail.enabled=false});
 * when enabled, every failure is swallowed after a warning so an SMTP outage
 * can never roll back a booking, payment, or cancellation.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;

    public MailService(JavaMailSender mailSender, @Value("${mail.enabled:false}") boolean enabled) {
        this.mailSender = mailSender;
        this.enabled = enabled;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.debug("Email disabled; skipping [{}] to {}", subject, to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(body, StandardCharsets.UTF_8.name());
            mailSender.send(message);
            log.debug("Sent email [{}] to {}", subject, to);
        } catch (Exception e) {
            log.warn("Failed to send email [{}] to {}: {}", subject, to, e.getMessage());
        }
    }
}
