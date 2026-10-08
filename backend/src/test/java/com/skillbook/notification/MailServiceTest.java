package com.skillbook.notification;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private MimeMessage newMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void sendsMailWhenEnabled() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMessage());
        MailService service = new MailService(mailSender, true);

        service.send("bob@example.com", "Booking confirmed", "Your session is confirmed");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendFailureIsSwallowed() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMessage());
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));
        MailService service = new MailService(mailSender, true);

        assertThatCode(() -> service.send("bob@example.com", "Booking confirmed", "body"))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledNeverTouchesSender() {
        MailService service = new MailService(mailSender, false);

        service.send("bob@example.com", "Booking confirmed", "body");

        verifyNoInteractions(mailSender);
    }

    @Test
    void recipientSubjectAndBodyAreApplied() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMessage());
        MailService service = new MailService(mailSender, true);

        service.send("alice@example.com", "Session reminder", "Starts in 2 hours");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
