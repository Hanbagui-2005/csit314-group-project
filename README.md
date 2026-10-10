# csit314-group-project

Spring Boot 3.5 + Thymeleaf + MySQL. Needs JDK 25 and Maven.

## Database setup

The app connects to MySQL at `localhost:3306` as `root` / `root` and creates the
`interior_design_platform` database and tables on first start (`ddl-auto=update`).

No MySQL installed? Start one with Docker:

```
docker compose up -d
```

Different credentials? Set environment variables instead of editing `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## Run

```
mvn spring-boot:run
```

Then open http://localhost:8080/search.

A default User Admin is created on first start: `admin@platform.local` / `Admin@12345`
(override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`). Log in at `/admin/login`.

There's no mail server yet, so password reset links are printed in the app log.

## Tests

```
mvn test
```

Tests use an in-memory H2 database, so they don't need MySQL running.
