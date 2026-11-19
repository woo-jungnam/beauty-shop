ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject);
