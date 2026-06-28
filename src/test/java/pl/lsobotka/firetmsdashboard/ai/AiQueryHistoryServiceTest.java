package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryHistoryEntry;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryHistoryService;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryHistoryStatus;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryHistoryWriteRequest;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;
import pl.lsobotka.firetmsdashboard.ai.persistence.AiQueryHistoryEntity;
import pl.lsobotka.firetmsdashboard.ai.persistence.AiQueryHistoryRepository;

@ExtendWith(MockitoExtension.class)
class AiQueryHistoryServiceTest {

    @Mock
    private AiQueryHistoryRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-28T10:15:30Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void saveRedactsSecretsAndAuthorizationHeaders() {
        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        service.save(new AiQueryHistoryWriteRequest(
                "Use key sk-live-secret-123 and Authorization: Bearer sk-live-secret-123",
                "select invoice_number from ai_sales_invoice_view limit 10",
                new AiVisualizationSpec(AiVisualizationSpec.VisualizationType.TABLE, null, null, null),
                "Invoices",
                "Prompt mentioned sk-live-secret-123",
                10,
                AiQueryHistoryStatus.FAILED,
                "OpenAI rejected Authorization: Bearer sk-live-secret-123",
                320L,
                45L), List.of("sk-live-secret-123"));

        ArgumentCaptor<AiQueryHistoryEntity> captor = ArgumentCaptor.forClass(AiQueryHistoryEntity.class);
        verify(repository).save(captor.capture());

        AiQueryHistoryEntity entity = captor.getValue();
        assertThat(entity.getPrompt()).doesNotContain("sk-live-secret-123");
        assertThat(entity.getPrompt()).contains("[REDACTED]");
        assertThat(entity.getExplanation()).doesNotContain("sk-live-secret-123");
        assertThat(entity.getSanitizedErrorMessage()).isEqualTo("OpenAI rejected Authorization: [REDACTED]");
        assertThat(entity.getVisualization()).contains("\"visualization\":\"TABLE\"");
        assertThat(entity.getCreatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 28, 10, 15, 30));
    }

    @Test
    void findRecentMapsRepositoryEntries() {
        AiQueryHistoryEntity entity = new AiQueryHistoryEntity();
        entity.setPrompt("Show recent invoices");
        entity.setGeneratedSql("select invoice_number from ai_sales_invoice_view order by issue_date desc limit 25");
        entity.setVisualization("{\"visualization\":\"TABLE\"}");
        entity.setTitle("Recent invoices");
        entity.setExplanation("Lists recent invoices.");
        entity.setRowCount(25);
        entity.setStatus(AiQueryHistoryStatus.SUCCESS);
        entity.setOpenAiDurationMs(120L);
        entity.setSqlDurationMs(18L);
        entity.setCreatedAt(java.time.LocalDateTime.of(2026, 6, 28, 12, 0, 0));

        when(repository.findAllByOrderByCreatedAtDescIdDesc(any())).thenReturn(List.of(entity));

        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        List<AiQueryHistoryEntry> history = service.findRecent();

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().prompt()).isEqualTo("Show recent invoices");
        assertThat(history.getFirst().generatedSql()).contains("ai_sales_invoice_view");
        assertThat(history.getFirst().rowCount()).isEqualTo(25);
        assertThat(history.getFirst().status()).isEqualTo(AiQueryHistoryStatus.SUCCESS);
    }

    @Test
    void getByIdMapsRepositoryEntry() {
        AiQueryHistoryEntity entity = new AiQueryHistoryEntity();
        entity.setPrompt("Saved prompt");
        entity.setStatus(AiQueryHistoryStatus.SUCCESS);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));

        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        AiQueryHistoryEntry entry = service.getById(11L);

        assertThat(entry.prompt()).isEqualTo("Saved prompt");
        assertThat(entry.status()).isEqualTo(AiQueryHistoryStatus.SUCCESS);
    }

    @Test
    void deleteByIdDelegatesToRepository() {
        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        service.deleteById(15L);

        verify(repository).deleteById(15L);
    }

    @Test
    void deserializeVisualizationReturnsStructuredSpec() {
        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        AiVisualizationSpec visualization = service.deserializeVisualization("""
                {"visualization":"COLUMN","xColumn":"status","yColumn":"invoice_count","seriesColumn":null}
                """);

        assertThat(visualization.visualization()).isEqualTo(AiVisualizationSpec.VisualizationType.COLUMN);
        assertThat(visualization.xColumn()).isEqualTo("status");
        assertThat(visualization.yColumn()).isEqualTo("invoice_count");
    }

    @Test
    void deserializeVisualizationFallsBackToSimpleEnumValue() {
        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        AiVisualizationSpec visualization = service.deserializeVisualization("TABLE");

        assertThat(visualization.visualization()).isEqualTo(AiVisualizationSpec.VisualizationType.TABLE);
    }

    @Test
    void deserializeVisualizationRejectsInvalidValue() {
        AiQueryHistoryService service = new AiQueryHistoryService(repository, objectMapper, clock);

        assertThatThrownBy(() -> service.deserializeVisualization("INVALID"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("Stored visualization metadata is invalid.");
    }
}
