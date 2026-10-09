package com.ghana.dashboard.dto;

import java.util.List;

/**
 * Response shape for the global search box (Ctrl+K).
 *
 * <p>Results are returned as a list of groups rather than one flat list because the palette shows
 * them as sections, and because it lets the backend decide what is worth showing first - a bill
 * number match is far more likely to be what someone typed than a product name match.
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /**
     * The sections, in the order they are rendered.
     *
     * <p>{@link #BILL_NUMBER} is not one of the data groups - it is a best-effort exact match on a
     * bill or order number, promoted to the top because pasting a bill number is the single most
     * common reason to reach for a global search in a shop.
     */
    public enum Group {
        BILL_NUMBER,
        PRODUCTS,
        SALES,
        ORDERS,
        CUSTOMERS,
        KHATA_PARTIES,
        BATCHES,
        SUPPLIERS
    }

    /**
     * @param id     the row's primary key
     * @param title  the primary label
     * @param subtitle supporting detail - SKU, amount, phone number
     * @param link   frontend route to navigate to on selection
     */
    public record SearchItem(
            Long id,
            String title,
            String subtitle,
            String link
    ) {
    }

    public record SearchGroup(
            String group,
            List<SearchItem> items,
            boolean hasMore
    ) {
    }

    public record SearchResponse(
            String query,
            List<SearchGroup> groups,
            int total
    ) {
    }
}