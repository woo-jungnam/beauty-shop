package com.core.beautyshop.modules.spa.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Matches Hibernate's hibernate.jdbc.time_zone=UTC, including its legacy JDBC TIME binding. */
public final class SpaJdbcSupport {
    private SpaJdbcSupport() {}
    public static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }

    public static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Instant instant) statement.setTimestamp(index, Timestamp.from(instant), utc());
        else if (value instanceof Timestamp timestamp) statement.setTimestamp(index, timestamp, utc());
        else if (value instanceof LocalTime time) statement.setTime(index, Time.valueOf(time), utc());
        else if (value instanceof Time time) statement.setTime(index, time, utc());
        else statement.setObject(index, value);
    }

    public static int update(JdbcTemplate jdbc, String sql, Object... values) {
        return jdbc.update(sql, statement -> {
            for (int index = 0; index < values.length; index++) bind(statement, index + 1, values[index]);
        });
    }

    public static <T> List<T> query(JdbcTemplate jdbc, String sql, RowMapper<T> mapper, Object... values) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            for (int index = 0; index < values.length; index++) bind(statement, index + 1, values[index]);
            return statement;
        }, mapper);
    }

    public static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column, utc());
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static Instant instant(ResultSet result, int column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column, utc());
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static LocalTime time(ResultSet result, String column) throws SQLException {
        Time time = result.getTime(column, utc());
        return time == null ? null : time.toLocalTime();
    }
}
