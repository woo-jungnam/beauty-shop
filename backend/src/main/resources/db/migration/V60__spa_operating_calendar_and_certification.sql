CREATE TABLE spa_calendar_guard (id BIGINT PRIMARY KEY);
INSERT INTO spa_calendar_guard(id) VALUES (1);
CREATE TABLE spa_weekly_hours (
    day_of_week INTEGER PRIMARY KEY,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL
);
INSERT INTO spa_weekly_hours(day_of_week,closed,opens_at,closes_at) VALUES
(1,FALSE,'08:00:00','20:00:00'),(2,FALSE,'08:00:00','20:00:00'),
(3,FALSE,'08:00:00','20:00:00'),(4,FALSE,'08:00:00','20:00:00'),
(5,FALSE,'08:00:00','20:00:00'),(6,FALSE,'08:00:00','20:00:00'),(7,FALSE,'08:00:00','20:00:00');
CREATE TABLE spa_calendar_exceptions (
    work_date DATE PRIMARY KEY,
    closed BOOLEAN NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    reason VARCHAR(500) NOT NULL,
    updated_by BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_spa_calendar_actor FOREIGN KEY(updated_by) REFERENCES users(id)
);
ALTER TABLE beauty_services ADD COLUMN requires_certification BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE staff_service_skills ADD COLUMN certified_until DATE;
