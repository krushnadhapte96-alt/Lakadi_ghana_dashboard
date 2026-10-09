package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.dto.PaymentDtos;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.service.KhataLedgerService.EntryRow;
import com.ghana.dashboard.service.KhataLedgerService.PartyRow;
import com.ghana.dashboard.service.KhataLedgerService.Standing;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KhataService {

    private static final Set<String> TYPES = Set.of("RETAILER", "HOTEL", "RESTAURANT", "INDIVIDUAL");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final JdbcSupport db;
    private final KhataLedgerService ledger;
    private final CustomerService customerService;
    private final SaleService saleService;
    private final PaymentService paymentService;
    private final ShopService shopService;

    public KhataService(JdbcSupport db, KhataLedgerService ledger, CustomerService customerService,
                        SaleService saleService, PaymentService paymentService, ShopService shopService) {
        this.db = db;
        this.ledger = ledger;
        this.customerService = customerService;
        this.saleService = saleService;
        this.paymentService = paymentService;
        this.shopService = shopService;
    }

    // ==================== Parties ====================

    @Transactional(readOnly = true)
    public List<KhataDtos.PartyResponse> listParties(Long shopId, String type, Boolean overdueOnly,
                                                     Boolean withBalance, String query) {
        LocalDate today = DateDefaults.today();
        Map<Long, List<EntryRow>> entries = ledger.entriesByParty(shopId);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<KhataDtos.PartyResponse> out = new ArrayList<>();
        for (PartyRow party : ledger.listParties(shopId)) {
            if (!party.active()) {
                continue;
            }
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(party.type())) {
                continue;
            }
            if (!q.isEmpty() && !party.name().toLowerCase(Locale.ROOT).contains(q) && !party.phone().contains(q)) {
                continue;
            }
            Standing s = KhataLedgerService.standing(party, entries.getOrDefault(party.id(), List.of()), today);
            if (Boolean.TRUE.equals(overdueOnly) && s.overdue().signum() <= 0) {
                continue;
            }
            if (Boolean.TRUE.equals(withBalance) && s.balance().signum() <= 0) {
                continue;
            }
            out.add(KhataLedgerService.toResponse(party, s));
        }
        out.sort(Comparator.comparing(KhataDtos.PartyResponse::overdue).reversed()
                .thenComparing(KhataDtos.PartyResponse::balance, Comparator.reverseOrder()));
        return out;
    }

    @Transactional(readOnly = true)
    public KhataDtos.PartyResponse getParty(Long shopId, Long id) {
        PartyRow party = ledger.requireParty(shopId, id);
        return KhataLedgerService.toResponse(party, ledger.standingOf(party));
    }

    @Transactional
    public KhataDtos.PartyResponse createParty(Long shopId, KhataDtos.PartyRequest request) {
        String type = normalizeType(request.type());
        String phone = normalizePhone(request.phone());
        if (ledger.findPartyByPhone(shopId, phone).isPresent()) {
            throw new DomainException(409, "A khata party with this phone already exists");
        }
        Long customerId = customerFor(shopId, request.name(), phone);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", shopId);
        row.put("customer_id", customerId);
        row.put("name", request.name().trim());
        row.put("type", type);
        row.put("phone", phone);
        row.put("address", blankToNull(request.address()));
        row.put("credit_limit", request.creditLimit());
        row.put("payment_terms_days", request.paymentTermsDays() == null ? 30 : request.paymentTermsDays());
        row.put("opening_balance", request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance());
        row.put("active", true);
        row.put("created_at", Timestamp.valueOf(now));
        row.put("updated_at", Timestamp.valueOf(now));
        return getParty(shopId, db.insert("khata_parties", row));
    }

    @Transactional
    public KhataDtos.PartyResponse updateParty(Long shopId, Long id, KhataDtos.PartyRequest request) {
        PartyRow existing = ledger.requireParty(shopId, id);
        String phone = normalizePhone(request.phone());
        if (!phone.equals(existing.phone()) && ledger.findPartyByPhone(shopId, phone).isPresent()) {
            throw new DomainException(409, "Another khata party already uses this phone");
        }
        db.jdbc().update("""
                UPDATE khata_parties SET name = :name, type = :type, phone = :phone, address = :address,
                credit_limit = :limit, payment_terms_days = :terms, opening_balance = :opening, updated_at = :now
                WHERE id = :id AND shop_id = :shop""", new MapSqlParameterSource()
                .addValue("name", request.name().trim()).addValue("type", normalizeType(request.type()))
                .addValue("phone", phone).addValue("address", blankToNull(request.address()))
                .addValue("limit", request.creditLimit())
                .addValue("terms", request.paymentTermsDays() == null ? existing.termsDays() : request.paymentTermsDays())
                .addValue("opening", request.openingBalance() == null ? existing.openingBalance() : request.openingBalance())
                .addValue("now", Timestamp.valueOf(LocalDateTime.now())).addValue("id", id).addValue("shop", shopId));
        return getParty(shopId, id);
    }

    @Transactional
    public void deleteParty(Long shopId, Long id) {
        PartyRow party = ledger.requireParty(shopId, id);
        if (ledger.standingOf(party).balance().signum() != 0) {
            throw new DomainException(409, "Settle the outstanding balance before removing this party");
        }
        db.jdbc().update("UPDATE khata_parties SET active = 0, updated_at = :now WHERE id = :id",
                Map.of("now", Timestamp.valueOf(LocalDateTime.now()), "id", id));
    }

    // ==================== Ledger ====================

    @Transactional(readOnly = true)
    public KhataDtos.LedgerResponse ledger(Long shopId, Long partyId) {
        PartyRow party = ledger.requireParty(shopId, partyId);
        List<EntryRow> rows = ledger.entriesOf(partyId);
        Standing standing = KhataLedgerService.standing(party, rows, DateDefaults.today());

        Map<Long, List<KhataDtos.EntryItem>> items = new HashMap<>();
        db.jdbc().query("""
                SELECT i.entry_id, i.product_id, i.product_name, i.qty, i.unit, i.rate, i.amount
                FROM khata_entry_items i JOIN khata_entries e ON e.id = i.entry_id
                WHERE e.party_id = :party ORDER BY i.id""", Map.of("party", partyId), rs -> {
            items.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>()).add(new KhataDtos.EntryItem(
                    rs.getLong("product_id"), rs.getString("product_name"), rs.getBigDecimal("qty"),
                    rs.getString("unit"), rs.getBigDecimal("rate"), rs.getBigDecimal("amount")));
        });
        Map<Long, Map<String, Object>> meta = new HashMap<>();
        db.jdbc().queryForList("""
                SELECT id, bill_no, source_type, source_id, payment_mode, note FROM khata_entries
                WHERE party_id = :party""", Map.of("party", partyId)).forEach(m -> meta.put(((Number) m.get("id")).longValue(), m));

        List<KhataDtos.LedgerEntry> entries = new ArrayList<>();
        BigDecimal running = party.openingBalance();
        if (party.openingBalance().signum() != 0) {
            entries.add(new KhataDtos.LedgerEntry(null, "OPENING", party.createdAt().toLocalDate(), null,
                    party.openingBalance().abs(), party.openingBalance(), running, null, null, null, null,
                    "Opening balance", List.of()));
        }
        for (EntryRow row : rows) {
            running = running.add(row.delta());
            Map<String, Object> m = meta.get(row.id());
            entries.add(new KhataDtos.LedgerEntry(row.id(), row.type(), row.entryDate(), row.dueDate(), row.amount(),
                    row.delta(), running, (String) m.get("bill_no"), (String) m.get("source_type"),
                    m.get("source_id") == null ? null : ((Number) m.get("source_id")).longValue(),
                    (String) m.get("payment_mode"), (String) m.get("note"), items.getOrDefault(row.id(), List.of())));
        }
        return new KhataDtos.LedgerResponse(KhataLedgerService.toResponse(party, standing), entries);
    }

    // ==================== Credit sale / payment / adjustment ====================

    /**
     * A credit sale is a real UDHAAR sale: the sale service deducts stock FIFO, books the revenue and
     * publishes {@code SaleOnCreditEvent}, which the ledger turns into the CREDIT_SALE entry.
     */
    @Transactional
    public KhataDtos.LedgerResponse creditSale(Long shopId, Long userId, Long partyId, KhataDtos.CreditSaleRequest request) {
        PartyRow party = ledger.requireParty(shopId, partyId);
        Long customerId = party.customerId() != null ? party.customerId() : customerFor(shopId, party.name(), party.phone());
        List<SalesDtos.SaleItemRequest> items = request.items().stream()
                .map(i -> new SalesDtos.SaleItemRequest(i.productId(), i.qty(), i.rate())).toList();
        SalesDtos.SaleRequest sale = new SalesDtos.SaleRequest(customerId, party.phone(), items, BigDecimal.ZERO,
                "AMOUNT", "UDHAAR", BigDecimal.ZERO, null, request.note(), request.overrideCreditLimit());
        SalesDtos.SaleResponse created = saleService.createSale(shopId, sale, userId);
        if (request.dueDate() != null) {
            db.jdbc().update("UPDATE khata_entries SET due_date = :due WHERE source_type = 'SALE' AND source_id = :id",
                    Map.of("due", java.sql.Date.valueOf(request.dueDate()), "id", created.id()));
        }
        return ledger(shopId, partyId);
    }

    @Transactional
    public KhataDtos.LedgerResponse receivePayment(Long shopId, Long userId, Long partyId, KhataDtos.PaymentRequest request) {
        PartyRow party = ledger.requireParty(shopId, partyId);
        BigDecimal balance = ledger.standingOf(party).balance();
        if (balance.signum() <= 0) {
            throw new DomainException(409, "This party has nothing outstanding");
        }
        if (request.amount().compareTo(balance) > 0) {
            throw new DomainException(400, "Payment of Rs. " + request.amount() + " is more than the outstanding Rs. " + balance);
        }
        Payment.PaymentMode mode = parseMode(request.mode());
        LocalDate date = request.date() == null ? DateDefaults.today() : request.date();
        PaymentDtos.PaymentResponse payment = paymentService.createPayment(shopId, new PaymentDtos.PaymentRequest(
                Payment.Direction.IN, mode, request.amount(), Payment.SourceType.KHATA, partyId, request.referenceNo(),
                date, request.note() == null ? "Khata payment from " + party.name() : request.note()), userId);
        ledger.insertEntry(shopId, partyId, "PAYMENT_RECEIVED", request.amount(), request.amount().negate(), date, null,
                "PAYMENT", payment.id(), request.referenceNo(), payment.id(), mode.name(), request.note(), userId);
        ledger.syncSaleStatuses(party);
        return ledger(shopId, partyId);
    }

    @Transactional
    public KhataDtos.LedgerResponse adjust(Long shopId, Long userId, Long partyId, KhataDtos.AdjustmentRequest request) {
        PartyRow party = ledger.requireParty(shopId, partyId);
        if (request.amount().signum() == 0) {
            throw new DomainException(400, "Adjustment amount cannot be zero");
        }
        ledger.insertEntry(shopId, partyId, "ADJUSTMENT", request.amount().abs(), request.amount(),
                DateDefaults.today(), null, "MANUAL", null, null, null, null, request.note(), userId);
        ledger.syncSaleStatuses(party);
        return ledger(shopId, partyId);
    }

    // ==================== Summary ====================

    @Transactional(readOnly = true)
    public KhataDtos.SummaryResponse summary(Long shopId) {
        List<KhataDtos.PartyResponse> parties = listParties(shopId, null, null, null, null);
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int withBalance = 0;
        int overdueParties = 0;
        for (KhataDtos.PartyResponse p : parties) {
            if (p.balance().signum() > 0) {
                outstanding = outstanding.add(p.balance());
                withBalance++;
            }
            if (p.overdue()) {
                overdue = overdue.add(p.overdueAmount());
                overdueParties++;
            }
        }
        LocalDate monthStart = DateDefaults.monthStart();
        Map<String, Object> params = Map.of("shop", shopId, "from", java.sql.Date.valueOf(monthStart));
        BigDecimal collected = db.jdbc().queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM khata_entries
                WHERE shop_id = :shop AND entry_type = 'PAYMENT_RECEIVED' AND entry_date >= :from""", params, BigDecimal.class);
        BigDecimal credit = db.jdbc().queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM khata_entries
                WHERE shop_id = :shop AND entry_type = 'CREDIT_SALE' AND entry_date >= :from""", params, BigDecimal.class);
        List<KhataDtos.PartyResponse> top = parties.stream().filter(p -> p.balance().signum() > 0)
                .sorted(Comparator.comparing(KhataDtos.PartyResponse::balance).reversed()).limit(5).toList();
        return new KhataDtos.SummaryResponse(outstanding, overdue, withBalance, overdueParties, collected, credit, top);
    }

    // ==================== Statement PDF ====================

    @Transactional(readOnly = true)
    public byte[] statementPdf(Long shopId, Long partyId) {
        KhataDtos.LedgerResponse data = ledger(shopId, partyId);
        KhataDtos.PartyResponse party = data.party();
        String shopName = shopService.findById(shopId).getName();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font title = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 10);

            Paragraph heading = new Paragraph(shopName, title);
            heading.setAlignment(Element.ALIGN_CENTER);
            doc.add(heading);
            Paragraph sub = new Paragraph("Khata Statement", new Font(Font.HELVETICA, 13, Font.BOLD));
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);

            doc.add(new Paragraph("Party: " + party.name() + " (" + party.type() + ")", bold));
            doc.add(new Paragraph("Phone: " + party.phone(), normal));
            if (party.address() != null) {
                doc.add(new Paragraph("Address: " + party.address(), normal));
            }
            doc.add(new Paragraph("Statement date: " + DateDefaults.today().format(DAY), normal));
            Paragraph balance = new Paragraph("Outstanding balance: Rs. " + money(party.balance()), new Font(Font.HELVETICA, 12, Font.BOLD));
            balance.setSpacingBefore(6);
            balance.setSpacingAfter(10);
            doc.add(balance);
            if (party.overdue()) {
                doc.add(new Paragraph("Overdue: Rs. " + money(party.overdueAmount()) + " (" + party.overdueDays() + " days)", bold));
            }

            PdfPTable table = new PdfPTable(new float[]{2f, 2.4f, 2.6f, 2f, 2f, 2.2f});
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String h : new String[]{"Date", "Type", "Reference", "Debit", "Credit", "Balance"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, bold));
                cell.setBackgroundColor(new java.awt.Color(240, 235, 220));
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (KhataDtos.LedgerEntry e : data.entries()) {
                boolean debit = e.delta().signum() > 0;
                String ref = e.billNo() != null ? e.billNo() : e.note() != null ? e.note() : "";
                table.addCell(cell(e.date().format(DAY), normal, Element.ALIGN_LEFT));
                table.addCell(cell(e.type().replace('_', ' '), normal, Element.ALIGN_LEFT));
                table.addCell(cell(ref, normal, Element.ALIGN_LEFT));
                table.addCell(cell(debit ? money(e.delta()) : "", normal, Element.ALIGN_RIGHT));
                table.addCell(cell(debit ? "" : money(e.delta().negate()), normal, Element.ALIGN_RIGHT));
                table.addCell(cell(money(e.runningBalance()), bold, Element.ALIGN_RIGHT));
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the khata statement", e);
        }
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        return c;
    }

    // ==================== WhatsApp reminder ====================

    @Transactional(readOnly = true)
    public KhataDtos.ReminderResponse whatsappReminder(Long shopId, Long partyId, String language) {
        KhataDtos.PartyResponse p = getParty(shopId, partyId);
        if (p.balance().signum() <= 0) {
            throw new DomainException(409, "Nothing is outstanding for " + p.name());
        }
        String shop = shopService.findById(shopId).getName();
        boolean hindi = "hi".equalsIgnoreCase(language);
        String due = p.dueDate() == null ? "-" : p.dueDate().format(DAY);
        String message;
        if (hindi) {
            message = "नमस्ते " + p.name() + " जी,\n" + shop + " की ओर से याद दिलाना चाहते हैं कि आपका बकाया ₹" + money(p.balance())
                    + " है।" + (p.overdue() ? "\nइसमें ₹" + money(p.overdueAmount()) + " की देय तिथि निकल चुकी है (" + p.overdueDays() + " दिन)।" : "")
                    + "\nदेय तिथि: " + due + "\nकृपया जल्द भुगतान करें। धन्यवाद!";
        } else {
            message = "Namaste " + p.name() + ",\nThis is a gentle reminder from " + shop + ". Your outstanding balance is Rs. "
                    + money(p.balance()) + "."
                    + (p.overdue() ? "\nRs. " + money(p.overdueAmount()) + " is overdue by " + p.overdueDays() + " days." : "")
                    + "\nDue date: " + due + "\nKindly clear the amount at your earliest. Thank you!";
        }
        return new KhataDtos.ReminderResponse(waUrl(p.phone(), message), message, hindi ? "hi" : "en");
    }

    public static String waUrl(String phone, String message) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            digits = "91" + digits;
        }
        return "https://wa.me/" + digits + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ==================== helpers ====================

    private Long customerFor(Long shopId, String name, String phone) {
        List<Long> existing = db.jdbc().queryForList(
                "SELECT id FROM customers WHERE shop_id = :shop AND phone = :phone LIMIT 1",
                Map.of("shop", shopId, "phone", phone), Long.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return customerService.createCustomer(shopId, new SalesDtos.CustomerRequest(name.trim(), phone, null, null)).id();
    }

    private static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new DomainException(400, "Party type must be one of " + TYPES);
        }
        return t;
    }

    private static String normalizePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        if (digits.length() < 7) {
            throw new DomainException(400, "Enter a valid phone number");
        }
        return digits;
    }

    private static Payment.PaymentMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return Payment.PaymentMode.CASH;
        }
        try {
            return Payment.PaymentMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Unknown payment mode " + mode);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String money(BigDecimal value) {
        return String.format(Locale.ENGLISH, "%,.2f", value);
    }
}
