package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.BatchDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.BatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BatchDtos.CreateBatchResponse>> createBatch(
            @Valid @RequestBody BatchDtos.CreateBatchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.createBatch(CurrentUser.getShopId(), request, CurrentUser.getUserId())));
    }

    @PostMapping("/{id}/output")
    public ResponseEntity<ApiResponse<BatchDtos.RecordOutputResponse>> recordOutput(
            @PathVariable Long id,
            @Valid @RequestBody BatchDtos.RecordOutputRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.recordOutput(CurrentUser.getShopId(), id, request, CurrentUser.getUserId())));
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<ApiResponse<BatchDtos.MarkReadyResponse>> markReady(
            @PathVariable Long id,
            @Valid @RequestBody BatchDtos.MarkReadyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.markReady(CurrentUser.getShopId(), id, request, CurrentUser.getUserId())));
    }

    @PostMapping("/{id}/bottle")
    public ResponseEntity<ApiResponse<BatchDtos.BottleBatchResponse>> bottleBatch(
            @PathVariable Long id,
            @Valid @RequestBody BatchDtos.BottleBatchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.bottleBatch(CurrentUser.getShopId(), id, request, CurrentUser.getUserId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BatchDtos.BatchSummaryResponse>>> getBatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long oilTypeId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.getBatches(CurrentUser.getShopId(),
                        new BatchDtos.BatchFilter(status, oilTypeId, code, dateFrom, dateTo))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BatchDtos.BatchDetailResponse>> getBatch(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.getBatchDetail(CurrentUser.getShopId(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BatchDtos.BatchDetailResponse>> updateBatch(
            @PathVariable Long id,
            @Valid @RequestBody BatchDtos.CreateBatchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.updateBatchPressing(CurrentUser.getShopId(), id, request, CurrentUser.getUserId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable Long id) {
        batchService.deleteBatch(CurrentUser.getShopId(), id, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(200, "Batch deleted and seed restored"));
    }

    @GetMapping("/{id}/label")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabel(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.getLabelData(CurrentUser.getShopId(), id)));
    }
}
