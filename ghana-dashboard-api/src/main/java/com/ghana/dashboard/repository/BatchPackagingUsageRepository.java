package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.BatchPackagingUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchPackagingUsageRepository extends JpaRepository<BatchPackagingUsage, Long> {

    List<BatchPackagingUsage> findByBatchId(Long batchId);
}
