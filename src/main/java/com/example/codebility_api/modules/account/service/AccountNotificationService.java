package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.shared.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Account emails. Links point at the calling frontend when its Origin is in
 * the app.url allowlist, otherwise at the first configured app URL.
 */
@Service
public class AccountNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AccountNotificationService.class);

    private final EmailService emailService;
    private final List<String> appUrls;
    private final String appName;

    public AccountNotificationService(
            EmailService emailService,
            @Value("${app.url}") String appUrls,
            @Value("${app.name:Codebility}") String appName) {
        this.emailService = emailService;
        this.appUrls = Arrays.stream(appUrls.split(","))
                .map(String::trim)
                .map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url)
                .filter(url -> !url.isEmpty())
                .toList();
        this.appName = appName;
        if (this.appUrls.isEmpty()) {
            throw new IllegalStateException("app.url must list at least one frontend URL");
        }
    }

    public void sendVerificationEmail(String email, String token, String origin) {
        String url = link(origin, "/account/verify-email", token);
        emailService.sendHtml(email, appName + " - Verify Email",
                "<h4>Verify Email</h4><p>Thanks for registering!</p>"
                        + "<p>Please click the link below to verify your email address:</p>"
                        + anchor(url));
    }

    public void sendAlreadyRegisteredEmail(String email, String origin) {
        String url = link(origin, "/account/forgot-password", null);
        emailService.sendHtml(email, appName + " - Email Already Registered",
                "<h4>Email Already Registered</h4><p>Your email <strong>" + HtmlUtils.htmlEscape(email)
                        + "</strong> is already registered.</p>"
                        + "<p>If you don't know your password please visit the forgot password page:</p>"
                        + anchor(url));
    }

    public void sendPasswordResetEmail(String email, String token, String origin) {
        String url = link(origin, "/account/reset-password", token);
        emailService.sendHtml(email, appName + " - Reset Password",
                "<h4>Reset Password Email</h4>"
                        + "<p>Please click the link below to reset your password. The link expires soon"
                        + " and can only be used once.</p>"
                        + anchor(url)
                        + "<p>If you did not request this reset, you can ignore this email.</p>");
    }

    public void sendPasswordSetupEmail(String email, String token, String origin) {
        String url = link(origin, "/account/reset-password", token);
        emailService.sendHtml(email, appName + " - Set Password",
                "<h4>Set Your Password</h4><p>An account has been created for you.</p>"
                        + "<p>Please click the link below to set your password:</p>"
                        + anchor(url));
    }

    private String link(String origin, String path, String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(resolveBaseUrl(origin)).path(path);
        if (token != null) {
            builder.queryParam("token", token);
        }
        return builder.build().encode().toUriString();
    }

    private String resolveBaseUrl(String origin) {
        if (origin != null && appUrls.contains(origin)) {
            return origin;
        }
        if (origin != null && !origin.isBlank()) {
            log.warn("Origin {} is not in app.url; using {} for email links", origin, appUrls.get(0));
        }
        return appUrls.get(0);
    }

    private static String anchor(String url) {
        String escaped = HtmlUtils.htmlEscape(url);
        return "<p><a href=\"" + escaped + "\">" + escaped + "</a></p>";
    }
}
