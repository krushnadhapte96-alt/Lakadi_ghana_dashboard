package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.service.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expense-categories")
@PreAuthorize("hasRole('OWNER')")
public class ExpenseCategoryController {

    private final ExpenseService expenseService;

    public ExpenseCategoryController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDtos.ExpenseCategoryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getAllCategories()));
    }
}
