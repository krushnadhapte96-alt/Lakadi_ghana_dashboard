package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploads under {@code app.storage.upload-dir/<folder>/<shopId>/} with a random name, validates the
 * extension, size and magic number, and only ever reads back paths that live inside the upload root.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    public static final long MAX_VIDEO_MB = 50;
    private static final Map<String, String> MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "pdf", "application/pdf", "mp4", "video/mp4", "webm", "video/webm", "mov", "video/quicktime");

    private final AppProperties props;

    public FileStorageService(AppProperties props) {
        this.props = props;
    }

    public record Stored(Path path, String originalName, long size, String contentType) {
    }

    public record Download(Resource resource, String fileName, String contentType) {
    }

    public Path root() {
        return Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    public Stored store(Long shopId, String folder, MultipartFile file, boolean allowVideo) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(400, "Choose a file to upload");
        }
        String original = sanitize(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MIME.get(ext);
        if (mime == null || (!allowVideo && mime.startsWith("video/"))) {
            throw new DomainException(400, "Unsupported file type. Allowed: jpg, png, webp, pdf" + (allowVideo ? ", mp4, webm, mov" : ""));
        }
        long limitMb = mime.startsWith("video/") ? MAX_VIDEO_MB : props.getStorage().getMaxFileSizeMb();
        if (file.getSize() > limitMb * 1024 * 1024) {
            throw new DomainException(413, "File is too large. The limit for this type is " + limitMb + " MB");
        }
        try {
            Path dir = root().resolve(folder).resolve(String.valueOf(shopId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
            if (!target.startsWith(root())) {
                throw new DomainException(400, "Invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!looksLike(target, mime)) {
                delete(target);
                throw new DomainException(400, "The file content does not match its extension");
            }
            return new Stored(target, original, file.getSize(), mime);
        } catch (IOException e) {
            throw new DomainException(500, "Could not store the file");
        }
    }

    public Download read(String storedPath, String fileName, String contentType) {
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(root()) || !Files.isReadable(path)) {
            throw new DomainException(404, "File is missing from storage");
        }
        return new Download(new PathResource(path), fileName, contentType);
    }

    public void delete(String storedPath) {
        if (storedPath != null) {
            delete(Paths.get(storedPath));
        }
    }

    public void delete(Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.startsWith(root())) {
                Files.deleteIfExists(normalized);
            }
        } catch (IOException e) {
            log.warn("Could not delete {}", path, e);
        }
    }

    /** Cheap magic-number check so a renamed executable cannot masquerade as a photo or PDF. */
    private static boolean looksLike(Path path, String mime) throws IOException {
        byte[] head = new byte[12];
        try (InputStream in = Files.newInputStream(path)) {
            if (in.readNBytes(head, 0, head.length) < 4) {
                return false;
            }
        }
        return switch (mime) {
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "image/webp" -> head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
            case "application/pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "video/webm" -> (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45;
            default -> true; // mp4 / mov containers vary; extension and size limits apply
        };
    }

    private static String sanitize(String name) {
        String base = name == null ? "file" : Paths.get(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return cleaned.isBlank() ? "file" : cleaned.substring(0, Math.min(cleaned.length(), 200));
    }
}
