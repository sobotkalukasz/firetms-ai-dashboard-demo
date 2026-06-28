package pl.lsobotka.firetmsdashboard.ai.query;

import java.util.List;
import java.util.Map;

public record DynamicSqlQueryResult(List<String> columns, List<Map<String, Object>> rows) {
}
