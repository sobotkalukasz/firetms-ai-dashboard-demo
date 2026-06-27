# FireTMS AI Dashboard Demo

Demo application for experimenting with FireTMS API data, Vaadin Grids, charts, and AI-assisted UI generation.

## Initial goals

- Java 25
- Spring Boot
- Vaadin 25.2
- H2 database for local development
- FireTMS API integration
- FireTMS API key entered by the user in the UI
- First endpoint: `/invoices/sales/issued`
- Manual synchronization by selected date range
- Simple Vaadin Grid view
- No authentication or multi-tenancy in the first version

## Local development

The app defaults to the `dev` Spring profile. Run it locally with:

```bash
./mvnw spring-boot:run
```

The `dev` profile uses a file-based H2 database at `./data/firetms-dashboard-db`.
The H2 console is available at `http://localhost:8080/h2-console` with JDBC URL
`jdbc:h2:file:./data/firetms-dashboard-db`, user `sa`, and a blank password.

## FireTMS API spike

The first FireTMS integration spike is available at `/firetms/sales-invoices`.
It lets you enter a FireTMS API key for the current session, choose an issue-date
range, call `/invoices/sales/issued`, and inspect a short technical JSON preview.
