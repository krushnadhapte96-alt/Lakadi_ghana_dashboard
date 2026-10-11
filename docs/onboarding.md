# Onboarding guide — how the Ghana Dashboard data fits together

This guide is for anyone (a new developer, tester, or shop owner) opening the app for the
first time. It explains what each module represents, how the tables relate, and what
demo data already exists so the app is never an empty shell.

## Demo access

| Thing | Value |
| --- | --- |
| Login field | `identifier` (phone or email — **not** `username`) |
| Owner demo account | `9876501234` / `Owner@2026` (shop 1000340) |
| API | http://localhost:8080/api/v1 |
| Frontend | http://localhost:5173 (Vite dev) |

Only the owner sees the business tabs in Settings and the net-profit figures; all staff
accounts have the same read/write access as the owner on sales, inventory, and payments
except the owner-only actions (void sale, void product, staff management).

## The core loops

Everything hangs off **products** and **oil types**. Think of the business as:

    seed purchase ──► stock in (seed)  ──► production batch ──► bottled product stock
                                                                      │
    packaging purchase ───────────────────────────────────────────┘      │
                                                                          ▼
    sale (counter)  ──► stock out           order (delivery) ──► reserves stock, then ships
           │                                     │
           ├─► payment IN                        ├─► payment IN (or COD / udhaar)
           └─► khata bill if udhaar              └─► khata bill if udhaar
                                                                          │
    return / khali sale ──► stock back in or money back (payment REFUND)
    expense            ──► payment OUT

### Modules and what they record

- **Beej (seed)** — seed purchases. Creates/updates *seed stock* (kg by seed type).
  A seed purchase feeds the next step.
- **Production** — one batch becomes finished stock. When you run a batch the API adds a
  `stock_movements` row of type `BATCH_OUTPUT` (oil into tanks) and if "bottling" is set,
  it takes packaging items (`BATCH_BOTTLE` usage) and creates finished-product stock.
- **Inventory** — the live stock view. Oil products, khali (empty-can) products, and
  packaging items, plus the full `stock_movements` history. Every sale, production,
  adjustment, and opening entry writes a movement row with a reason and user.
- **Sales & Orders** — `Sales` are counter bills (bill number, items, discount, payment
  mode/status, optional linked customer, optional khata bill). `Orders` are delivery bills
  with the same line items plus a delivery address; they move through
  `NEW → CONFIRMED → PACKED → OUT_FOR_DELIVERY → DELIVERED` (or `CANCELLED`).
- **Payments** — one row per money movement. Direction `IN` (sale/order/khata/khali/refund)
  or `OUT` (expense/supplier/other); `sourceType` points back at the document that caused it.
  Payment recorders are also created automatically inside a sale/order/expense transaction.
- **Khata** — the running ledger for a customer. A *udhaar* sale posts a bill; settling that
  bill posts a payment `IN` of source `KHATA`. The khata balance is the single source of
  truth for **money to collect** on the dashboard.
- **Returns** — a return can restock the product (movement back in), swap, or refund; the
  resolution choice drives the inventory/payment effect.
- **PNL / Expenses / Costing / Insights** — read-only aggregations: expenses feed PNL,
  costing prices a recipe from oil + packaging + labour, Insights reuses the dashboard
  summary and the customer analytics (refill predictions, top spenders, inactive).

### Table notes (things that trip people up)

- `customers.id` ↔ `khata.tbl` loyalty: a *sale with a customer phone* auto-creates or
  reuses a customer, and a *udhaar* sale auto-creates the khata bill. There is no separate
  "create customer" screen inside a sale — it happens through the phone number.
- Orders **require** an existing customer and a delivery address (`OrderRequest.customerId`
  and `addressId` are `@NotNull`). The New Order modal therefore makes you pick both; you
  cannot type a phone number like in New Sale.
- `stock_movements.type` is an ENUM that was widened (migration **V18**) to include
  `BATCH_OUTPUT`, `BATCH_BOTTLE`, and `BATCH_RETURN_SEED`. If you add more production
  movement types you must extend the enum in the same style.
- Invoice/bill PDFs: sales, expenses, seed purchases, and khata statements all stream a PDF
  from the API. Seed purchases stored by the demo seeder have **no bill attached**, so
  `GET /seed-purchases/{id}/bill` correctly 404s — that is expected, not a bug.
- `GET /inventory/products` is a Server-Sent-Events stream. It "hangs" (500
  `AsyncRequestTimeoutException`) when read as a normal HTTP call. Use `GET /inventory`
  for the aggregate or `GET /products` for the product list.

## Seeded demo data (shop 1000340)

The `DemoSeedRunner` fires on app start for a fresh database. Current contents (as verified
against the live API):

| Module | Count |
| --- | --- |
| Sales | 67 (various payment modes/statuses) |
| Orders | 25 (statuses from NEW to DELIVERED) |
| Products | 17 (oil + khali) |
| Inventory movements | ~60 rows across sales/production/adjustments |
| Payments | 25 IN + OUT (39 with a wider date filter) |
| Customers | 30 (with addresses, preferred language, khata parties) |
| Khata parties/bills | linked to customers |
| Expenses | 15 |
| Oil types | 4 (mustard, groundnut, soybean, coconut) |
| Packaging items | bottles/caps/labels with stock and cost |

So Sales, Inventory, Payments, Orders, Khata, and Customers all have data out of the box.

## Verify a full round trip in under a minute

1. Open **Inventory** → an oil product shows available qty, low-stock flag, and buttons for
   Adjust / Opening / Price / Threshold.
2. Open **Sales & Orders** → the Sales tab lists bills (67); open one to see its items and
   totals; run the Orders tab and watch the status-counts tabs increment.
3. Open **Payments** → set the date range to this month; money in/out/next to each sale.
4. Sell something: **New Sale** → pick a product, qty, rate defaults from selling price,
   choose *udhaar* with a customer phone, save. The brand-new bill appears at the top of
   Sales, its payment posts to Payments as `IN`, stock drops in Inventory, and a khata bill
   is created for that customer.
5. Open **Khata** → settle that bill; Payments gains a `KHATA` `IN` row and the dashboard's
   "money to collect" drops.

## Running

- Backend: `.\mvnw.cmd -q -o compile -DskipTests` then run the app (`run-app.ps1` in the
  temp tooling dir). MySQL DB `ghana_dashboard`, user `ghana_app`.
- Frontend: `npm run dev` (Vite, proxies to the API), or `npm run build` for a production
  bundle (runs `tsc -b` first). `node scripts/check-i18n.mjs` verifies translations.