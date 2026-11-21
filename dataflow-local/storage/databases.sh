#!/bin/sh
set -eu

for database in $DATABASES; do
  psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'" | grep -q 1 || psql -c "CREATE DATABASE $database"
  echo "Database $database ready"
done
