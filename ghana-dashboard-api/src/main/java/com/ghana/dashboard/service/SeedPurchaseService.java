package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.config.AppProperties;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.domain.Supplier;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.repository.SeedPurchaseRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import com.ghana.dashboard.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SeedPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(SeedPurchaseService.class);

    private static final Map<String, String> BILL_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final SeedPurchaseRepository seedPurchaseRepository;
    private final SeedTypeRepository seedTypeRepository;
    private final SupplierRepository supplierRepository;
    private final SeedStockService seedStockService;
    private final SupplierService supplierService;
    private final UserService userService;
    private final AppProperties props;

    public SeedPurchaseService(SeedPurchaseRepository seedPurchaseRepository,
                               SeedTypeRepository seedTypeRepository,
                               SupplierRepository supplierRepository,
                               SeedStockService seedStockService,
                               SupplierService supplierService,
                               UserService userService,
                               AppProperties props) {
        this.seedPurchaseRepository = seedPurchaseRepository;
        this.seedTypeRepository = seedTypeRepository;
        this.supplierRepository = supplierRepository;
        this.seedStockService = seedStockService;
        this.supplierService = supplierService;
        this.userService = userService;
        this.props = props;
    }

    // ==================== CRUD ====================

    @Transactional
    public BeejDtos.SeedPurchaseResponse createPurchase(Long shopId, Long userId, BeejDtos.SeedPurchaseForm form) {
        SeedType seedType = requireActiveSeedType(form.seedTypeId());
        Supplier supplier = requireActiveSupplier(shopId, form.supplierId());

        BigDecimal totalAmount = form.qtyKg().multiply(form.ratePerKg()).setScale(2, RoundingMode.HALF_UP);
        if (form.amountPaid().compareTo(totalAmount) > 0) {
            throw new DomainException(400, "Amount paid cannot exceed the purchase total");
        }

        SeedPurchase purchase = new SeedPurchase();
        purchase.setShopId(shopId);
        purchase.setSeedType(seedType);
        purchase.setSupplier(supplier);
        purchase.setQtyKg(form.qtyKg());
        purchase.setRatePerKg(form.ratePerKg());
        purchase.setTotalAmount(totalAmount);
        purchase.setMoisturePercent(form.moisturePercent());
        purchase.setGrade(form.grade());
        purchase.setPurchaseDate(form.purchaseDate());
        purchase.setAmountPaid(form.amountPaid());
        purchase.setPaymentStatus(paymentStatus(form.amountPaid(), totalAmount));
        purchase.setCreatedBy(userId);

        StoredBill bill = storeBill(shopId, form.billFile());
        if (bill != null) {
            applyBill(purchase, bill);
            deleteFileOnRollback(bill.path());
        }

        SeedPurchase saved = seedPurchaseRepository.save(purchase);

        seedStockService.add(shopId, seedType, form.qtyKg(), form.ratePerKg(), userId, "SEED_PURCHASE", saved.getId());

        if (form.amountPaid().signum() > 0) {
            Payment.PaymentMode mode = form.paymentMode() != null ? form.paymentMode() : Payment.PaymentMode.CASH;
            supplierService.createAutoPayment(shopId, supplier, saved.getId(), form.amountPaid(), mode,
                    form.purchaseDate(), userId);
        }

        return toResponse(saved);
    }

    @Transactional
    public BeejDtos.SeedPurchaseResponse updatePurchase(Long shopId, Long userId, Long id,
            BeejDtos.SeedPurchaseUpdateForm form) {
        SeedPurchase purchase = requirePurchase(shopId, id);
        Supplier supplier = requireActiveSupplier(shopId, form.supplierId());

        if (form.amountPaid().compareTo(purchase.getTotalAmount()) > 0) {
            throw new DomainException(400, "Amount paid cannot exceed the purchase total");
        }

        Path oldBill = purchase.getBillFilePath() != null ? Paths.get(purchase.getBillFilePath()) : null;

        purchase.setSupplier(supplier);
        purchase.setMoisturePercent(form.moisturePercent());
        purchase.setGrade(form.grade());
        purchase.setPurchaseDate(form.purchaseDate());
        purchase.setAmountPaid(form.amountPaid());
        purchase.setPaymentStatus(paymentStatus(form.amountPaid(), purchase.getTotalAmount()));

        StoredBill bill = storeBill(shopId, form.billFile());
        if (bill != null) {
            applyBill(purchase, bill);
            deleteFileOnRollback(bill.path());
            deleteFileAfterCommit(oldBill);
        } else if (Boolean.TRUE.equals(form.removeBill()) && oldBill != null) {
            clearBill(purchase);
            deleteFileAfterCommit(oldBill);
        }

        SeedPurchase saved = seedPurchaseRepository.save(purchase);

        supplierService.removePaymentsForPurchase(shopId, saved.getId());
        if (form.amountPaid().signum() > 0) {
            Payment.PaymentMode mode = form.paymentMode() != null ? form.paymentMode() : Payment.PaymentMode.CASH;
            supplierService.createAutoPayment(shopId, supplier, saved.getId(), form.amountPaid(), mode,
                    form.purchaseDate(), userId);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BeejDtos.SeedPurchaseResponse getPurchase(Long shopId, Long id) {
        return toResponse(requirePurchase(shopId, id));
    }

    @Transactional(readOnly = true)
    public List<BeejDtos.SeedPurchaseSummaryResponse> getPurchases(Long shopId, LocalDate from, LocalDate to,
            Long supplierId, Long seedTypeId, SeedPurchase.PaymentStatus paymentStatus) {
        return seedPurchaseRepository.search(shopId, from, to, supplierId, seedTypeId, paymentStatus).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePurchase(Long shopId, Long id) {
        SeedPurchase purchase = requirePurchase(shopId, id);

        supplierService.removePaymentsForPurchase(shopId, purchase.getId());
        seedStockService.reversePurchase(shopId, purchase.getSeedType().getId(), purchase.getQtyKg(),
                purchase.getRatePerKg(), purchase.getId());

        if (purchase.getBillFilePath() != null) {
            deleteFileAfterCommit(Paths.get(purchase.getBillFilePath()));
        }
        seedPurchaseRepository.delete(purchase);
    }

    // ==================== Bill file ====================

    public record BillDownload(Resource resource, String fileName, String contentType) {}

    @Transactional(readOnly = true)
    public BillDownload getBill(Long shopId, Long id) {
        SeedPurchase purchase = requirePurchase(shopId, id);
        if (purchase.getBillFilePath() == null) {
            throw new DomainException(404, "This purchase has no bill attached");
        }
        Path path = resolveInsideUploadRoot(Paths.get(purchase.getBillFilePath()));
        if (!Files.isReadable(path)) {
            throw new DomainException(404, "Bill file is missing");
        }
        return new BillDownload(new PathResource(path), purchase.getBillFileName(), purchase.getBillFileType());
    }

    private record StoredBill(Path path, String originalName, long size, String contentType) {}

    private StoredBill storeBill(Long shopId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        long maxBytes = props.getStorage().getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new DomainException(413, "Bill file must be at most " + props.getStorage().getMaxFileSizeMb() + " MB");
        }

        String original = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(original);
        String contentType = BILL_TYPES.get(ext);
        if (contentType == null) {
            throw new DomainException(400, "Bill must be a PDF, JPG, PNG or WEBP file");
        }

        Path dir = uploadRoot().resolve("seed-purchases").resolve(shopId.toString()).normalize();
        Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store seed purchase bill", e);
            throw new DomainException(500, "Could not save the bill file");
        }
        return new StoredBill(target, original, file.getSize(), contentType);
    }

    private Path uploadRoot() {
        return Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    private Path resolveInsideUploadRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(uploadRoot())) {
            throw new DomainException(404, "Bill file is missing");
        }
        return normalized;
    }

    private static String sanitizeName(String name) {
        String base = name == null ? "bill" : Paths.get(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return cleaned.isBlank() ? "bill" : cleaned.substring(0, Math.min(cleaned.length(), 200));
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteFileOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(path);
                }
            }
        });
    }

    private void deleteFileAfterCommit(Path path) {
        if (path == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(path);
            }
        });
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete bill file {}", path, e);
        }
    }

    // ==================== Helpers ====================

    private static SeedPurchase.PaymentStatus paymentStatus(BigDecimal amountPaid, BigDecimal totalAmount) {
        if (amountPaid.compareTo(totalAmount) >= 0) {
            return SeedPurchase.PaymentStatus.PAID;
        }
        if (amountPaid.signum() > 0) {
            return SeedPurchase.PaymentStatus.PARTIAL;
        }
        return SeedPurchase.PaymentStatus.UNPAID;
    }

    private static void applyBill(SeedPurchase purchase, StoredBill bill) {
        purchase.setBillFileName(bill.originalName());
        purchase.setBillFilePath(bill.path().toString());
        purchase.setBillFileSize(bill.size());
        purchase.setBillFileType(bill.contentType());
    }

    private static void clearBill(SeedPurchase purchase) {
        purchase.setBillFileName(null);
        purchase.setBillFilePath(null);
        purchase.setBillFileSize(null);
        purchase.setBillFileType(null);
    }

    private SeedPurchase requirePurchase(Long shopId, Long id) {
        return seedPurchaseRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new DomainException(404, "Seed purchase not found"));
    }

    private SeedType requireActiveSeedType(Long id) {
        SeedType seedType = seedTypeRepository.findById(id)
                .orElseThrow(() -> new DomainException(400, "Seed type not found"));
        if (!seedType.isActive()) {
            throw new DomainException(400, "Seed type is not active");
        }
        return seedType;
    }

    private Supplier requireActiveSupplier(Long shopId, Long id) {
        Supplier supplier = supplierRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new DomainException(400, "Supplier not found"));
        if (!supplier.isActive()) {
            throw new DomainException(400, "Supplier is not active");
        }
        return supplier;
    }

    private BeejDtos.SeedPurchaseResponse toResponse(SeedPurchase p) {
        User createdBy = userService.findById(p.getCreatedBy()).orElse(null);
        return new BeejDtos.SeedPurchaseResponse(
                p.getId(),
                p.getSeedType().getId(),
                p.getSeedType().getNameEn(),
                p.getSupplier().getId(),
                p.getSupplier().getName(),
                p.getQtyKg(),
                p.getRatePerKg(),
                p.getTotalAmount(),
                p.getMoisturePercent(),
                p.getGrade().name(),
                p.getPurchaseDate(),
                p.getBillFilePath() != null,
                p.getBillFileName(),
                p.getPaymentStatus().name(),
                p.getAmountPaid(),
                p.getCreatedBy(),
                createdBy != null ? createdBy.getName() : "",
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
        );
    }

    private BeejDtos.SeedPurchaseSummaryResponse toSummary(SeedPurchase p) {
        return new BeejDtos.SeedPurchaseSummaryResponse(
                p.getId(),
                p.getSeedType().getId(),
                p.getSeedType().getNameEn(),
                p.getSupplier().getId(),
                p.getSupplier().getName(),
                p.getQtyKg(),
                p.getRatePerKg(),
                p.getTotalAmount(),
                p.getGrade().name(),
                p.getPurchaseDate(),
                p.getPaymentStatus().name(),
                p.getAmountPaid(),
                p.getBillFilePath() != null
        );
    }
}
