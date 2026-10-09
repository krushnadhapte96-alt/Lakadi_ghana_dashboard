package com.ghana.dashboard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.SeedStock;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.BatchRepository;
import com.ghana.dashboard.repository.SeedStockRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import com.ghana.dashboard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documents vault + batch quality notes, against the real schema.
 * Covers the Prompt 16 checklist: upload/download round-trip, cross-shop blocking,
 * and a lab report showing up on the batch page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class DocumentFlowIntegrationTest {

    private static final String COOKIE = "ghana_refresh";
    private static final Long OIL_TYPE_ID = 1L;
    private static final Long SEED_TYPE_ID = 1L;

    /** Smallest valid PNG (1x1 transparent) so the magic-number check passes. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedStockRepository seedStockRepository;

    @Autowired
    private SeedTypeRepository seedTypeRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private JsonNode registerShop(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-shop")
                        .with(request -> {
                            request.setRemoteAddr("10.0." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "shopName", "Doc Shop " + phone,
                                "ownerName", "Owner " + phone,
                                "phone", phone,
                                "email", phone + "@example.com",
                                "password", "secret1234"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode shop = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        TEST_SHOP_IDS.add(shop.path("shopId").asLong());
        return shop;
    }

    /** Shop ids used by this run, so @AfterAll can remove their upload folders. */
    private static final java.util.Set<Long> TEST_SHOP_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Every test rolls back, which also discards the documents rows — but the stored files
     * survive on disk. Without this the upload directory grows with every run.
     */
    @org.junit.jupiter.api.AfterAll
    static void removeUploadArtifacts() {
        Path root = Path.of(System.getProperty("user.dir"), "uploads", "documents");
        for (Long shopId : TEST_SHOP_IDS) {
            Path shopFolder = root.resolve(String.valueOf(shopId));
            if (!Files.isDirectory(shopFolder)) continue;
            try (var walk = Files.walk(shopFolder)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                        // Best effort: a locked file must not fail the suite.
                    }
                });
            } catch (java.io.IOException ignored) {
                // Shop uploaded nothing, or the tree changed underneath us.
            }
        }
    }

    private String login(String identifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.0." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", identifier, "password", "secret1234"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private String staffToken(JsonNode shop, String phone) throws Exception {
        User staff = new User();
        staff.setShopId(shop.path("shopId").asLong());
        staff.setName("Staff " + phone);
        staff.setPhone(phone);
        staff.setEmail(null);
        staff.setPassword(passwordEncoder.encode("secret1234"));
        staff.setRole(Role.STAFF);
        staff.setActive(true);
        staff.setCreatedAt(LocalDateTime.now());
        staff.setUpdatedAt(LocalDateTime.now());
        userRepository.save(staff);
        return login(phone);
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private Long createBatch(String token, Long shopId) throws Exception {
        stockSeed(shopId);
        MvcResult result = mockMvc.perform(post("/api/v1/batches")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oilTypeId", OIL_TYPE_ID,
                                "seedTypeId", SEED_TYPE_ID,
                                "seedKg", "50.000",
                                "pressStart", "2026-01-05T09:00:00"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    /** Pressing consumes seed stock, so the shop needs a balance before it can start a batch. */
    private void stockSeed(Long shopId) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, SEED_TYPE_ID)
                .orElseGet(() -> {
                    SeedStock fresh = new SeedStock();
                    fresh.setShopId(shopId);
                    fresh.setSeedType(seedTypeRepository.findById(SEED_TYPE_ID).orElseThrow());
                    fresh.setAvailableKg(new java.math.BigDecimal("500.000"));
                    fresh.setAvgRate(new java.math.BigDecimal("120.00"));
                    fresh.setLowStockThreshold(new java.math.BigDecimal("100.000"));
                    return fresh;
                });
        stock.setAvailableKg(new java.math.BigDecimal("500.000"));
        seedStockRepository.save(stock);
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    private MvcResult upload(String token, MockMultipartFile file, String type, String title, Long batchId, String notes)
            throws Exception {
        var builder = multipart("/api/v1/documents")
                .file(file)
                .param("type", type)
                .param("title", title)
                .header("Authorization", auth(token));
        if (batchId != null) builder.param("batchId", String.valueOf(batchId));
        if (notes != null) builder.param("notes", notes);
        return mockMvc.perform(builder).andReturn();
    }

    private Long uploadId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static final java.util.concurrent.atomic.AtomicLong PHONE_SEQ =
            new java.util.concurrent.atomic.AtomicLong(200_000_000L);

    private String randomPhone() {
        return String.valueOf(9_000_000_000L + PHONE_SEQ.incrementAndGet() + (long) (Math.random() * 1_000_000L));
    }

    // â”€â”€ checklist: upload / download works â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void uploadThenDownloadRoundTripsTheBytes() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        Long id = uploadId(upload(token, png("ghana.png"), "GHANA_PHOTO", "Ghana of the day", null, "settled batch"));

        mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("GHANA_PHOTO"))
                .andExpect(jsonPath("$.data.title").value("Ghana of the day"))
                .andExpect(jsonPath("$.data.kind").value("IMAGE"))
                .andExpect(jsonPath("$.data.fileName").value("ghana.png"))
                .andExpect(jsonPath("$.data.batchId").doesNotExist())
                .andExpect(jsonPath("$.data.uploadedByName").isNotEmpty());

        byte[] served = mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);

        // Cleanup so the upload directory does not accumulate test files.
        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk());
    }

    @Test
    void downloadFlagSwitchesToAttachment() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long id = uploadId(upload(token, png("lab.png"), "LAB_REPORT", "Lab report", null, null));

        String disposition = mockMvc.perform(get("/api/v1/files/" + id).param("download", "true")
                        .header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment").contains("lab.png");

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)));
    }

    @Test
    void deletingRemovesTheStoredFile() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long id = uploadId(upload(token, png("temp.png"), "SHOP_PHOTO", "Shop front", null, null));

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", auth(token)))
                .andExpect(status().isNotFound());
    }

    // â”€â”€ checklist: other shops blocked â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void otherShopCannotSeeOrDownloadTheDocument() throws Exception {
        JsonNode shopA = registerShop(randomPhone());
        JsonNode shopB = registerShop(randomPhone());
        String tokenA = login(shopA.path("phone").asText());
        String tokenB = login(shopB.path("phone").asText());

        Long id = uploadId(upload(tokenA, png("private.png"), "CERTIFICATE", "A's certificate", null, null));

        // Metadata, listing and the raw file are all invisible to B, and B's own
        // gallery never mentions it.
        mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", auth(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", auth(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents").header("Authorization", auth(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // B cannot re-point A's document at its own batch, nor delete it.
        Long batchB = createBatch(tokenB, shopB.path("shopId").asLong());
        mockMvc.perform(put("/api/v1/documents/" + id).header("Authorization", auth(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "stolen", "type", "OTHER", "batchId", batchB, "notes", ""))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(tokenB)))
                .andExpect(status().isNotFound());

        // A is unaffected.
        mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", auth(tokenA)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(tokenA)));
    }

    @Test
    void batchDocumentsAndQualityNotesAreTenantChecked() throws Exception {
        JsonNode shopA = registerShop(randomPhone());
        JsonNode shopB = registerShop(randomPhone());
        String tokenA = login(shopA.path("phone").asText());
        String tokenB = login(shopB.path("phone").asText());

        Long batchA = createBatch(tokenA, shopA.path("shopId").asLong());
        uploadId(upload(tokenA, png("qa.png"), "LAB_REPORT", "A's lab report", batchA, null));

        // B asks about A's batch: treated as missing, never as forbidden.
        mockMvc.perform(get("/api/v1/batches/" + batchA + "/documents").header("Authorization", auth(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/batches/" + batchA + "/quality-notes").header("Authorization", auth(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/batches/" + batchA + "/quality-notes").header("Authorization", auth(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("remarks", "sneaky"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/documents").header("Authorization", auth(tokenA)));
    }

    @Test
    void unauthenticatedFileAccessIsRejected() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long id = uploadId(upload(token, png("guarded.png"), "OTHER", "Guarded", null, null));

        mockMvc.perform(get("/api/v1/files/" + id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)));
    }

    @Test
    void staffCannotDeleteButCanUpload() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String staff = staffToken(shop, randomPhone());

        Long id = uploadId(upload(staff, png("staff.png"), "SHOP_PHOTO", "Staff upload", null, null));
        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(staff)))
                .andExpect(status().isForbidden());

        // Owner can clean it up.
        String owner = login(shop.path("phone").asText());
        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(owner)))
                .andExpect(status().isOk());
    }

    // â”€â”€ checklist: lab report shows on the batch page â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void labReportUploadedAgainstABatchAppearsOnThatBatch() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        Long batchId = createBatch(token, shop.path("shopId").asLong());
        Long docId = uploadId(upload(token, png("report.png"), "LAB_REPORT", "GC-MS lab report", batchId, "batch 7"));

        // The batch's own Documents tab.
        mockMvc.perform(get("/api/v1/batches/" + batchId + "/documents").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(docId))
                .andExpect(jsonPath("$.data[0].type").value("LAB_REPORT"))
                .andExpect(jsonPath("$.data[0].title").value("GC-MS lab report"))
                .andExpect(jsonPath("$.data[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.data[0].batchId").value(batchId))
                .andExpect(jsonPath("$.data[0].batchCode").isNotEmpty())
                .andExpect(jsonPath("$.data[0].notes").value("batch 7"));

        // It is downloadable from the batch view.
        mockMvc.perform(get("/api/v1/files/" + docId).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        // And reachable through the type filter of the main gallery.
        mockMvc.perform(get("/api/v1/documents").param("type", "LAB_REPORT").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(docId));

        // A document with no batch never leaks into a batch's tab.
        uploadId(upload(token, png("shop.png"), "SHOP_PHOTO", "Unrelated", null, null));
        mockMvc.perform(get("/api/v1/batches/" + batchId + "/documents").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void uploadingAgainstAForeignBatchIsRejected() throws Exception {
        JsonNode shopA = registerShop(randomPhone());
        JsonNode shopB = registerShop(randomPhone());
        String tokenA = login(shopA.path("phone").asText());
        Long batchB = createBatch(login(shopB.path("phone").asText()), shopB.path("shopId").asLong());

        MvcResult result = upload(tokenA, png("cross.png"), "LAB_REPORT", "Cross-tenant", batchB, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // â”€â”€ document CRUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void updateRetitlesAndRefiltersWithoutTouchingTheFile() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        Long id = uploadId(upload(token, png("edit-me.png"), "OTHER", "Before", null, null));

        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("title", "After");
        patch.put("type", "SHOP_PHOTO");
        patch.put("batchId", null);
        patch.put("notes", "retitled");

        mockMvc.perform(put("/api/v1/documents/" + id).header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After"))
                .andExpect(jsonPath("$.data.type").value("SHOP_PHOTO"))
                .andExpect(jsonPath("$.data.notes").value("retitled"));

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)));
    }

    @Test
    void unknownDocumentTypeIsRejected() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(png("x.png"))
                        .param("type", "SCANNER_SOUVENIR")
                        .param("title", "Nope")
                        .header("Authorization", auth(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRequiresFileAndTitle() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        mockMvc.perform(multipart("/api/v1/documents")
                        .param("type", "OTHER")
                        .param("title", "No file")
                        .header("Authorization", auth(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(png("x.png"))
                        .param("type", "OTHER")
                        .param("title", "  ")
                        .header("Authorization", auth(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void executableRenamedToJpgIsRejected() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        byte[] elf = new byte[] { 0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00, 0, 0, 0, 0 };
        MvcResult result = upload(token,
                new MockMultipartFile("file", "payload.jpg", "image/jpeg", elf), "GHANA_PHOTO", "Sneaky", null, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("does not match its extension");
    }

    @Test
    void unsupportedExtensionIsRejected() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());

        MvcResult result = upload(token,
                new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[] { 'M', 'Z', 0, 0 }),
                "OTHER", "Windows binary", null, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("Unsupported file type");
    }

    @Test
    void pdfLabReportIsStoredAndTypedAsPdf() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long batchId = createBatch(token, shop.path("shopId").asLong());

        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".getBytes();
        Long id = uploadId(upload(token,
                new MockMultipartFile("file", "lab.pdf", "application/pdf", pdf),
                "LAB_REPORT", "FSSAI lab report", batchId, null));

        mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("PDF"))
                .andExpect(jsonPath("$.data.fileType").value("application/pdf"));

        mockMvc.perform(get("/api/v1/batches/" + batchId + "/documents").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].kind").value("PDF"));

        mockMvc.perform(delete("/api/v1/documents/" + id).header("Authorization", auth(token)));
    }

    // â”€â”€ quality notes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void qualityNoteCrudRoundTripsEveryObservationField() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long batchId = createBatch(token, shop.path("shopId").asLong());

        MvcResult created = mockMvc.perform(post("/api/v1/batches/" + batchId + "/quality-notes")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "colour", "Golden amber",
                                "colourScore", 5,
                                "aromaScore", 4,
                                "tasteScore", 5,
                                "clarity", "Clear",
                                "sedimentLevel", "LOW",
                                "testedBy", "Lab technician",
                                "testedOn", "2026-01-20",
                                "remarks", "Ready for bottling"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.batchId").value(batchId))
                .andExpect(jsonPath("$.data.colour").value("Golden amber"))
                .andExpect(jsonPath("$.data.colourScore").value(5))
                .andExpect(jsonPath("$.data.aromaScore").value(4))
                .andExpect(jsonPath("$.data.tasteScore").value(5))
                .andExpect(jsonPath("$.data.clarity").value("Clear"))
                .andExpect(jsonPath("$.data.sedimentLevel").value("LOW"))
                .andExpect(jsonPath("$.data.testedBy").value("Lab technician"))
                .andExpect(jsonPath("$.data.testedOn").value("2026-01-20"))
                .andExpect(jsonPath("$.data.remarks").value("Ready for bottling"))
                .andReturn();

        long noteId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/batches/" + batchId + "/quality-notes").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(noteId));

        mockMvc.perform(put("/api/v1/batches/" + batchId + "/quality-notes/" + noteId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "colour", "Deep gold",
                                "colourScore", 4,
                                "aromaScore", 4,
                                "tasteScore", 4,
                                "clarity", "Slight haze",
                                "sedimentLevel", "medium",
                                "testedBy", "Lab technician",
                                "testedOn", "2026-01-22",
                                "remarks", "Sediment settling"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colour").value("Deep gold"))
                .andExpect(jsonPath("$.data.sedimentLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.clarity").value("Slight haze"));

        mockMvc.perform(delete("/api/v1/batches/" + batchId + "/quality-notes/" + noteId)
                        .header("Authorization", auth(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/batches/" + batchId + "/quality-notes").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void qualityNoteRejectsOutOfRangeScoresAndEmptyRecords() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long batchId = createBatch(token, shop.path("shopId").asLong());

        mockMvc.perform(post("/api/v1/batches/" + batchId + "/quality-notes")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tasteScore", 6))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/batches/" + batchId + "/quality-notes")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tasteScore", 0))))
                .andExpect(status().isBadRequest());

        // Nothing recorded at all is a mistake worth reporting.
        mockMvc.perform(post("/api/v1/batches/" + batchId + "/quality-notes")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("remarks", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Record at least one quality observation"));

        mockMvc.perform(post("/api/v1/batches/" + batchId + "/quality-notes")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sedimentLevel", "EXTREME"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sediment level must be NONE, LOW, MEDIUM or HIGH"));
    }

    @Test
    void deletingABatchTakesItsDocumentsWithIt() throws Exception {
        JsonNode shop = registerShop(randomPhone());
        String token = login(shop.path("phone").asText());
        Long batchId = createBatch(token, shop.path("shopId").asLong());
        Long docId = uploadId(upload(token, png("orphan.png"), "LAB_REPORT", "Orphan", batchId, null));

        mockMvc.perform(delete("/api/v1/batches/" + batchId).header("Authorization", auth(token)))
                .andExpect(status().isOk());
        // JdbcTemplate reads do not trigger a Hibernate flush, and the test wraps every
        // call in one transaction, so push the DELETE out before checking the FK's effect.
        batchRepository.flush();

        // The document survives but is no longer pinned to a batch.
        mockMvc.perform(get("/api/v1/documents/" + docId).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").doesNotExist());

        mockMvc.perform(delete("/api/v1/documents/" + docId).header("Authorization", auth(token)));
    }
}
