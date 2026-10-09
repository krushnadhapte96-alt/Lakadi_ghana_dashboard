package com.ghana.dashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Storage storage = new Storage();
    private Crypto crypto = new Crypto();
    private Mail mail = new Mail();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(7);
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> origins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "./uploads";
        private long maxFileSizeMb = 10;
        private List<String> allowedTypes = new ArrayList<>(
                List.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf"));
    }

    @Getter
    @Setter
    public static class Crypto {
        private String aesKey;
    }

    /**
     * Optional SMTP relay for licence reminders. {@code enabled} defaults to false, so
     * the module runs on the log channel until a host is actually configured — the
     * mail beans only exist when {@code app.mail.enabled=true}.
     */
    @Getter
    @Setter
    public static class Mail {
        private boolean enabled = false;
        private String from = "no-reply@ghana-dashboard.local";
        /** Overrides the per-shop owner email; handy for a single test inbox. */
        private String recipientOverride;
    }
}