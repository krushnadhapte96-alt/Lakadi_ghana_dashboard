package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findByActiveTrueOrderBySortOrderAsc();

    Optional<ExpenseCategory> findByCategoryType(ExpenseCategory.CategoryType categoryType);

    Optional<ExpenseCategory> findByIdAndActiveTrue(Long id);
}
