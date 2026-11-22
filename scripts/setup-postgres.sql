-- Script idempotente para preparar el rol y las bases locales de Colibrí.
-- Ejecutar como superusuario (postgres), puerto 5434 en esta máquina.
-- Ejemplo:
--   "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -p 5434 -f scripts\setup-postgres.sql

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'colibri') THEN
    CREATE ROLE colibri LOGIN PASSWORD 'colibri' NOSUPERUSER NOCREATEDB;
  END IF;
END
$$;

SELECT 'CREATE DATABASE colibri'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'colibri')\gexec

SELECT 'CREATE DATABASE colibri_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'colibri_test')\gexec

GRANT ALL PRIVILEGES ON DATABASE colibri TO colibri;
GRANT ALL PRIVILEGES ON DATABASE colibri_test TO colibri;
