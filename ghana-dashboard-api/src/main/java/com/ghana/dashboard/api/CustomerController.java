package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.CustomerDtos;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.service.CustomerInsightService;
import jakarta.validation.Valid;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerInsightService insights;

    public CustomerController(CustomerService customerService, CustomerInsightService insights) {
        this.customerService = customerService;
        this.insights = insights;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDtos.ListItem>>> getAll(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(insights.list(CurrentUser.getShopId(), tab, q)));
    }

    @GetMapping("/refill-due")
    public ResponseEntity<ApiResponse<List<CustomerDtos.ListItem>>> refillDue() {
        return ResponseEntity.ok(ApiResponse.ok(insights.list(CurrentUser.getShopId(), "refill", null)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SalesDtos.CustomerSummaryResponse>>> search(
            @RequestParam(required = false) String query) {
        Long shopId = CurrentUser.getShopId();
        List<SalesDtos.CustomerSummaryResponse> customers = customerService.searchCustomers(shopId, query);
        return ResponseEntity.ok(ApiResponse.ok(customers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDtos.Detail>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(insights.detail(CurrentUser.getShopId(), id)));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<CustomerDtos.NoteResponse>> addNote(
            @PathVariable Long id, @Valid @RequestBody CustomerDtos.NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                insights.addNote(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request.note())));
    }

    @GetMapping("/{id}/whatsapp-refill")
    public ResponseEntity<ApiResponse<KhataDtos.ReminderResponse>> whatsappRefill(
            @PathVariable Long id, @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(ApiResponse.ok(insights.whatsappRefill(CurrentUser.getShopId(), id, lang)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesDtos.CustomerResponse>> create(
            @RequestBody SalesDtos.CustomerRequest request) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.CustomerResponse customer = customerService.createCustomer(shopId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDtos.Detail>> update(
            @PathVariable Long id, @Valid @RequestBody CustomerDtos.ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(insights.update(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        customerService.deleteCustomer(shopId, id);
        return ResponseEntity.ok(ApiResponse.success(200, "Customer deleted"));
    }

    // ==================== Address Endpoints ====================

    @GetMapping("/{customerId}/addresses")
    public ResponseEntity<ApiResponse<List<SalesDtos.CustomerAddressResponse>>> getAddresses(
            @PathVariable Long customerId) {
        Long shopId = CurrentUser.getShopId();
        List<SalesDtos.CustomerAddressResponse> addresses = 
                customerService.getCustomerAddresses(shopId, customerId);
        return ResponseEntity.ok(ApiResponse.ok(addresses));
    }

    @GetMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<SalesDtos.CustomerAddressResponse>> getAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.CustomerAddressResponse address = 
                customerService.getCustomerAddress(shopId, customerId, addressId);
        return ResponseEntity.ok(ApiResponse.ok(address));
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<ApiResponse<SalesDtos.CustomerAddressResponse>> createAddress(
            @PathVariable Long customerId,
            @RequestBody SalesDtos.CustomerAddressRequest request) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.CustomerAddressResponse address = 
                customerService.createCustomerAddress(shopId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(address));
    }

    @PutMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<SalesDtos.CustomerAddressResponse>> updateAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId,
            @RequestBody SalesDtos.CustomerAddressRequest request) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.CustomerAddressResponse address = 
                customerService.updateCustomerAddress(shopId, customerId, addressId, request);
        return ResponseEntity.ok(ApiResponse.ok(address));
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        Long shopId = CurrentUser.getShopId();
        customerService.deleteCustomerAddress(shopId, customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(200, "Address deleted"));
    }
}
