package pl.lsobotka.firetmsdashboard.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiQueryHistoryService {

    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]+");
    private static final Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile("(?i)authorization\\s*[:=]\\s*[^\\s,;]+(?:\\s+[^\\s,;]+)?");
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("(?i)sk-[a-z0-9\\-_]+");

    private final AiQueryHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiQueryHistoryService(AiQueryHistoryRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void save(AiQueryHistoryWriteRequest request, Collection<String> secrets) {
        AiQueryHistoryEntity entity = new AiQueryHistoryEntity();
        entity.setPrompt(sanitize(request.prompt(), secrets));
        entity.setGeneratedSql(sanitize(request.generatedSql(), secrets));
        entity.setVisualization(serializeVisualization(request.visualizationSpec(), secrets));
        entity.setTitle(sanitize(request.title(), secrets));
        entity.setExplanation(sanitize(request.explanation(), secrets));
        entity.setRowCount(request.rowCount());
        entity.setStatus(request.status());
        entity.setSanitizedErrorMessage(sanitize(request.sanitizedErrorMessage(), secrets));
        entity.setOpenAiDurationMs(request.openAiDurationMs());
        entity.setSqlDurationMs(request.sqlDurationMs());
        entity.setCreatedAt(LocalDateTime.now(clock));
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AiQueryHistoryEntry> findRecent() {
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, DEFAULT_RECENT_LIMIT))
                .stream()
                .map(this::mapEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiQueryHistoryEntry getById(Long id) {
        return repository.findById(id)
                .map(this::mapEntry)
                .orElseThrow(() -> new NoSuchElementException("AI query history entry was not found."));
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public String sanitize(String value, Collection<String> secrets) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = value;
        sanitized = redactSecrets(sanitized, secrets);
        sanitized = AUTHORIZATION_HEADER_PATTERN.matcher(sanitized).replaceAll("Authorization: [REDACTED]");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = OPENAI_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED]");
        return sanitized;
    }

    private String redactSecrets(String value, Collection<String> secrets) {
        String sanitized = value;
        if (secrets == null) {
            return sanitized;
        }
        for (String secret : secrets) {
            if (StringUtils.hasText(secret)) {
                sanitized = sanitized.replace(secret, "[REDACTED]");
            }
        }
        return sanitized;
    }

    private String serializeVisualization(AiVisualizationSpec visualizationSpec, Collection<String> secrets) {
        if (visualizationSpec == null) {
            return null;
        }
        try {
            return sanitize(objectMapper.writeValueAsString(visualizationSpec), secrets);
        } catch (JsonProcessingException exception) {
            return sanitize(visualizationSpec.visualization().name(), secrets);
        }
    }

    public AiVisualizationSpec deserializeVisualization(String visualization) {
        if (!StringUtils.hasText(visualization)) {
            return new AiVisualizationSpec(AiVisualizationSpec.VisualizationType.TABLE, null, null, null);
        }
        try {
            return objectMapper.readValue(visualization, AiVisualizationSpec.class);
        } catch (JsonProcessingException exception) {
            try {
                return new AiVisualizationSpec(
                        AiVisualizationSpec.VisualizationType.valueOf(visualization.trim()),
                        null,
                        null,
                        null);
            } catch (IllegalArgumentException invalidVisualization) {
                throw new NoSuchElementException("Stored visualization metadata is invalid.");
            }
        }
    }

    private AiQueryHistoryEntry mapEntry(AiQueryHistoryEntity entity) {
        return new AiQueryHistoryEntry(
                entity.getId(),
                entity.getPrompt(),
                entity.getGeneratedSql(),
                entity.getVisualization(),
                entity.getTitle(),
                entity.getExplanation(),
                entity.getRowCount(),
                entity.getStatus(),
                entity.getSanitizedErrorMessage(),
                entity.getOpenAiDurationMs(),
                entity.getSqlDurationMs(),
                entity.getCreatedAt());
    }
}
