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

The FireTMS API key is provided by the user through the UI, not through local environment configuration.

## Development rules

- Prefer small, focused changes.
- Do not add PostgreSQL yet.
- Do not add Spring Security yet.
- Do not add multi-tenancy yet.
- Keep FireTMS API credentials out of Git.
- Do not require a FireTMS API key in environment variables for the initial demo.
- The FireTMS API key should be entered by the user in the Vaadin UI.
- For the initial demo, the API key may be kept only in memory for the current application session.
- Do not persist the FireTMS API key unless explicitly requested later.
- Do not log the FireTMS API key.
- Do not expose the FireTMS API key in error messages.
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
