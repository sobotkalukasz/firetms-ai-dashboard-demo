package pl.lsobotka.firetmsdashboard.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_query_history")
public class AiQueryHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Lob
    @Column(name = "generated_sql")
    private String generatedSql;

    @Lob
    @Column(name = "visualization")
    private String visualization;

    @Column(name = "title")
    private String title;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "row_count")
    private Integer rowCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiQueryHistoryStatus status;

    @Column(name = "sanitized_error_message")
    private String sanitizedErrorMessage;

    @Column(name = "openai_duration_ms")
    private Long openAiDurationMs;

    @Column(name = "sql_duration_ms")
    private Long sqlDurationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public void setGeneratedSql(String generatedSql) {
        this.generatedSql = generatedSql;
    }

    public String getVisualization() {
        return visualization;
    }

    public void setVisualization(String visualization) {
        this.visualization = visualization;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public AiQueryHistoryStatus getStatus() {
        return status;
    }

    public void setStatus(AiQueryHistoryStatus status) {
        this.status = status;
    }

    public String getSanitizedErrorMessage() {
        return sanitizedErrorMessage;
    }

    public void setSanitizedErrorMessage(String sanitizedErrorMessage) {
        this.sanitizedErrorMessage = sanitizedErrorMessage;
    }

    public Long getOpenAiDurationMs() {
        return openAiDurationMs;
    }

    public void setOpenAiDurationMs(Long openAiDurationMs) {
        this.openAiDurationMs = openAiDurationMs;
    }

    public Long getSqlDurationMs() {
        return sqlDurationMs;
    }

    public void setSqlDurationMs(Long sqlDurationMs) {
        this.sqlDurationMs = sqlDurationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
