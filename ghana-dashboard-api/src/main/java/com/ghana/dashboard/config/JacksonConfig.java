package com.ghana.dashboard.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
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
    public Jackson2ObjectMapperBuilderCustomizer jsr310Customizer() {
        return builder -> builder.deserializers(new LenientLocalDateTimeDeserializer());
    }

    public static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final DateTimeFormatter LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        private static final DateTimeFormatter LOCAL_NOSEC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
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
                throw new JsonMappingException(p, "Cannot parse '" + value + "' as a LocalDateTime", e);
            }
        }
    }
}