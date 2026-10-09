package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.BatchQualityNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchQualityNoteRepository extends JpaRepository<BatchQualityNote, Long> {

    List<BatchQualityNote> findByBatchIdOrderByCreatedAtAsc(Long batchId);
}
