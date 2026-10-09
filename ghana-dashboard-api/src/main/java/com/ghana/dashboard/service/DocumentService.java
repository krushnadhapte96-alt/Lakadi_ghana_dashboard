package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.config.AppProperties;
import com.ghana.dashboard.dto.DocumentDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Document vault: lab reports, ghana photos/videos, certificates - tenant-isolated and served only through the API. */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> TYPES = Set.of("LAB_REPORT", "GHANA_PHOTO", "GHANA_VIDEO", "SHOP_PHOTO", "CERTIFICATE", "OTHER");
    private static final long MAX_VIDEO_MB = 50;
    private static final Map<String, String> MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "pdf", "application/pdf", "mp4", "video/mp4", "webm", "video/webm", "mov", "video/quicktime");

    private final JdbcSupport db;
    private final AppProperties props;

    public DocumentService(JdbcSupport db, AppProperties props) {
        this.db = db;
        this.props = props;
    }

    public record Download(Resource resource, String fileName, String contentType) {
    }

    // ==================== CRUD ====================

    @Transactional
    public DocumentDtos.DocumentResponse upload(Long shopId, Long userId, MultipartFile file, String type, String title,
                                                Long batchId, String notes) {
        String docType = normalizeType(type);
        if (file == null || file.isEmpty()) {
            throw new DomainException(400, "Choose a file to upload");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException(400, "Give the document a title");
        }
        if (batchId != null) {
            requireBatch(shopId, batchId);
        }
        Stored stored = store(shopId, file);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", shopId);
        row.put("doc_type", docType);
        row.put("title", title.trim());
        row.put("file_name", stored.originalName());
        row.put("file_path", stored.path().toString());
        row.put("file_size", stored.size());
        row.put("file_type", stored.contentType());
        row.put("batch_id", batchId);
        row.put("notes", notes == null || notes.isBlank() ? null : notes.trim());
        row.put("uploaded_by", userId);
        row.put("created_at", Timestamp.valueOf(now));
        row.put("updated_at", Timestamp.valueOf(now));
        try {
            return get(shopId, db.insert("documents", row));
        } catch (RuntimeException e) {
            deleteQuietly(stored.path());
            throw e;
        }
    }

    private static final String SELECT = """
            SELECT d.id, d.doc_type, d.title, d.file_name, d.file_size, d.file_type, d.batch_id, b.code AS batch_code,
                   d.notes, u.name AS uploader, d.created_at
            FROM documents d JOIN users u ON u.id = d.uploaded_by LEFT JOIN batches b ON b.id = d.batch_id
            WHERE d.shop_id = :shop""";

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> list(Long shopId, String type, Long batchId, String query) {
        if (batchId != null) {
            // Same contract as the quality-notes endpoints: another shop's batch is simply missing.
            requireBatch(shopId, batchId);
        }
        StringBuilder sql = new StringBuilder(SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource("shop", shopId);
        if (type != null && !type.isBlank()) {
            sql.append(" AND d.doc_type = :type");
            params.addValue("type", normalizeType(type));
        }
        if (batchId != null) {
            sql.append(" AND d.batch_id = :batch");
            params.addValue("batch", batchId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (d.title LIKE :q OR d.notes LIKE :q)");
            params.addValue("q", "%" + query.trim() + "%");
        }
        sql.append(" ORDER BY d.created_at DESC, d.id DESC");
        return db.jdbc().query(sql.toString(), params, (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DocumentResponse get(Long shopId, Long id) {
        return db.jdbc().query(SELECT + " AND d.id = :id", new MapSqlParameterSource("shop", shopId).addValue("id", id),
                (rs, i) -> map(rs)).stream().findFirst().orElseThrow(() -> new DomainException(404, "Document not found"));
    }

    @Transactional
    public DocumentDtos.DocumentResponse update(Long shopId, Long id, DocumentDtos.DocumentUpdateRequest request) {
        get(shopId, id);
        if (request.batchId() != null) {
            requireBatch(shopId, request.batchId());
        }
        db.jdbc().update("""
                UPDATE documents SET title = :title, doc_type = :type, batch_id = :batch, notes = :notes, updated_at = :now
                WHERE id = :id AND shop_id = :shop""", new MapSqlParameterSource()
                .addValue("title", request.title().trim()).addValue("type", normalizeType(request.type()))
                .addValue("batch", request.batchId()).addValue("notes", request.notes())
                .addValue("now", Timestamp.valueOf(LocalDateTime.now())).addValue("id", id).addValue("shop", shopId));
        return get(shopId, id);
    }

    @Transactional
    public void delete(Long shopId, Long id) {
        List<String> paths = db.jdbc().queryForList("SELECT file_path FROM documents WHERE id = :id AND shop_id = :shop",
                Map.of("id", id, "shop", shopId), String.class);
        if (paths.isEmpty()) {
            throw new DomainException(404, "Document not found");
        }
        db.jdbc().update("DELETE FROM documents WHERE id = :id AND shop_id = :shop", Map.of("id", id, "shop", shopId));
        deleteQuietly(Paths.get(paths.get(0)));
    }

    /** Authenticated, tenant-checked file access. A document of another shop is indistinguishable from a missing one. */
    @Transactional(readOnly = true)
    public Download download(Long shopId, Long id) {
        List<Map<String, Object>> rows = db.jdbc().queryForList(
                "SELECT file_path, file_name, file_type FROM documents WHERE id = :id AND shop_id = :shop",
                Map.of("id", id, "shop", shopId));
        if (rows.isEmpty()) {
            throw new DomainException(404, "File not found");
        }
        Path path = Paths.get((String) rows.get(0).get("file_path")).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot()) || !Files.isReadable(path)) {
            throw new DomainException(404, "File is missing from storage");
        }
        return new Download(new PathResource(path), (String) rows.get(0).get("file_name"), (String) rows.get(0).get("file_type"));
    }

    // ==================== Quality notes ====================

    private static final String QUALITY_SELECT = """
            SELECT id, batch_id, colour, colour_score, aroma_score, taste_score, clarity, sediment_level, tested_by,
                   tested_on, COALESCE(remarks, note) AS remarks, created_at
            FROM batch_quality_notes WHERE batch_id = :batch""";

    @Transactional(readOnly = true)
    public List<DocumentDtos.QualityNoteResponse> qualityNotes(Long shopId, Long batchId) {
        requireBatch(shopId, batchId);
        return db.jdbc().query(QUALITY_SELECT + " ORDER BY COALESCE(tested_on, DATE(created_at)) DESC, id DESC",
                Map.of("batch", batchId), (rs, i) -> mapQuality(rs));
    }

    @Transactional
    public DocumentDtos.QualityNoteResponse addQualityNote(Long shopId, Long batchId, DocumentDtos.QualityNoteRequest r) {
        requireBatch(shopId, batchId);
        requireSomething(r);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batch_id", batchId);
        row.put("shop_id", shopId);
        fill(row, r);
        row.put("created_at", Timestamp.valueOf(now));
        row.put("updated_at", Timestamp.valueOf(now));
        long id = db.insert("batch_quality_notes", row);
        return findQuality(batchId, id);
    }

    @Transactional
    public DocumentDtos.QualityNoteResponse updateQualityNote(Long shopId, Long batchId, Long noteId, DocumentDtos.QualityNoteRequest r) {
        requireBatch(shopId, batchId);
        requireSomething(r);
        findQuality(batchId, noteId);
        Map<String, Object> row = new LinkedHashMap<>();
        fill(row, r);
        row.put("updated_at", Timestamp.valueOf(LocalDateTime.now()));
        MapSqlParameterSource params = new MapSqlParameterSource("id", noteId).addValue("batch", batchId);
        StringBuilder set = new StringBuilder();
        row.forEach((k, v) -> {
            set.append(set.length() == 0 ? "" : ", ").append(k).append(" = :").append(k);
            params.addValue(k, v);
        });
        db.jdbc().update("UPDATE batch_quality_notes SET " + set + " WHERE id = :id AND batch_id = :batch", params);
        return findQuality(batchId, noteId);
    }

    @Transactional
    public void deleteQualityNote(Long shopId, Long batchId, Long noteId) {
        requireBatch(shopId, batchId);
        if (db.jdbc().update("DELETE FROM batch_quality_notes WHERE id = :id AND batch_id = :batch",
                Map.of("id", noteId, "batch", batchId)) == 0) {
            throw new DomainException(404, "Quality note not found");
        }
    }

    private DocumentDtos.QualityNoteResponse findQuality(Long batchId, Long noteId) {
        return db.jdbc().query(QUALITY_SELECT + " AND id = :id", Map.of("batch", batchId, "id", noteId),
                (rs, i) -> mapQuality(rs)).stream().findFirst().orElseThrow(() -> new DomainException(404, "Quality note not found"));
    }

    private static void fill(Map<String, Object> row, DocumentDtos.QualityNoteRequest r) {
        String sediment = r.sedimentLevel() == null ? null : r.sedimentLevel().trim().toUpperCase(Locale.ROOT);
        if (sediment != null && !Set.of("NONE", "LOW", "MEDIUM", "HIGH").contains(sediment)) {
            throw new DomainException(400, "Sediment level must be NONE, LOW, MEDIUM or HIGH");
        }
        row.put("colour", blank(r.colour()));
        row.put("colour_score", r.colourScore());
        row.put("aroma_score", r.aromaScore());
        row.put("taste_score", r.tasteScore());
        row.put("clarity", blank(r.clarity()));
        row.put("sediment_level", sediment);
        row.put("tested_by", blank(r.testedBy()));
        row.put("checked_by", blank(r.testedBy()));
        row.put("tested_on", r.testedOn() == null ? null : java.sql.Date.valueOf(r.testedOn()));
        row.put("remarks", blank(r.remarks()));
        row.put("note", blank(r.remarks()));
    }

    private static void requireSomething(DocumentDtos.QualityNoteRequest r) {
        if (blank(r.colour()) == null && r.colourScore() == null && r.aromaScore() == null && r.tasteScore() == null
                && blank(r.clarity()) == null && blank(r.sedimentLevel()) == null && blank(r.remarks()) == null) {
            throw new DomainException(400, "Record at least one quality observation");
        }
    }

    // ==================== helpers ====================

    private void requireBatch(Long shopId, Long batchId) {
        Integer n = db.jdbc().queryForObject("SELECT COUNT(*) FROM batches WHERE id = :id AND shop_id = :shop",
                Map.of("id", batchId, "shop", shopId), Integer.class);
        if (n == null || n == 0) {
            throw new DomainException(404, "Batch not found");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new DomainException(400, "Document type must be one of " + TYPES);
        }
        return t;
    }

    private static String kind(String mime) {
        return mime.startsWith("image/") ? "IMAGE" : mime.startsWith("video/") ? "VIDEO" : "PDF";
    }

    private static DocumentDtos.DocumentResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long batch = rs.getLong("batch_id");
        boolean noBatch = rs.wasNull();
        String type = rs.getString("file_type");
        return new DocumentDtos.DocumentResponse(rs.getLong("id"), rs.getString("doc_type"), rs.getString("title"),
                rs.getString("file_name"), rs.getLong("file_size"), type, kind(type), noBatch ? null : batch,
                rs.getString("batch_code"), rs.getString("notes"), rs.getString("uploader"),
                JdbcSupport.iso(JdbcSupport.dateTime(rs, "created_at")));
    }

    private static DocumentDtos.QualityNoteResponse mapQuality(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DocumentDtos.QualityNoteResponse(rs.getLong("id"), rs.getLong("batch_id"), rs.getString("colour"),
                (Integer) rs.getObject("colour_score"), (Integer) rs.getObject("aroma_score"),
                (Integer) rs.getObject("taste_score"), rs.getString("clarity"), rs.getString("sediment_level"),
                rs.getString("tested_by"), JdbcSupport.date(rs, "tested_on"), rs.getString("remarks"),
                JdbcSupport.iso(JdbcSupport.dateTime(rs, "created_at")));
    }

    // ==================== storage ====================

    private record Stored(Path path, String originalName, long size, String contentType) {
    }

    private Path uploadRoot() {
        return Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    private Stored store(Long shopId, MultipartFile file) {
        String original = sanitize(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MIME.get(ext);
        if (mime == null) {
            throw new DomainException(400, "Unsupported file type. Allowed: " + String.join(", ", MIME.keySet()));
        }
        long limitMb = mime.startsWith("video/") ? MAX_VIDEO_MB : props.getStorage().getMaxFileSizeMb();
        if (file.getSize() > limitMb * 1024 * 1024) {
            throw new DomainException(413, "File is too large. Limit for this type is " + limitMb + " MB");
        }
        try {
            Path dir = uploadRoot().resolve("documents").resolve(String.valueOf(shopId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
            if (!target.startsWith(uploadRoot())) {
                throw new DomainException(400, "Invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!looksLike(target, mime)) {
                deleteQuietly(target);
                throw new DomainException(400, "The file content does not match its extension");
            }
            return new Stored(target, original, file.getSize(), mime);
        } catch (IOException e) {
            throw new DomainException(500, "Could not store the file");
        }
    }

    /** Cheap magic-number check so a renamed executable cannot masquerade as a photo or PDF. */
    private static boolean looksLike(Path path, String mime) throws IOException {
        byte[] head = new byte[12];
        try (InputStream in = Files.newInputStream(path)) {
            int n = in.readNBytes(head, 0, head.length);
            if (n < 4) {
                return false;
            }
        }
        return switch (mime) {
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "image/webp" -> head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
            case "application/pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "video/webm" -> (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45;
            default -> true; // mp4 / mov containers vary; extension + size limits apply
        };
    }

    private static String sanitize(String name) {
        String base = name == null ? "file" : Paths.get(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return cleaned.isBlank() ? "file" : cleaned.substring(0, Math.min(cleaned.length(), 200));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}", path, e);
        }
    }

    /** Used by the data export: all of a shop's documents metadata. */
    public List<DocumentDtos.DocumentResponse> all(Long shopId) {
        return new ArrayList<>(list(shopId, null, null, null));
    }
}
