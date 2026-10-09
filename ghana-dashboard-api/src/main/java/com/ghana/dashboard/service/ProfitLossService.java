package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Expense;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.repository.ExpenseRepository;
import com.ghana.dashboard.repository.LossLedgerRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Profit &amp; Loss. Revenue (sales + delivered orders, khali included) minus COGS gives gross;
 * net = gross - expenses - losses. Every group total is built from the same set of dated lines,
 * so any grouping sums to exactly the same headline figures.
 */
@Service
public class ProfitLossService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final Set<String> GROUPINGS = Set.of("day", "month", "oilType");

    private static final String UNALLOCATED = "unallocated";
    private static final String OTHER = "other";
    private static final String KHALI = "khali";

    private final SaleRepository saleRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final LossLedgerRepository lossLedgerRepository;
    private final ProductRepository productRepository;
    private final ShopService shopService;

    public ProfitLossService(SaleRepository saleRepository,
                             OrderRepository orderRepository,
                             ExpenseRepository expenseRepository,
                             LossLedgerRepository lossLedgerRepository,
                             ProductRepository productRepository,
                             ShopService shopService) {
        this.saleRepository = saleRepository;
        this.orderRepository = orderRepository;
        this.expenseRepository = expenseRepository;
        this.lossLedgerRepository = lossLedgerRepository;
        this.productRepository = productRepository;
        this.shopService = shopService;
    }

    private record Line(LocalDate date, String oilKey, String oilLabel,
                        BigDecimal revenue, BigDecimal cogs, BigDecimal expenses, BigDecimal losses) {}

    private record Oil(String key, String label) {}

    // ==================== P&L ====================

    @Transactional(readOnly = true)
    public ExpenseDtos.PnLSummaryResponse getProfitLoss(Long shopId, LocalDate from, LocalDate to, String groupBy) {
        validate(from, to, groupBy);

        List<Line> lines = collectLines(shopId, from, to);
        Map<String, Acc> groups = new LinkedHashMap<>();
        if (!groupBy.equals("oilType")) {
            seedEmptyBuckets(groups, from, to, groupBy);
        }
        for (Line line : lines) {
            String key = groupKey(line, groupBy);
            groups.computeIfAbsent(key, k -> new Acc(groupLabel(line, groupBy))).add(line);
        }

        List<Map.Entry<String, Acc>> ordered = new ArrayList<>(groups.entrySet());
        if (groupBy.equals("oilType")) {
            ordered.sort(Comparator
                    .comparing((Map.Entry<String, Acc> e) -> e.getKey().equals(UNALLOCATED))
                    .thenComparing((e1, e2) -> e2.getValue().revenue.compareTo(e1.getValue().revenue)));
        }

        Acc total = new Acc("Total");
        List<ExpenseDtos.PnLGroupResponse> responses = new ArrayList<>();
        for (Map.Entry<String, Acc> e : ordered) {
            Acc acc = e.getValue();
            total.merge(acc);
            responses.add(new ExpenseDtos.PnLGroupResponse(e.getKey(), acc.label,
                    money(acc.revenue), money(acc.cogs), money(acc.gross()),
                    money(acc.expenses), money(acc.losses), money(acc.net())));
        }

        return new ExpenseDtos.PnLSummaryResponse(from, to, groupBy,
                money(total.revenue), money(total.cogs), money(total.gross()),
                money(total.expenses), money(total.losses), money(total.net()),
                percent(total.gross(), total.revenue), percent(total.net(), total.revenue),
                responses);
    }

    private void validate(LocalDate from, LocalDate to, String groupBy) {
        if (!GROUPINGS.contains(groupBy)) {
            throw new DomainException(400, "groupBy must be one of day, month, oilType");
        }
        if (from.isAfter(to)) {
            throw new DomainException(400, "'from' must not be after 'to'");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366 * 5) {
            throw new DomainException(400, "Date range is limited to 5 years");
        }
        if (groupBy.equals("day") && days > 366) {
            throw new DomainException(400, "Use groupBy=month for ranges longer than a year");
        }
    }

    private List<Line> collectLines(Long shopId, LocalDate from, LocalDate to) {
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();
        List<Line> lines = new ArrayList<>();

        for (Sale sale : saleRepository.findForPnl(shopId, start, end)) {
            List<SaleItem> items = sale.getItems() == null ? List.of() : sale.getItems();
            addDocumentLines(lines, sale.getCreatedAt().toLocalDate(), sale.getTotalAmount(),
                    items, SaleItem::getAmount, SaleItem::getProduct, SaleItem::getCostPrice, SaleItem::getQty);
        }

        for (Order order : orderRepository.findDeliveredForPnl(shopId, from, to, start, end)) {
            LocalDate date = order.getActualDeliveryDate() != null
                    ? order.getActualDeliveryDate() : order.getCreatedAt().toLocalDate();
            List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
            addDocumentLines(lines, date, order.getTotalAmount(),
                    items, OrderItem::getAmount, OrderItem::getProduct, OrderItem::getCostPrice, OrderItem::getQty);
        }

        Oil unallocated = new Oil(UNALLOCATED, "General (unallocated)");
        for (Expense expense : expenseRepository.findByShopIdAndExpenseDateBetween(shopId, from, to)) {
            lines.add(new Line(expense.getExpenseDate(), unallocated.key, unallocated.label,
                    ZERO, ZERO, expense.getAmount(), ZERO));
        }

        List<LossLedger> losses = lossLedgerRepository.findByShopIdAndLossDateBetween(shopId, from, to);
        Map<Long, Product> products = productRepository.findAllById(
                        losses.stream().map(LossLedger::getProductId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (LossLedger loss : losses) {
            Product product = loss.getProductId() != null ? products.get(loss.getProductId()) : null;
            Oil oil = product != null ? oilOf(product) : unallocated;
            lines.add(new Line(loss.getLossDate(), oil.key, oil.label, ZERO, ZERO, ZERO, loss.getValue()));
        }
        return lines;
    }

    /**
     * A bill's total already includes discount and delivery charge, so it is spread over the items
     * in proportion to their amounts; the last item absorbs the rounding remainder.
     */
    private <T> void addDocumentLines(List<Line> lines, LocalDate date, BigDecimal total, List<T> items,
                                      Function<T, BigDecimal> amount, Function<T, Product> product,
                                      Function<T, BigDecimal> cost, Function<T, BigDecimal> qty) {
        BigDecimal itemsTotal = items.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (items.isEmpty() || itemsTotal.signum() == 0) {
            lines.add(new Line(date, OTHER, "Other / Unassigned", total, ZERO, ZERO, ZERO));
            return;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            BigDecimal share = i == items.size() - 1
                    ? total.subtract(allocated)
                    : total.multiply(amount.apply(item)).divide(itemsTotal, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(share);

            Product p = product.apply(item);
            Oil oil = p != null ? oilOf(p) : new Oil(OTHER, "Other / Unassigned");
            lines.add(new Line(date, oil.key, oil.label, share, cogsOf(item, p, cost, qty), ZERO, ZERO));
        }
    }

    /** Snapshot cost on the bill line; falls back to the product's current cost price when not captured. */
    private <T> BigDecimal cogsOf(T item, Product product, Function<T, BigDecimal> cost, Function<T, BigDecimal> qty) {
        BigDecimal unitCost = cost.apply(item);
        if ((unitCost == null || unitCost.signum() == 0) && product != null && product.getCostPrice() != null) {
            unitCost = product.getCostPrice();
        }
        return unitCost == null ? ZERO : unitCost.multiply(qty.apply(item));
    }

    private Oil oilOf(Product product) {
        if (product.isKhali()) {
            return new Oil(KHALI, "Khali");
        }
        if (product.getOilType() != null) {
            return new Oil(String.valueOf(product.getOilType().getId()), product.getOilType().getNameEn());
        }
        return new Oil(OTHER, "Other / Unassigned");
    }

    private String groupKey(Line line, String groupBy) {
        return switch (groupBy) {
            case "day" -> line.date.toString();
            case "month" -> YearMonth.from(line.date).toString();
            default -> line.oilKey;
        };
    }

    private String groupLabel(Line line, String groupBy) {
        return switch (groupBy) {
            case "day" -> line.date.toString();
            case "month" -> YearMonth.from(line.date).format(MONTH_LABEL);
            default -> line.oilLabel;
        };
    }

    private void seedEmptyBuckets(Map<String, Acc> groups, LocalDate from, LocalDate to, String groupBy) {
        if (groupBy.equals("day")) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                groups.put(d.toString(), new Acc(d.toString()));
            }
        } else {
            for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                groups.put(m.toString(), new Acc(m.format(MONTH_LABEL)));
            }
        }
    }

    private static final class Acc {
        final String label;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        Acc(String label) {
            this.label = label;
        }

        void add(Line l) {
            revenue = revenue.add(l.revenue);
            cogs = cogs.add(l.cogs);
            expenses = expenses.add(l.expenses);
            losses = losses.add(l.losses);
        }

        void merge(Acc o) {
            revenue = revenue.add(o.revenue);
            cogs = cogs.add(o.cogs);
            expenses = expenses.add(o.expenses);
            losses = losses.add(o.losses);
        }

        BigDecimal gross() {
            return revenue.subtract(cogs);
        }

        BigDecimal net() {
            return gross().subtract(expenses).subtract(losses);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    // ==================== CSV export ====================

    @Transactional(readOnly = true)
    public String exportPnLCsv(Long shopId, LocalDate from, LocalDate to, String groupBy) {
        ExpenseDtos.PnLSummaryResponse pnl = getProfitLoss(shopId, from, to, groupBy);
        StringBuilder csv = new StringBuilder("Group,Revenue,COGS,Gross Profit,Expenses,Losses,Net Profit\r\n");
        for (ExpenseDtos.PnLGroupResponse g : pnl.groups()) {
            csv.append(csvText(g.groupLabel())).append(',')
                    .append(g.revenue()).append(',').append(g.cogs()).append(',').append(g.grossProfit()).append(',')
                    .append(g.expenses()).append(',').append(g.losses()).append(',').append(g.netProfit()).append("\r\n");
        }
        csv.append("TOTAL,")
                .append(pnl.totalRevenue()).append(',').append(pnl.totalCogs()).append(',').append(pnl.grossProfit()).append(',')
                .append(pnl.totalExpenses()).append(',').append(pnl.totalLosses()).append(',').append(pnl.netProfit())
                .append("\r\n");
        return csv.toString();
    }

    /** Quotes a text cell and neutralises spreadsheet formula injection from user-defined names. */
    private static String csvText(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    // ==================== PDF report ====================

    @Transactional(readOnly = true)
    public byte[] generatePnLReportPdf(Long shopId, LocalDate from, LocalDate to, String groupBy) {
        ExpenseDtos.PnLSummaryResponse pnl = getProfitLoss(shopId, from, to, groupBy);
        Shop shop = shopService.findById(shopId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 10);

            Paragraph shopName = new Paragraph(shop.getName(), new Font(Font.HELVETICA, 18, Font.BOLD));
            shopName.setAlignment(Element.ALIGN_CENTER);
            document.add(shopName);

            Paragraph title = new Paragraph("Profit & Loss Statement: " + from + " to " + to,
                    new Font(Font.HELVETICA, 13, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(16);
            document.add(title);

            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(60);
            summary.setWidths(new float[]{2, 1});
            summaryRow(summary, "Revenue", rs(pnl.totalRevenue()), bold, normal);
            summaryRow(summary, "Cost of goods sold", rs(pnl.totalCogs()), bold, normal);
            summaryRow(summary, "Gross profit (" + pnl.grossMarginPercent() + "%)", rs(pnl.grossProfit()), bold, normal);
            summaryRow(summary, "Expenses", rs(pnl.totalExpenses()), bold, normal);
            summaryRow(summary, "Losses", rs(pnl.totalLosses()), bold, normal);
            summaryRow(summary, "Net profit (" + pnl.netMarginPercent() + "%)", rs(pnl.netProfit()), bold, bold);
            document.add(summary);

            Paragraph breakdown = new Paragraph("Breakdown by " + groupBy, bold);
            breakdown.setSpacingBefore(16);
            breakdown.setSpacingAfter(6);
            document.add(breakdown);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            for (String h : List.of("Group", "Revenue", "COGS", "Gross", "Expenses", "Losses", "Net")) {
                cell(table, h, bold, h.equals("Group") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            }
            for (ExpenseDtos.PnLGroupResponse g : pnl.groups()) {
                cell(table, g.groupLabel(), normal, Element.ALIGN_LEFT);
                for (BigDecimal v : List.of(g.revenue(), g.cogs(), g.grossProfit(), g.expenses(), g.losses(), g.netProfit())) {
                    cell(table, v.toPlainString(), normal, Element.ALIGN_RIGHT);
                }
            }
            cell(table, "TOTAL", bold, Element.ALIGN_LEFT);
            for (BigDecimal v : List.of(pnl.totalRevenue(), pnl.totalCogs(), pnl.grossProfit(),
                    pnl.totalExpenses(), pnl.totalLosses(), pnl.netProfit())) {
                cell(table, v.toPlainString(), bold, Element.ALIGN_RIGHT);
            }
            document.add(table);

            Paragraph footer = new Paragraph("Generated on " + LocalDate.now(), new Font(Font.HELVETICA, 8));
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(16);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new DomainException(500, "Failed to generate the P&L report");
        }
    }

    private static String rs(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void summaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        cell(table, label, labelFont, Element.ALIGN_LEFT);
        cell(table, value, valueFont, Element.ALIGN_RIGHT);
    }

    private static void cell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(4);
        c.setHorizontalAlignment(alignment);
        table.addCell(c);
    }
}
