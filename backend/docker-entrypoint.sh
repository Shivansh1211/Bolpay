#!/bin/sh
set -eu

# Render injects PostgreSQL's private URL as postgresql://. Spring's JDBC driver
# expects jdbc:postgresql://; avoid printing the credential-bearing value.
if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
  case "$DATABASE_URL" in
    postgresql://*) export SPRING_DATASOURCE_URL="jdbc:${DATABASE_URL}" ;;
    postgres://*) export SPRING_DATASOURCE_URL="jdbc:postgresql://${DATABASE_URL#postgres://}" ;;
    jdbc:postgresql://*) export SPRING_DATASOURCE_URL="$DATABASE_URL" ;;
    *) echo "DATABASE_URL has an unsupported PostgreSQL URL scheme" >&2; exit 1 ;;
  esac
fi

exec java -jar /app/app.jar
