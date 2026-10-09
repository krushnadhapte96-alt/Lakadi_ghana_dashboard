package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.config.AppProperties;
import com.ghana.dashboard.domain.Expense;
import com.ghana.dashboard.domain.ExpenseCategory;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.repository.ExpenseCategoryRepository;
import com.ghana.dashboard.repository.ExpenseRepository;
import com.ghana.dashboard.repository.PaymentRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private static final Map<String, String> BILL_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final PaymentRepository paymentRepository;
    private final CashBookDayService cashBookDayService;
    private final UserService userService;
    private final AppProperties props;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseCategoryRepository categoryRepository,
                          PaymentRepository paymentRepository,
                          CashBookDayService cashBookDayService,
                          UserService userService,
                          AppProperties props) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.paymentRepository = paymentRepository;
        this.cashBookDayService = cashBookDayService;
        this.userService = userService;
        this.props = props;
    }

    // ==================== Categories ====================

    @Transactional(readOnly = true)
    public List<ExpenseDtos.ExpenseCategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    // ==================== Expense CRUD ====================

    @Transactional
    public ExpenseDtos.ExpenseResponse createExpense(Long shopId, Long userId, ExpenseDtos.ExpenseForm form) {
        ExpenseCategory category = requireActiveCategory(form.categoryId());
        assertDayOpen(shopId, form.expenseDate());

        Expense expense = new Expense();
        expense.setShopId(shopId);
        expense.setCreatedBy(userId);
        apply(expense, form, category);

        StoredBill bill = storeBill(shopId, form.billFile());
        if (bill != null) {
            applyBill(expense, bill);
            deleteFileOnRollback(bill.path());
        }

        Expense saved = expenseRepository.save(expense);
        createOutPayment(saved, userId);
        return toResponse(saved, category);
    }

    @Transactional
    public ExpenseDtos.ExpenseResponse updateExpense(Long shopId, Long userId, Long id,
                                                     ExpenseDtos.ExpenseForm form) {
        Expense expense = requireExpense(shopId, id);
        ExpenseCategory category = requireActiveCategory(form.categoryId());
        assertDayOpen(shopId, expense.getExpenseDate());
        assertDayOpen(shopId, form.expenseDate());

        Path oldBill = expense.getBillFilePath() != null ? Paths.get(expense.getBillFilePath()) : null;
        apply(expense, form, category);

        StoredBill bill = storeBill(shopId, form.billFile());
        if (bill != null) {
            applyBill(expense, bill);
            deleteFileOnRollback(bill.path());
            deleteFileAfterCommit(oldBill);
        } else if (Boolean.TRUE.equals(form.removeBill()) && oldBill != null) {
            clearBill(expense);
            deleteFileAfterCommit(oldBill);
        }

        Expense saved = expenseRepository.save(expense);
        removeOutPayment(saved);
        createOutPayment(saved, userId);
        return toResponse(saved, category);
    }

    @Transactional(readOnly = true)
    public ExpenseDtos.ExpenseResponse getExpense(Long shopId, Long id) {
        Expense expense = requireExpense(shopId, id);
        return toResponse(expense, categoryRepository.findById(expense.getCategoryId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ExpenseDtos.ExpenseSummaryResponse> getExpenses(Long shopId, LocalDate from, LocalDate to,
                                                                Long categoryId, Payment.PaymentMode mode) {
        Map<Long, ExpenseCategory> categories = categoriesById();
        return expenseRepository.search(shopId, from, to, categoryId, mode != null ? mode.name() : null)
                .stream()
                .map(e -> toSummary(e, categories.get(e.getCategoryId())))
                .toList();
    }

    @Transactional
    public void deleteExpense(Long shopId, Long id) {
        Expense expense = requireExpense(shopId, id);
        assertDayOpen(shopId, expense.getExpenseDate());

        removeOutPayment(expense);
        if (expense.getBillFilePath() != null) {
            deleteFileAfterCommit(Paths.get(expense.getBillFilePath()));
        }
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDtos.ExpenseBreakdownResponse> getBreakdownByCategory(Long shopId, LocalDate from, LocalDate to) {
        Map<Long, ExpenseCategory> categories = categoriesById();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Expense e : expenseRepository.findByShopIdAndExpenseDateBetween(shopId, from, to)) {
            totals.merge(e.getCategoryId(), e.getAmount(), BigDecimal::add);
            counts.merge(e.getCategoryId(), 1, Integer::sum);
        }

        List<ExpenseDtos.ExpenseBreakdownResponse> result = new ArrayList<>();
        totals.forEach((categoryId, amount) -> {
            ExpenseCategory c = categories.get(categoryId);
            result.add(new ExpenseDtos.ExpenseBreakdownResponse(
                    categoryId,
                    c != null ? c.getNameEn() : "Unknown",
                    c != null ? c.getColour() : null,
                    amount,
                    counts.get(categoryId)));
        });
        result.sort((a, b) -> b.amount().compareTo(a.amount()));
        return result;
    }

    // ==================== Bill file ====================

    public record BillDownload(Resource resource, String fileName, String contentType) {}

    @Transactional(readOnly = true)
    public BillDownload getBill(Long shopId, Long id) {
        Expense expense = requireExpense(shopId, id);
        if (expense.getBillFilePath() == null) {
            throw new DomainException(404, "This expense has no bill attached");
        }
        Path path = resolveInsideUploadRoot(Paths.get(expense.getBillFilePath()));
        if (!Files.isReadable(path)) {
            throw new DomainException(404, "Bill file is missing");
        }
        return new BillDownload(new PathResource(path), expense.getBillFileName(), expense.getBillFileType());
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

        Path dir = uploadRoot().resolve("expenses").resolve(shopId.toString()).normalize();
        Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store expense bill", e);
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

    // ==================== Payment (cash book) side effects ====================

    private void createOutPayment(Expense expense, Long userId) {
        Payment payment = new Payment();
        payment.setShopId(expense.getShopId());
        payment.setDirection(Payment.Direction.OUT);
        payment.setMode(Payment.PaymentMode.valueOf(expense.getPaymentMode()));
        payment.setAmount(expense.getAmount());
        payment.setSourceType(Payment.SourceType.EXPENSE);
        payment.setSourceId(expense.getId());
        payment.setReferenceNo(expense.getReferenceNo());
        payment.setPaymentDate(expense.getExpenseDate());
        payment.setNote("Expense: " + expense.getTitle());
        payment.setCreatedBy(userId);
        Payment saved = paymentRepository.save(payment);
        cashBookDayService.recordPaymentForDay(expense.getShopId(), saved);
    }

    private void removeOutPayment(Expense expense) {
        List<Payment> payments = paymentRepository.findByShopIdAndSourceTypeAndSourceId(
                expense.getShopId(), Payment.SourceType.EXPENSE, expense.getId());
        for (Payment payment : payments) {
            cashBookDayService.removePaymentFromDay(expense.getShopId(), payment);
            paymentRepository.delete(payment);
        }
    }

    private void assertDayOpen(Long shopId, LocalDate date) {
        if (cashBookDayService.isDayClosed(shopId, date)) {
            throw new DomainException(409, "Cash book for " + date + " is closed. Reopen it before changing expenses on that day.");
        }
    }

    // ==================== Mapping helpers ====================

    private void apply(Expense expense, ExpenseDtos.ExpenseForm form, ExpenseCategory category) {
        expense.setCategoryId(category.getId());
        expense.setAmount(form.amount());
        expense.setTitle(form.title().trim());
        expense.setDescription(form.description());
        expense.setExpenseDate(form.expenseDate());
        expense.setPaymentMode(form.paymentMode().name());
        expense.setReferenceNo(blankToNull(form.referenceNo()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void applyBill(Expense expense, StoredBill bill) {
        expense.setBillFileName(bill.originalName());
        expense.setBillFilePath(bill.path().toString());
        expense.setBillFileSize(bill.size());
        expense.setBillFileType(bill.contentType());
    }

    private static void clearBill(Expense expense) {
        expense.setBillFileName(null);
        expense.setBillFilePath(null);
        expense.setBillFileSize(null);
        expense.setBillFileType(null);
    }

    private Expense requireExpense(Long shopId, Long id) {
        return expenseRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new DomainException(404, "Expense not found"));
    }

    private ExpenseCategory requireActiveCategory(Long categoryId) {
        return categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new DomainException(400, "Expense category not found"));
    }

    private Map<Long, ExpenseCategory> categoriesById() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, Function.identity()));
    }

    private ExpenseDtos.ExpenseCategoryResponse toCategoryResponse(ExpenseCategory c) {
        return new ExpenseDtos.ExpenseCategoryResponse(c.getId(), c.getNameEn(), c.getNameHi(),
                c.getCategoryType(), c.getDescription(), c.getColour(), c.isActive(), c.getSortOrder());
    }

    private ExpenseDtos.ExpenseResponse toResponse(Expense e, ExpenseCategory category) {
        User createdBy = userService.findById(e.getCreatedBy()).orElse(null);
        return new ExpenseDtos.ExpenseResponse(
                e.getId(), e.getCategoryId(), category != null ? category.getNameEn() : "",
                e.getAmount(), e.getTitle(), e.getDescription(),
                e.getBillFileName(), e.getBillFileSize(), e.getBillFileType(), e.getBillFilePath() != null,
                e.getExpenseDate(), e.getPaymentMode(), e.getReferenceNo(),
                e.getCreatedBy(), createdBy != null ? createdBy.getName() : "",
                String.valueOf(e.getCreatedAt()));
    }

    private ExpenseDtos.ExpenseSummaryResponse toSummary(Expense e, ExpenseCategory category) {
        return new ExpenseDtos.ExpenseSummaryResponse(
                e.getId(), e.getCategoryId(),
                category != null ? category.getNameEn() : "",
                category != null ? category.getColour() : null,
                e.getAmount(), e.getTitle(), e.getExpenseDate(), e.getPaymentMode(),
                e.getReferenceNo(), e.getBillFilePath() != null, String.valueOf(e.getCreatedAt()));
    }
}
