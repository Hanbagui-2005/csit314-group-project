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

## Logging in

Customers and Interior Designers register themselves (`/register` and `/designer/register`).
User Admin and Platform Management have no public sign-up — a default account for each is
seeded on first start:

| Role | Login page | Default email | Override email via |
|---|---|---|---|
| User Admin | `/admin/login` | `admin@platform.local` | `ADMIN_EMAIL` |
| Platform Management | `/platform-manager/login` | `platform-manager@platform.local` | `PLATFORM_MANAGER_EMAIL` |

There's no fixed default password checked into the repo anymore. Set `ADMIN_PASSWORD` /
`PLATFORM_MANAGER_PASSWORD` yourself, or leave them unset and a random password is
generated and printed to the startup log once (`No ADMIN_PASSWORD set - created default
admin account ... with generated password: ...`) — copy it from there before it scrolls
past.

There's no mail server yet, so password reset links are printed in the app log.

## Demo data

To populate a running instance (pointed at real MySQL, not the test H2 database) with a
realistic set of customers, designers and companies instead of clicking through
registration forms by hand, run:

```
mvn spring-boot:run -Dspring-boot.run.arguments="--app.demo-data.import=true --app.demo-data.count=50"
```

This reuses the same registration services the UI calls (so passwords are hashed the
same way, and data stays consistent), and auto-approves the generated designers so
there's something to browse right away. `count` defaults to 50 and is capped at 2000.
It's safe to run more than once — it skips importing if demo accounts already exist.

## Tests

```
mvn test
```

Tests use an in-memory H2 database, so they don't need MySQL running.
