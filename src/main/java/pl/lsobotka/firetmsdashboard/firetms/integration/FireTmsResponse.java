package pl.lsobotka.firetmsdashboard.firetms.integration;

import com.fasterxml.jackson.databind.JsonNode;

public record FireTmsResponse(String rawJson, JsonNode payload) {
}
