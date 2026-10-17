-- Runs once, when the Postgres volume is created for the first time.
-- One database and one user per service: a service's credentials only open its own database.
-- If you add a service later, run `docker compose down -v` to recreate the volume.

-- auth-service
CREATE USER auth_user WITH PASSWORD 'auth_pass';
CREATE DATABASE auth_db OWNER auth_user;
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
