package com.ghana.dashboard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(
            Long id,
            String type,
            String title,
            String fileName,
            long fileSize,
            String fileType,
            String kind,
            Long batchId,
            String batchCode,
            String notes,
            String uploadedByName,
            String createdAt
    ) {
    }

    public record DocumentUpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String type,
            Long batchId,
            @Size(max = 1000) String notes
    ) {
    }

    public record QualityNoteRequest(
            @Size(max = 40) String colour,
            @Min(1) @Max(5) Integer colourScore,
            @Min(1) @Max(5) Integer aromaScore,
            @Min(1) @Max(5) Integer tasteScore,
            @Size(max = 20) String clarity,
            @Size(max = 10) String sedimentLevel,
            @Size(max = 100) String testedBy,
            LocalDate testedOn,
            @Size(max = 1000) String remarks
    ) {
    }

    public record QualityNoteResponse(
            Long id,
            Long batchId,
            String colour,
            Integer colourScore,
            Integer aromaScore,
            Integer tasteScore,
            String clarity,
            String sedimentLevel,
            String testedBy,
            LocalDate testedOn,
            String remarks,
            String createdAt
    ) {
    }
}
