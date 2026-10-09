package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.*;
import com.ghana.dashboard.dto.PaymentDtos;
import com.ghana.dashboard.repository.CashBookDayRepository;
import com.ghana.dashboard.repository.PaymentRepository;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CashBookDayService {

    private final CashBookDayRepository cashBookDayRepository;
    private final PaymentRepository paymentRepository;
    private final ShopService shopService;
    private final UserService userService;

    public CashBookDayService(CashBookDayRepository cashBookDayRepository,
                              PaymentRepository paymentRepository,
                              ShopService shopService,
                              UserService userService) {
        this.cashBookDayRepository = cashBookDayRepository;
        this.paymentRepository = paymentRepository;
        this.shopService = shopService;
        this.userService = userService;
    }

    // ==================== Cash Book Day Management ====================

    @Transactional
    public CashBookDay getOrCreateCashBookDay(Long shopId, LocalDate date) {
        return cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .orElseGet(() -> {
                    CashBookDay cashBookDay = new CashBookDay();
                    cashBookDay.setShopId(shopId);
                    cashBookDay.setDayDate(date);
                    cashBookDay.setStatus(CashBookDay.Status.OPEN);
                    cashBookDay.setOpeningBalance(BigDecimal.ZERO);
                    cashBookDay.setTotalIn(BigDecimal.ZERO);
                    cashBookDay.setTotalOut(BigDecimal.ZERO);
                    cashBookDay.setCreatedAt(LocalDateTime.now());
                    cashBookDay.setUpdatedAt(LocalDateTime.now());
                    return cashBookDayRepository.save(cashBookDay);
                });
    }

    @Transactional
    public void recordPaymentForDay(Long shopId, Payment payment) {
        CashBookDay cashBookDay = getOrCreateCashBookDay(shopId, payment.getPaymentDate());
        
        if (cashBookDay.isClosed()) {
            // Reopen if needed (shouldn't happen for auto-payments, but manual payments check before)
            // This is handled separately
            return;
        }

        if (com.ghana.dashboard.domain.Payment.Direction.IN.equals(payment.getDirection())) {
            cashBookDay.setTotalIn(cashBookDay.getTotalIn().add(payment.getAmount()));
        } else {
            cashBookDay.setTotalOut(cashBookDay.getTotalOut().add(payment.getAmount()));
        }
        
        cashBookDay.setUpdatedAt(LocalDateTime.now());
        cashBookDayRepository.save(cashBookDay);
    }

    @Transactional
    public void removePaymentFromDay(Long shopId, Payment payment) {
        CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, payment.getPaymentDate())
                .orElse(null);
        if (cashBookDay == null || cashBookDay.isClosed()) {
            return;
        }

        if (Payment.Direction.IN.equals(payment.getDirection())) {
            cashBookDay.setTotalIn(cashBookDay.getTotalIn().subtract(payment.getAmount()));
        } else {
            cashBookDay.setTotalOut(cashBookDay.getTotalOut().subtract(payment.getAmount()));
        }
        cashBookDayRepository.save(cashBookDay);
    }

    @Transactional(readOnly = true)
    public boolean isDayClosed(Long shopId, LocalDate date) {
        return cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .map(CashBookDay::isClosed)
                .orElse(false);
    }

    public void verifyDayNotClosed(Long shopId, LocalDate date, Long userId) {
        CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .orElse(null);
        
        if (cashBookDay != null && cashBookDay.isClosed()) {
            User user = userService.getById(userId);
            if (!Role.OWNER.equals(user.getRole())) {
                throw new DomainException(403, 
                        "Day " + date + " is closed. Only OWNER can reopen.");
            }
        }
    }

    // ==================== Close Day ====================

    @Transactional
    public PaymentDtos.CashBookDayResponse closeDay(Long shopId, LocalDate date, 
            PaymentDtos.CloseCashBookRequest request, Long userId) {
        
        CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .orElseThrow(() -> new DomainException(404, "Cash book day not found for date: " + date));

        if (cashBookDay.isClosed()) {
            throw new DomainException(400, "Day is already closed");
        }

        // Calculate actual totals from payments
        BigDecimal totalIn = calculateTotalIn(shopId, date);
        BigDecimal totalOut = calculateTotalOut(shopId, date);
        BigDecimal closingBalance = cashBookDay.getOpeningBalance()
                .add(totalIn)
                .subtract(totalOut);

        cashBookDay.setTotalIn(totalIn);
        cashBookDay.setTotalOut(totalOut);
        cashBookDay.setClosingBalance(request.closingBalance() != null 
                ? request.closingBalance() : closingBalance);
        cashBookDay.setStatus(CashBookDay.Status.CLOSED);
        cashBookDay.setClosedBy(userId);
        cashBookDay.setClosedAt(LocalDateTime.now());
        cashBookDay.setNotes(request.notes());
        cashBookDay.setUpdatedAt(LocalDateTime.now());

        CashBookDay saved = cashBookDayRepository.save(cashBookDay);
        
        return toCashBookDayResponse(saved, userId);
    }

    private BigDecimal calculateTotalIn(Long shopId, LocalDate date) {
        return paymentRepository.findByShopIdAndPaymentDateAndDirection(shopId, date, com.ghana.dashboard.domain.Payment.Direction.IN)
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalOut(Long shopId, LocalDate date) {
        return paymentRepository.findByShopIdAndPaymentDateAndDirection(shopId, date, com.ghana.dashboard.domain.Payment.Direction.OUT)
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== Reopen Day ====================

    @Transactional
    public PaymentDtos.CashBookDayResponse reopenDay(Long shopId, LocalDate date,
            PaymentDtos.ReopenCashBookRequest request, Long userId) {
        
        CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .orElseThrow(() -> new DomainException(404, "Cash book day not found for date: " + date));

        User user = userService.getById(userId);
        if (!Role.OWNER.equals(user.getRole())) {
            throw new DomainException(403, "Only OWNER can reopen a closed day");
        }

        cashBookDay.setStatus(CashBookDay.Status.REOPENED);
        cashBookDay.setReopenedBy(userId);
        cashBookDay.setReopenedAt(LocalDateTime.now());
        cashBookDay.setNotes((cashBookDay.getNotes() != null ? cashBookDay.getNotes() + " \n" : "") + 
                "Reopened: " + request.reason());
        cashBookDay.setUpdatedAt(LocalDateTime.now());

        CashBookDay saved = cashBookDayRepository.save(cashBookDay);
        
        return toCashBookDayResponse(saved, userId);
    }

    // ==================== Get Cash Book Day ====================

    @Transactional(readOnly = true)
    public PaymentDtos.CashBookDayResponse getCashBookDay(Long shopId, LocalDate date, Long userId) {
        CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                .orElseGet(() -> {
                    CashBookDay newDay = new CashBookDay();
                    newDay.setShopId(shopId);
                    newDay.setDayDate(date);
                    newDay.setStatus(CashBookDay.Status.OPEN);
                    return newDay;
                });

        return toCashBookDayResponse(cashBookDay, userId);
    }

    // ==================== Get Cash Book Entries ====================

    @Transactional(readOnly = true)
    public List<PaymentDtos.CashBookEntryResponse> getCashBookEntries(Long shopId, LocalDate date) {
        List<Payment> payments = paymentRepository.findByShopIdAndPaymentDateOrderByCreatedAtDesc(
                shopId, date);

        return payments.stream()
                .map(this::toCashBookEntryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentDtos.CashBookSummaryResponse getCashBookSummary(Long shopId, LocalDate date) {
        List<Payment> inPayments = paymentRepository.findByShopIdAndPaymentDateAndDirection(
                shopId, date, com.ghana.dashboard.domain.Payment.Direction.IN);
        
        BigDecimal cashIn = inPayments.stream()
                .filter(p -> com.ghana.dashboard.domain.Payment.PaymentMode.CASH.equals(p.getMode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal upiIn = inPayments.stream()
                .filter(p -> com.ghana.dashboard.domain.Payment.PaymentMode.UPI.equals(p.getMode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal cardIn = inPayments.stream()
                .filter(p -> com.ghana.dashboard.domain.Payment.PaymentMode.CARD.equals(p.getMode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal bankIn = inPayments.stream()
                .filter(p -> com.ghana.dashboard.domain.Payment.PaymentMode.BANK.equals(p.getMode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalIn = cashIn.add(upiIn).add(cardIn).add(bankIn);
        
        BigDecimal totalOut = paymentRepository.findByShopIdAndPaymentDateAndDirection(
                        shopId, date, com.ghana.dashboard.domain.Payment.Direction.OUT)
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal netBalance = totalIn.subtract(totalOut);

        return new PaymentDtos.CashBookSummaryResponse(
                cashIn, upiIn, cardIn, bankIn, totalIn, totalOut, netBalance, date);
    }

    // ==================== PDF Report ====================

    @Transactional(readOnly = true)
    public byte[] generateCashBookReportPdf(Long shopId, LocalDate date) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Shop shop = shopService.findById(shopId);
            CashBookDay cashBookDay = cashBookDayRepository.findByShopIdAndDayDate(shopId, date)
                    .orElseThrow(() -> new DomainException(404, "Cash book day not found"));
            PaymentDtos.CashBookSummaryResponse summary = getCashBookSummary(shopId, date);
            List<PaymentDtos.CashBookEntryResponse> entries = getCashBookEntries(shopId, date);

            // Header
            Font shopNameFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Paragraph shopName = new Paragraph(shop.getName(), shopNameFont);
            shopName.setAlignment(Element.ALIGN_CENTER);
            document.add(shopName);

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph("Cash Book - " + date, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Summary
            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{1, 1, 1, 1});

            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font valueFont = new Font(Font.HELVETICA, 10);

            addTableCell(summaryTable, "Opening Balance:", headerFont);
            addTableCell(summaryTable, formatCurrency(cashBookDay.getOpeningBalance()), valueFont);
            addTableCell(summaryTable, "Status:", headerFont);
            addTableCell(summaryTable, cashBookDay.getStatus().name(), valueFont);

            addTableCell(summaryTable, "Cash IN:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.cashIn()), valueFont);
            addTableCell(summaryTable, "UPI IN:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.upiIn()), valueFont);

            addTableCell(summaryTable, "Card IN:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.cardIn()), valueFont);
            addTableCell(summaryTable, "Bank IN:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.bankIn()), valueFont);

            addTableCell(summaryTable, "Total IN:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.totalIn()), valueFont);
            addTableCell(summaryTable, "Total OUT:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.totalOut()), valueFont);

            addTableCell(summaryTable, "Closing Balance:", headerFont);
            addTableCell(summaryTable, formatCurrency(cashBookDay.getClosingBalance()), valueFont);
            addTableCell(summaryTable, "Net Balance:", headerFont);
            addTableCell(summaryTable, formatCurrency(summary.netBalance()), valueFont);

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // Entries Table
            PdfPTable entriesTable = new PdfPTable(5);
            entriesTable.setWidthPercentage(100);
            entriesTable.setWidths(new float[]{1, 1, 1, 1, 1});

            addTableCell(entriesTable, "Time", headerFont);
            addTableCell(entriesTable, "Direction", headerFont);
            addTableCell(entriesTable, "Mode", headerFont);
            addTableCell(entriesTable, "Amount", headerFont);
            addTableCell(entriesTable, "Description", headerFont);

            Font itemFont = new Font(Font.HELVETICA, 9);
            for (PaymentDtos.CashBookEntryResponse entry : entries) {
                addTableCell(entriesTable, entry.createdAt(), itemFont);
                addTableCell(entriesTable, entry.direction().name(), itemFont);
                addTableCell(entriesTable, entry.mode().name(), itemFont);
                addTableCell(entriesTable, formatCurrency(entry.amount()), itemFont);
                addTableCell(entriesTable, entry.description(), itemFont);
            }

            document.add(entriesTable);
            document.add(new Paragraph(" "));

            // Footer
            Font footerFont = new Font(Font.HELVETICA, 8);
            Paragraph generated = new Paragraph("Generated on: " + LocalDateTime.now(), footerFont);
            generated.setAlignment(Element.ALIGN_CENTER);
            document.add(generated);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        return "Rs. " + amount.setScale(2).toString();
    }

    // ==================== Helper Methods ====================

    private PaymentDtos.CashBookDayResponse toCashBookDayResponse(CashBookDay cashBookDay) {
        return toCashBookDayResponse(cashBookDay, null);
    }

    private PaymentDtos.CashBookDayResponse toCashBookDayResponse(CashBookDay cashBookDay, Long userId) {
        User closedByUser = cashBookDay.getClosedBy() != null ? userService.getById(cashBookDay.getClosedBy()) : null;
        User reopenedByUser = cashBookDay.getReopenedBy() != null ? userService.getById(cashBookDay.getReopenedBy()) : null;
        
        if (userId == null) {
            userId = 0L;
        }
        User currentUser = userService.findById(userId)
                .orElse(null);
        boolean canEdit = !cashBookDay.isClosed() || 
                (cashBookDay.isClosed() && currentUser != null && Role.OWNER.equals(currentUser.getRole()));

        return new PaymentDtos.CashBookDayResponse(
                cashBookDay.getId(),
                cashBookDay.getShopId(),
                cashBookDay.getDayDate(),
                cashBookDay.getStatus(),
                cashBookDay.getOpeningBalance(),
                cashBookDay.getTotalIn(),
                cashBookDay.getTotalOut(),
                cashBookDay.getClosingBalance(),
                cashBookDay.getClosedBy(),
                closedByUser != null ? closedByUser.getName() : null,
                cashBookDay.getClosedAt() != null ? cashBookDay.getClosedAt().toString() : null,
                cashBookDay.getReopenedBy(),
                reopenedByUser != null ? reopenedByUser.getName() : null,
                cashBookDay.getReopenedAt() != null ? cashBookDay.getReopenedAt().toString() : null,
                cashBookDay.getNotes(),
                canEdit
        );
    }

    private PaymentDtos.CashBookEntryResponse toCashBookEntryResponse(Payment payment) {
        User createdBy = userService.getById(payment.getCreatedBy());
        String description = buildDescription(payment);
        
        return new PaymentDtos.CashBookEntryResponse(
                payment.getId(),
                payment.getDirection(),
                payment.getMode(),
                payment.getAmount(),
                payment.getSourceType(),
                payment.getSourceId(),
                payment.getReferenceNo(),
                description,
                payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : "",
                createdBy != null ? createdBy.getName() : ""
        );
    }

    private String buildDescription(Payment payment) {
        StringBuilder sb = new StringBuilder();
        sb.append(payment.getNote() != null ? payment.getNote() : "");
        if (payment.getReferenceNo() != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(payment.getReferenceNo());
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public CashBookDay findById(Long id) {
        return cashBookDayRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Cash book day not found"));
    }
}
