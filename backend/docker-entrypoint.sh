#!/bin/sh
set -eu

# Database URL validation runs in BolPayApplication before Spring starts.
# Do not translate credential-bearing DATABASE_URL values here: hosted config
# uses the standard SPRING_DATASOURCE_* variables with credentials kept apart.
exec java -jar /app/app.jar
