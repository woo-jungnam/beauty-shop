-- Keep the demo administrator email generic in this sanitized snapshot.
UPDATE users SET email = 'admin@example.invalid' WHERE username = 'admin';
