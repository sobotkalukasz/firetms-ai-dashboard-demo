package pl.lsobotka.firetmsdashboard.ai.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQueryHistoryRepository extends JpaRepository<AiQueryHistoryEntity, Long> {

    List<AiQueryHistoryEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
