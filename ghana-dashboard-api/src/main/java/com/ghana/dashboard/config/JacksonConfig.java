package com.ghana.dashboard.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.util.AccessPattern;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The SPA sends timestamps in several shapes: ISO with offset (new Date().toISOString()),
 * datetime-local values without seconds, and plain dates. Deserialise them all instead of
 * failing with a 400/500 on the "Press start" input.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jsr310Customizer() {
        return builder -> builder.addModule(
                new SimpleModule().addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer()));
    }

    public static class LenientLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final DateTimeFormatter LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        private static final DateTimeFormatter LOCAL_NOSEC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String text = p.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            try {
                return LocalDateTime.parse(value, LOCAL);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, LOCAL_NOSEC);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, SPACE);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(value, DATE).atStartOfDay();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Cannot parse '" + value + "' as a LocalDateTime");
            }
        }

        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            return null;
        }

        @Override
        public AccessPattern getNullAccessPattern() {
            return AccessPattern.DYNAMIC;
        }
    }
}