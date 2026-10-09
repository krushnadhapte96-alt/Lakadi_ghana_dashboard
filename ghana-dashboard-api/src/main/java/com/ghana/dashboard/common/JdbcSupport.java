package com.ghana.dashboard.common;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Small helpers shared by the JDBC-backed modules (khata, documents, licences, insights, ...). */
@Component
public class JdbcSupport {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSupport(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /** Inserts a row built from a column->value map and returns the generated id. */
    public long insert(String table, Map<String, Object> values) {
        String columns = String.join(", ", values.keySet());
        String params = String.join(", ", values.keySet().stream().map(k -> ":" + k).toList());
        MapSqlParameterSource source = new MapSqlParameterSource();
        values.forEach(source::addValue);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + params + ")", source, keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key for " + table);
        }
        return key.longValue();
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    public static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    public static String iso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
