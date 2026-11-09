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
generated and written to a file under `secrets/` (override with `SECRETS_DIR`) instead
of the startup log — the log just tells you the file's path. That file is gitignored
and meant to be deleted once you've copied the password out.

If you're upgrading an instance that was already running before this change, your
admin/platform-manager account may still be using the old hardcoded default - setting
`ADMIN_PASSWORD`/`PLATFORM_MANAGER_PASSWORD` now won't change it, since those are only
read the first time that account is created. Startup logs a loud `SECURITY WARNING` if
your account is still on the old default; rotate its password directly in the database
(or via `/forgot-password`, if that's open to this role) if you see it.

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
Every demo account shares the password `DemoAccount!2026` (e.g. `demo.customer01@demo.example.test`).

It's safe to run more than once, including with a bigger `count` than last time: each
account is checked (and skipped) individually, so re-running only fills in whatever's
missing instead of silently doing nothing. A failed import is logged and does not stop
the application from starting.

### Demo before/after photos

The demo import creates accounts only. To also give it portfolio projects with before/after
photos (IDR-07, IDR-09, IDR-14, CUS-04, and an auto-flagged copy for PM-04), run this with
the app up and demo data imported (needs Python 3; Pillow only to regenerate the images):

```
python scripts/demo-photos/seed_projects.py            # uploads demo-photos/*.png via the real form
python scripts/demo-photos/generate_photos.py          # optional: re-render demo-photos/*.png
```

It adds three projects for `demo.designer01` and one for the next active demo designer that
reuses a designer01 photo, so it shows up under `/platform-manager/flagged-projects`.
Re-running skips projects that already exist.

## Tests

```
mvn test
```

Tests use an in-memory H2 database, so they don't need MySQL running.
