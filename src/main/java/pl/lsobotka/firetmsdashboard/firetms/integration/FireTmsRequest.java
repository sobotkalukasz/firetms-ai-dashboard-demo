package pl.lsobotka.firetmsdashboard.firetms.integration;

import java.util.Map;

public record FireTmsRequest(String path, Map<String, ?> queryParams) {
}
