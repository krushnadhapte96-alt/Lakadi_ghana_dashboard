package com.ghana.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Home dashboard payload. Every figure is defined so that it reconciles with the module it
 * points at — see {@code DashboardService} for the per-card definitions.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Walk-in sales booked today, matching the Sales module filtered to today. */
    public record TodaySales(BigDecimal amount, BigDecimal litres, long bills) {
    }

    public record LowStockItem(Long productId, String name, String sku,
                               BigDecimal available, BigDecimal threshold) {
    }

    /** Products below their own low-stock threshold, as the Inventory module counts them. */
    public record LowStock(long count, List<LowStockItem> top) {
    }

    /** Seed (beej) holding across every seed type, in kg. */
    public record SeedStock(BigDecimal totalKg, long seedTypes, long lowCount) {
    }

    public record ExpiringBatch(Long batchStockId, String productName, String batchId,
                                BigDecimal qtyAvailable, LocalDate expiryDate, long daysLeft) {
    }

    /** Live lots expiring within 30 days (the Expiry Alerts window), snoozed lots excluded. */
    public record ExpiringSoon(long count, LocalDate nearestExpiry, BigDecimal qtyAtRisk,
                               List<ExpiringBatch> top) {
    }

    /** Outstanding udhaar: unpaid walk-in bills plus the khata balance still owed. */
    public record MoneyToCollect(BigDecimal amount, long unpaidBills, long parties) {
    }

    public record LicenceAlert(Long licenceId, String number, String type,
                               LocalDate expiryDate, long daysLeft, String status) {
    }

    public record LicenceAlerts(long expiringSoon, long expired, LicenceAlert next) {
    }

    public record RefillCustomer(Long customerId, String name, String phone,
                                 LocalDate predictedDate, long daysUntil) {
    }

    /** Customers whose predicted refill has arrived (or is within the lead days). */
    public record RefillDue(long count, List<RefillCustomer> top) {
    }

    public record SalesPoint(LocalDate date, BigDecimal amount, long bills, BigDecimal litres) {
    }

    public record OilMixSlice(Long oilTypeId, String label, String colourTag, BigDecimal amount) {
    }

    /**
     * @param monthNetProfit    null for STAFF — the owner-only P&amp;L figure is never
     *                          computed for, or shipped to, a staff account
     * @param monthMarginPercent net profit as a share of revenue, likewise null for STAFF
     */
    public record Summary(
            LocalDate date,
            String shopName,
            TodaySales todaySales,
            long pendingOrders,
            LowStock lowStock,
            SeedStock seedStock,
            ExpiringSoon expiringSoon,
            BigDecimal monthNetProfit,
            BigDecimal monthMarginPercent,
            MoneyToCollect moneyToCollect,
            LicenceAlerts licences,
            RefillDue refillDue,
            List<SalesPoint> salesSeries,
            List<OilMixSlice> oilMix,
            LocalDateTime generatedAt) {
    }
}