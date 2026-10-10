
package com.example.codebility_api.service;

import org.eclipse.angus.mail.smtp.SMTPTransport;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class EmailService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String email, String token) {
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", String.valueOf(port));
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(properties);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(username);
            message.setRecipients(
                    Message.RecipientType.TO, email);
            message.setSubject(
                    "Verify your Codebility account");
            message.setText(
                    "Welcome to Codebility!\n\n"
                            + "Your verification token is:\n\n"
                            + token + "\n\n"
                            + "Submit this token to POST /accounts/verify-email."
            );
            message.saveChanges();

            try (SMTPTransport transport =
                         (SMTPTransport) session.getTransport("smtp")) {

                transport.connect(host, port, username, password);

                transport.sendMessage(
                        message,
                        message.getAllRecipients()
                );

                String response =
                        transport.getLastServerResponse();

                log.info(
                        "Verification email sent to {}",
                        email
                );
                log.info("SMTP response: {}", response);

                String messageId = extractMessageId(response);

                if (messageId != null) {
                    log.info(
                            "Ethereal preview URL: "
                                    + "https://ethereal.email/message/{}",
                            messageId
                    );
                } else {
                    log.warn(
                            "Ethereal preview URL could not be extracted "
                                    + "from the SMTP response."
                    );
                }
            }

        } catch (Exception ex) {
            log.error(
                    "Failed to send verification email to {}",
                    email,
                    ex
            );

            throw new IllegalStateException(
                    "Failed to send verification email", ex);
        }
    }

    private String extractMessageId(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile(
                "\\bMSGID=([^\\]\\s]+)"
        ).matcher(response);

        return matcher.find()
                ? matcher.group(1).trim()
                : null;
    }


    public void sendPasswordResetEmail(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Reset your Codebility password");
        message.setText(
                "Hello,\n\n"
                        + "We received a request to reset your Codebility password.\n\n"
                        + "Your password-reset token is:\n\n"
                        + token + "\n\n"
                        + "This token expires in 15 minutes and can only be used once.\n\n"
                        + "If you did not request this reset, you can ignore this email."
        );

        mailSender.send(message);

        log.info("Password-reset email sent to {}", email);
    }

}
