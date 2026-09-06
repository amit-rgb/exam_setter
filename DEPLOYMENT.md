# Production deployment

The production target is a single Oracle Cloud VM running Docker Compose:

- Spring Boot application
- PostgreSQL 16 with pgvector
- Caddy for HTTPS
- persistent Docker volumes for PostgreSQL and Caddy

## 1. Prepare secrets

Copy the example environment file:

    cp .env.example .env

Replace every CHANGE_ME value. Never commit .env.

Required values:

- POSTGRES_DB
- POSTGRES_USER
- POSTGRES_PASSWORD
- OPENAI_API_KEY
- APP_AUTH_USERNAME
- APP_AUTH_PASSWORD
- APP_DOMAIN

## 2. Start the private application stack

    docker compose up -d --build

The application is bound to 127.0.0.1:8080 and PostgreSQL is not published to the host.

## 3. Enable public HTTPS

Point the DNS A record for APP_DOMAIN at the VM public IP, then run:

    docker compose --profile public up -d

Caddy obtains and renews the TLS certificate automatically when the domain resolves to the VM and ports 80/443 are reachable.

## 4. Verify

    docker compose ps
    docker compose logs --tail=200 app
    docker compose logs --tail=100 caddy

Open:

    https://APP_DOMAIN

The application requires the APP_AUTH_USERNAME / APP_AUTH_PASSWORD credentials.

## 5. Database backup

Before any production migration, take a PostgreSQL dump. Keep backups outside the Docker volume.

Example:

    docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > exam-bank-backup.dump

Do not run docker compose down -v in production; that deletes the PostgreSQL volume.

## Security notes

- PostgreSQL is internal to the Docker network.
- The Spring Boot port is bound to localhost only.
- AI generation and PDF ingestion are authenticated and rate-limited.
- OpenAI and database credentials are supplied through environment variables.
- Startup AI verification is disabled in production.
