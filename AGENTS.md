# AGENTS.md

## Project overview

This is a demo application for experimenting with FireTMS API data, Vaadin Grids, charts, and AI-assisted UI generation.

The application should initially be simple and local-first:
- Java 25
- Spring Boot
- Vaadin 25.2
- Maven
- H2 database in the `dev` profile
- No user login
- No Spring Security
- No tenant context yet

The first supported FireTMS endpoint is:

`/invoices/sales/issued`

The user should only control the date/time range used to fetch data from the FireTMS API.

## Development rules

- Prefer small, focused changes.
- Do not add PostgreSQL yet.
- Do not add Spring Security yet.
- Do not add multi-tenancy yet.
- Keep FireTMS API credentials out of Git.
- Use environment variables for secrets.
- Use `FIRETMS_API_KEY` for the FireTMS API key.
- Use H2 for the `dev` profile.
- Prefer Flyway migrations over Hibernate auto-DDL.
- Prefer Spring `RestClient` for synchronous HTTP calls.
- Store imported API records in dedicated database tables.
- Keep the raw API response JSON for imported records when practical.
- Add or update tests for business logic.
- Use JUnit 5.

## Validation

Before considering a task complete, run:

```bash
mvn clean test
