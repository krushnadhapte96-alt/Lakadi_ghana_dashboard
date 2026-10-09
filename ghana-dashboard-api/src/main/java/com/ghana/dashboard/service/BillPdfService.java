package com.ghana.dashboard.service;

import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.Shop;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BillPdfService {

    private static final Logger log = LoggerFactory.getLogger(BillPdfService.class);

    private final ShopService shopService;
    private final LicenceService licenceService;
    private final SettingsService settingsService;
    private final QrCodeService qrCodeService;

    public BillPdfService(ShopService shopService, LicenceService licenceService,
                          SettingsService settingsService, QrCodeService qrCodeService) {
        this.shopService = shopService;
        this.licenceService = licenceService;
        this.settingsService = settingsService;
        this.qrCodeService = qrCodeService;
            }

    public byte[] generateSaleBillPdf(Sale sale) {
        try {
            SettingsService.BillBranding branding = settingsService.billBranding(sale.getShopId());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addBillHeader(document, sale.getShopId(), branding, "SALE BILL", sale.getBillNo(), sale.getCreatedAt());
            addCustomerInfo(document, sale);
            addItemsTable(document, sale.getItems(), sale.getSubtotal(), branding.showBatchNumber());
            addTotals(document, sale);
            addFooter(document, sale, branding);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    public byte[] generateOrderBillPdf(Order order) {
        try {
            SettingsService.BillBranding branding = settingsService.billBranding(order.getShopId());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addBillHeader(document, order.getShopId(), branding, "ORDER BILL", order.getBillNo(), order.getCreatedAt());
            addOrderCustomerInfo(document, order);
            addItemsTable(document, order.getItems(), order.getSubtotal(), branding.showBatchNumber());
            addOrderTotals(document, order);
            addOrderFooter(document, order, branding);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private void addBillHeader(Document document, Long shopId, SettingsService.BillBranding branding,
                               String title, String billNo, LocalDateTime createdAt) throws DocumentException {
        Shop shop = shopService.findById(shopId);

        addLogo(document, branding);

        String name = notBlank(branding.shopName()) ? branding.shopName() : shop.getName();
        Font shopNameFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph shopName = new Paragraph(name, shopNameFont);
        shopName.setAlignment(Element.ALIGN_CENTER);
        document.add(shopName);

        Font addressFont = new Font(Font.HELVETICA, 10);
        if (notBlank(shop.getAddress())) {
            Paragraph address = new Paragraph(shop.getAddress(), addressFont);
            address.setAlignment(Element.ALIGN_CENTER);
            document.add(address);
        }
        if (notBlank(shop.getMobile())) {
            Paragraph phone = new Paragraph("Ph: " + shop.getMobile(), addressFont);
            phone.setAlignment(Element.ALIGN_CENTER);
            document.add(phone);
        }

        addLicenceNumbers(document, shopId, branding);

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Paragraph billTitle = new Paragraph(title, titleFont);
        billTitle.setAlignment(Element.ALIGN_CENTER);
        billTitle.setSpacingAfter(10);
        document.add(billTitle);
        
        PdfPTable billDetailsTable = new PdfPTable(2);
        billDetailsTable.setWidthPercentage(100);
        billDetailsTable.setWidths(new float[]{1, 1});
        
        addTableCell(billDetailsTable, "Bill No:", new Font(Font.HELVETICA, 10, Font.BOLD));
        addTableCell(billDetailsTable, billNo, new Font(Font.HELVETICA, 10));
        addTableCell(billDetailsTable, "Date:", new Font(Font.HELVETICA, 10, Font.BOLD));
        addTableCell(billDetailsTable, formatDate(createdAt), new Font(Font.HELVETICA, 10));
        
        document.add(billDetailsTable);
        document.add(new Paragraph(" "));
    }

    /** Shop logo, scaled to a sensible width and centred. Omitted silently if unusable. */
    private void addLogo(Document document, SettingsService.BillBranding branding) throws DocumentException {
        if (branding.logoPng() == null || branding.logoPng().length == 0) {
            return;
        }
        try {
            Image logo = Image.getInstance(branding.logoPng());
            logo.scalePercent(45);
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);
        } catch (Exception e) {
            // A corrupt or unsupported image must not stop the bill from printing.
            log.warn("Skipping unreadable shop logo on bill", e);
        }
    }

    /**
     * FSSAI number and GSTIN under the shop address. A live licence in the register
     * always wins; the shop profile value is only a fallback for shops that have not
     * moved to the register yet. An expired licence is never printed.
     */
    private void addLicenceNumbers(Document document, Long shopId,
                                   SettingsService.BillBranding branding) throws DocumentException {
        String fssai = null;
        String gstin = null;
        try {
            LicenceService.BillIdentifiers ids = licenceService.billIdentifiers(shopId);
            fssai = ids.fssaiNumber();
            gstin = ids.gstin();
        } catch (RuntimeException e) {
            // A missing licence register must never stop a bill from printing; fall
            // through to whatever the shop profile holds.
            log.warn("Licence register unavailable while printing a bill for shop {}", shopId, e);
        }
        if (!notBlank(fssai)) {
            fssai = settingsService.shop(shopId).fssaiNumber();
        }
        if (!notBlank(gstin)) {
            gstin = settingsService.shop(shopId).gstin();
        }

        StringBuilder line = new StringBuilder();
        if (notBlank(fssai)) {
            line.append("FSSAI No: ").append(fssai);
        }
        if (notBlank(gstin)) {
            if (!line.isEmpty()) {
                line.append("   |   ");
            }
            line.append("GSTIN: ").append(gstin);
        }
        if (line.isEmpty()) {
            return;
        }
        Paragraph paragraph = new Paragraph(line.toString(), new Font(Font.HELVETICA, 8, Font.BOLD));
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void addCustomerInfo(Document document, Sale sale) throws DocumentException {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10);
        
        if (sale.getCustomer() != null) {
            PdfPTable customerTable = new PdfPTable(2);
            customerTable.setWidthPercentage(100);
            customerTable.setWidths(new float[]{1, 2});
            
            addTableCell(customerTable, "Customer:", labelFont);
            addTableCell(customerTable, sale.getCustomer().getName(), valueFont);
            addTableCell(customerTable, "Phone:", labelFont);
            addTableCell(customerTable, sale.getCustomer().getPhone(), valueFont);
            
            document.add(customerTable);
        }
        document.add(new Paragraph(" "));
    }

    private void addOrderCustomerInfo(Document document, Order order) throws DocumentException {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10);
        
        PdfPTable customerTable = new PdfPTable(2);
        customerTable.setWidthPercentage(100);
        customerTable.setWidths(new float[]{1, 2});
        
        addTableCell(customerTable, "Customer:", labelFont);
        addTableCell(customerTable, order.getCustomer().getName(), valueFont);
        addTableCell(customerTable, "Phone:", labelFont);
        addTableCell(customerTable, order.getCustomer().getPhone(), valueFont);
        
        if (order.getAddress() != null) {
            addTableCell(customerTable, "Address:", labelFont);
            addTableCell(customerTable, getAddressString(order.getAddress()), valueFont);
        }
        
        if (order.getExpectedDeliveryDate() != null) {
            addTableCell(customerTable, "Delivery Date:", labelFont);
            addTableCell(customerTable, order.getExpectedDeliveryDate().toString(), valueFont);
        }
        
        document.add(customerTable);
        document.add(new Paragraph(" "));
    }

    private String getAddressString(CustomerAddress address) {
        return Stream.of(
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPincode()
        ).filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(", "));
    }

    @SuppressWarnings("unchecked")
    private void addItemsTable(Document document, List<?> items, BigDecimal subtotal,
                               boolean showBatchNumber) throws DocumentException {
        // The batch column is only worth printing when the owner asked for it *and* some
        // line actually has a batch, so an order bill never shows a column of dashes.
        boolean withBatch = showBatchNumber && items.stream()
                .anyMatch(item -> item instanceof SaleItem s && s.getBatchId() != null);
        int columns = withBatch ? 6 : 5;
        PdfPTable itemsTable = new PdfPTable(columns);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(withBatch
                ? new float[]{2, 1.4f, 1.2f, 0.8f, 1, 1}
                : new float[]{2, 2, 1, 1, 1});

        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD);
        Color headerBg = new Color(240, 240, 240);

        addTableCell(itemsTable, "Item", headerFont, headerBg);
        if (withBatch) {
            addTableCell(itemsTable, "Batch", headerFont, headerBg);
        }
        addTableCell(itemsTable, "SKU", headerFont, headerBg);
        addTableCell(itemsTable, "Qty", headerFont, headerBg);
        addTableCell(itemsTable, "Rate", headerFont, headerBg);
        addTableCell(itemsTable, "Amount", headerFont, headerBg);

        Font itemFont = new Font(Font.HELVETICA, 9);
        for (Object itemObj : items) {
            String name = "";
            String sku = "";
            BigDecimal qty = BigDecimal.ZERO;
            BigDecimal rate = BigDecimal.ZERO;
            BigDecimal amount = BigDecimal.ZERO;
            String batchId = null;

            if (itemObj instanceof SaleItem saleItem) {
                name = saleItem.getProductName();
                sku = saleItem.getSku();
                qty = saleItem.getQty();
                rate = saleItem.getRate();
                amount = saleItem.getAmount();
                batchId = saleItem.getBatchId();
            } else if (itemObj instanceof OrderItem orderItem) {
                name = orderItem.getProductName();
                sku = orderItem.getSku();
                qty = orderItem.getQty();
                rate = orderItem.getRate();
                amount = orderItem.getAmount();
            }

            addTableCell(itemsTable, name, itemFont);
            if (withBatch) {
                // Lines sold straight from opening stock carry the literal "OPENING".
                addTableCell(itemsTable, batchId == null ? "-" : batchId, itemFont);
            }
            addTableCell(itemsTable, sku, itemFont);
            addTableCell(itemsTable, qty.toString(), itemFont);
            addTableCell(itemsTable, formatCurrency(rate), itemFont);
            addTableCell(itemsTable, formatCurrency(amount), itemFont);
        }

        document.add(itemsTable);
        document.add(new Paragraph(" "));
    }

    private void addTotals(Document document, Sale sale) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(50);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{1, 1});
        
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10);
        
        addTableCell(totalsTable, "Subtotal:", labelFont);
        addTableCell(totalsTable, formatCurrency(sale.getSubtotal()), valueFont);
        addTableCell(totalsTable, "Discount:", labelFont);
        addTableCell(totalsTable, formatCurrency(sale.getDiscount()), valueFont);
        addTableCell(totalsTable, "Total:", labelFont);
        addTableCell(totalsTable, formatCurrency(sale.getTotalAmount()), valueFont);
        
        if (sale.getPaidAmount() != null && sale.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTableCell(totalsTable, "Paid:", labelFont);
            addTableCell(totalsTable, formatCurrency(sale.getPaidAmount()), valueFont);
            BigDecimal balance = sale.getTotalAmount().subtract(sale.getPaidAmount());
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                addTableCell(totalsTable, "Balance:", labelFont);
                addTableCell(totalsTable, formatCurrency(balance), valueFont);
            }
        }
        
        document.add(totalsTable);
        document.add(new Paragraph(" "));
    }

    private void addOrderTotals(Document document, Order order) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(50);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{1, 1});
        
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10);
        
        addTableCell(totalsTable, "Subtotal:", labelFont);
        addTableCell(totalsTable, formatCurrency(order.getSubtotal()), valueFont);
        addTableCell(totalsTable, "Delivery Charge:", labelFont);
        addTableCell(totalsTable, formatCurrency(order.getDeliveryCharge()), valueFont);
        addTableCell(totalsTable, "Discount:", labelFont);
        addTableCell(totalsTable, formatCurrency(order.getDiscount()), valueFont);
        addTableCell(totalsTable, "Total:", labelFont);
        addTableCell(totalsTable, formatCurrency(order.getTotalAmount()), valueFont);
        
        if (order.getPaidAmount() != null && order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTableCell(totalsTable, "Paid:", labelFont);
            addTableCell(totalsTable, formatCurrency(order.getPaidAmount()), valueFont);
            BigDecimal balance = order.getTotalAmount().subtract(order.getPaidAmount());
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                addTableCell(totalsTable, "Balance:", labelFont);
                addTableCell(totalsTable, formatCurrency(balance), valueFont);
            }
        }
        
        document.add(totalsTable);
        document.add(new Paragraph(" "));
    }

    private void addFooter(Document document, Sale sale,
                            SettingsService.BillBranding branding) throws DocumentException {
        Font footerFont = new Font(Font.HELVETICA, 8);
        Paragraph thankYou = new Paragraph("Thank you for your purchase!", footerFont);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        document.add(thankYou);
        Paragraph paymentMode = new Paragraph("Payment: " + sale.getPaymentMode(), footerFont);
        paymentMode.setAlignment(Element.ALIGN_CENTER);
        document.add(paymentMode);

        addPaymentBlock(document, branding, footerFont);
        addCustomFooter(document, branding, footerFont);

        if (sale.isVoid()) {
            Paragraph voidNote = new Paragraph("VOID", new Font(Font.HELVETICA, 20, Font.BOLD, Color.RED));
            voidNote.setAlignment(Element.ALIGN_CENTER);
            document.add(voidNote);
        }
    }

    private void addOrderFooter(Document document, Order order,
                                SettingsService.BillBranding branding) throws DocumentException {
        Font footerFont = new Font(Font.HELVETICA, 8);
        Paragraph thankYou = new Paragraph("Thank you for your order!", footerFont);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        document.add(thankYou);
        Paragraph paymentMode = new Paragraph("Payment: " + order.getPaymentMode(), footerFont);
        paymentMode.setAlignment(Element.ALIGN_CENTER);
        document.add(paymentMode);
        Paragraph status = new Paragraph("Status: " + order.getStatus(), footerFont);
        status.setAlignment(Element.ALIGN_CENTER);
        document.add(status);

        addPaymentBlock(document, branding, footerFont);
        addCustomFooter(document, branding, footerFont);
    }

    /** UPI QR and/or bank details, each only when the owner has switched it on. */
    private void addPaymentBlock(Document document, SettingsService.BillBranding branding, Font font)
            throws DocumentException {
        boolean wantQr = branding.showUpiQr() && notBlank(branding.upiId());
        boolean wantBank = branding.showBankDetails() && notBlank(branding.bankDetails());
        if (!wantQr && !wantBank) {
            return;
        }

        PdfPTable block = new PdfPTable(2);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{1, 1});

        if (wantQr) {
            byte[] png = qrCodeService.pngForUpi(branding.upiId(), branding.shopName());
            if (png != null) {
                try {
                    Image qr = Image.getInstance(png);
                    qr.scaleAbsolute(80f, 80f);
                    PdfPCell cell = new PdfPCell(qr, false);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(4);
                    block.addCell(cell);

                    Paragraph caption = new Paragraph();
                    caption.setAlignment(Element.ALIGN_CENTER);
                    caption.add(new Phrase("Scan to pay", new Font(Font.HELVETICA, 8, Font.BOLD)));
                    caption.add(new Phrase("\n" + branding.upiId(), font));
                    PdfPCell text = new PdfPCell(caption);
                    text.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    text.setPadding(4);
                    block.addCell(text);
                } catch (Exception e) {
                    log.warn("Could not place the UPI QR on the bill", e);
                }
            }
        }
        if (wantBank) {
            if (wantQr && block.size() > 0) {
                // Keep the bank text in the right-hand column of the same row.
                PdfPCell spacer = new PdfPCell();
                block.addCell(spacer);
            }
            Paragraph bank = new Paragraph(bankingBlock(branding.bankDetails()), font);
            PdfPCell cell = new PdfPCell(bank);
            cell.setPadding(4);
            block.addCell(cell);
        }
        if (block.size() > 0) {
            document.add(block);
        }
    }

    /**
     * Bank details as stored by the owner, one entry per line. Line breaks inside a
     * PDF phrase need explicit Chunk.newLine() to survive.
     */
    private static String bankingBlock(String bankDetails) {
        StringBuilder out = new StringBuilder();
        for (String rawLine : bankDetails.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    /** The owner's own footer message, printed under everything else. */
    private void addCustomFooter(Document document, SettingsService.BillBranding branding, Font font)
            throws DocumentException {
        if (!notBlank(branding.footerMessage())) {
            return;
        }
        Paragraph custom = new Paragraph(bankingBlock(branding.footerMessage()), font);
        custom.setAlignment(Element.ALIGN_CENTER);
        document.add(custom);
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setBackgroundColor(background);
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        return "Rs. " + amount.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a"));
    }
}
