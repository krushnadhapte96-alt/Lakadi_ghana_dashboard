package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.repository.CustomerAddressRepository;
import com.ghana.dashboard.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final UserService userService;

    public CustomerService(CustomerRepository customerRepository,
                          CustomerAddressRepository customerAddressRepository,
                          UserService userService) {
        this.customerRepository = customerRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.userService = userService;
    }

    // ==================== Customer CRUD ====================

    @Transactional(readOnly = true)
    public List<SalesDtos.CustomerResponse> getAllCustomers(Long shopId) {
        return customerRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId).stream()
                .map(this::toCustomerResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalesDtos.CustomerResponse getCustomer(Long shopId, Long id) {
        return customerRepository.findById(id)
                .filter(c -> c.getShopId().equals(shopId))
                .map(this::toCustomerResponse)
                .orElseThrow(() -> new DomainException(404, "Customer not found"));
    }

    @Transactional
    public SalesDtos.CustomerResponse createCustomer(Long shopId, SalesDtos.CustomerRequest request) {
        // Check if customer with this phone already exists for this shop
        if (StringUtils.hasText(request.phone()) && 
            customerRepository.existsByShopIdAndPhone(shopId, request.phone())) {
            throw new DomainException(409, "Customer with this phone already exists");
        }

        Customer customer = new Customer();
        customer.setShopId(shopId);
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setGstNo(request.gstNo());
        customer.setActive(true);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        customerRepository.save(customer);
        return toCustomerResponse(customer);
    }

    @Transactional
    public SalesDtos.CustomerResponse updateCustomer(Long shopId, Long id, SalesDtos.CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        // Check if phone is being changed to an existing one
        if (StringUtils.hasText(request.phone()) && 
            !request.phone().equals(customer.getPhone()) &&
            customerRepository.existsByShopIdAndPhone(shopId, request.phone())) {
            throw new DomainException(409, "Customer with this phone already exists");
        }

        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setGstNo(request.gstNo());
        customer.setUpdatedAt(LocalDateTime.now());

        customerRepository.save(customer);
        return toCustomerResponse(customer);
    }

    @Transactional
    public void deleteCustomer(Long shopId, Long id) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));
        customer.setActive(false);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public SalesDtos.CustomerResponse getOrCreateCustomerByPhone(Long shopId, String phone) {
        return customerRepository.findByShopIdAndPhone(shopId, phone)
                .map(this::toCustomerResponse)
                .orElseGet(() -> {
                    // Auto-create customer
                    SalesDtos.CustomerRequest request = new SalesDtos.CustomerRequest(
                            "Customer", phone, null, null);
                    return createCustomer(shopId, request);
                });
    }

    @Transactional(readOnly = true)
    public List<SalesDtos.CustomerSummaryResponse> searchCustomers(Long shopId, String query) {
        if (!StringUtils.hasText(query)) {
            return customerRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId).stream()
                    .map(this::toCustomerSummaryResponse)
                    .collect(Collectors.toList());
        }
        return customerRepository.findByShopIdAndNameContainingIgnoreCase(shopId, query).stream()
                .map(this::toCustomerSummaryResponse)
                .collect(Collectors.toList());
    }

    // ==================== Customer Address CRUD ====================

    @Transactional(readOnly = true)
    public List<SalesDtos.CustomerAddressResponse> getCustomerAddresses(Long shopId, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        return customerAddressRepository.findByCustomerOrderByIsDefaultDesc(customer).stream()
                .map(this::toCustomerAddressResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalesDtos.CustomerAddressResponse getCustomerAddress(Long shopId, Long customerId, Long addressId) {
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        return customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .map(this::toCustomerAddressResponse)
                .orElseThrow(() -> new DomainException(404, "Address not found"));
    }

    @Transactional
    public SalesDtos.CustomerAddressResponse createCustomerAddress(Long shopId, 
            SalesDtos.CustomerAddressRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        // If this is the first address, make it default
        boolean isDefault = customerAddressRepository.findByCustomerAndActiveTrue(customer).isEmpty();

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        address.setShopId(shopId);
        address.setAddressType(CustomerAddress.AddressType.valueOf(request.addressType()));
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        address.setLandmark(request.landmark());
        address.setDefault(isDefault || request.isDefault());
        address.setActive(true);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());

        customerAddressRepository.save(address);
        return toCustomerAddressResponse(address);
    }

    @Transactional
    public SalesDtos.CustomerAddressResponse updateCustomerAddress(Long shopId, Long customerId,
            Long addressId, SalesDtos.CustomerAddressRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new DomainException(404, "Address not found"));

        address.setAddressType(CustomerAddress.AddressType.valueOf(request.addressType()));
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        address.setLandmark(request.landmark());
        address.setDefault(request.isDefault());
        address.setUpdatedAt(LocalDateTime.now());

        // If setting as default, unset others
        if (request.isDefault()) {
            customerAddressRepository.findByCustomerAndActiveTrue(customer).stream()
                    .filter(a -> !a.getId().equals(addressId))
                    .forEach(a -> a.setDefault(false));
        }

        customerAddressRepository.save(address);
        return toCustomerAddressResponse(address);
    }

    @Transactional
    public void deleteCustomerAddress(Long shopId, Long customerId, Long addressId) {
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Customer not found"));

        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new DomainException(404, "Address not found"));

        address.setActive(false);
        address.setUpdatedAt(LocalDateTime.now());
        customerAddressRepository.save(address);
    }

    // ==================== Helper Methods ====================

    private SalesDtos.CustomerResponse toCustomerResponse(Customer customer) {
        return new SalesDtos.CustomerResponse(
                customer.getId(),
                customer.getShopId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getGstNo(),
                customer.isActive(),
                customer.getCreatedAt() != null ? customer.getCreatedAt().toString() : null
        );
    }

    private SalesDtos.CustomerSummaryResponse toCustomerSummaryResponse(Customer customer) {
        return new SalesDtos.CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail()
        );
    }

    private SalesDtos.CustomerAddressResponse toCustomerAddressResponse(CustomerAddress address) {
        return new SalesDtos.CustomerAddressResponse(
                address.getId(),
                address.getCustomer().getId(),
                address.getAddressType().name(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLandmark(),
                address.isDefault(),
                address.isActive(),
                address.getCreatedAt() != null ? address.getCreatedAt().toString() : null
        );
    }

    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Customer not found"));
    }

    @Transactional(readOnly = true)
    public CustomerAddress findAddressById(Long id) {
        return customerAddressRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Address not found"));
    }
}
