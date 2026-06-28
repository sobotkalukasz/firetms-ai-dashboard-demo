# FireTMS AI Dashboard Demo

Demo application for experimenting with FireTMS API data, Vaadin Grids, charts, and AI-assisted UI generation.

## Initial goals

- Java 25
- Spring Boot
- Vaadin 25.2
- H2 database for local development
- FireTMS API integration
- FireTMS API key entered by the user in the UI
- OpenAI API key entered by the user in the UI
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
The FireTMS API key is kept only in UI/session memory for the current interaction
and is not persisted.

The main dashboard is available at `/dashboard` and is also the default landing
page. It currently includes basic sales invoice analytics from persisted
`sales_invoice` records.

An experimental AI dashboard is available at `/ai-dashboard`. It lets you enter
an OpenAI API key in the UI for the current session and uses a restricted
read-only database view named `ai_sales_invoice_view`.

When you click `Generate`, `/ai-dashboard` now makes a real OpenAI API call to
generate SQL and a visualization specification for the restricted AI view.

Only the following are sent to OpenAI:

- the user prompt
- the exact safe schema for `ai_sales_invoice_view`
- SQL generation rules
- the expected JSON output schema/instructions

Invoice row data is not sent to OpenAI. The app does not send `raw_json`,
`ai_sales_invoice_view` contents, or any FireTMS API key to OpenAI. Generated
SQL is parsed from the expected JSON object, validated locally, and only then
executed against the database. The result grid is always shown. When OpenAI
returns a valid visualization spec, the same query result is also rendered as a
Vaadin Chart.

The AI database surface is intentionally limited:

- only `ai_sales_invoice_view` is exposed to AI SQL
- `raw_json` is excluded from the view
- invoice rows are not sent to OpenAI
- generated SQL is validated before execution
- generated SQL remains restricted to `ai_sales_invoice_view`
- the FireTMS API key is never exposed to AI
- the OpenAI API key is used only for AI requests and is never exposed to FireTMS
- only validated `SELECT` queries against `ai_sales_invoice_view` are executed

Secret handling rules for this demo:

- enter the FireTMS API key only in `/firetms/sales-invoices`
- enter the OpenAI API key only in `/ai-dashboard`
- neither key is persisted
- neither key should be committed to Git
- the OpenAI API key is not read from environment variables or application
  configuration

The Vaadin experimental AI feature flag is enabled in
`src/main/resources/vaadin-featureflags.properties`.

The OpenAI model used for classification is configured with the non-secret
property `ai.openai.model` in `src/main/resources/application.yml`. The default
is `gpt-5.5-mini`. If that model is unavailable for your API key, update the
property to another supported Responses API model.
