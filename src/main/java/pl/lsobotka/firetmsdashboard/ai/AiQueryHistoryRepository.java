package pl.lsobotka.firetmsdashboard.ai;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiQueryHistoryRepository extends JpaRepository<AiQueryHistoryEntity, Long> {

    List<AiQueryHistoryEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
