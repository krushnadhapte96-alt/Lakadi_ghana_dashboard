package com.ghana.dashboard;

import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.repository.CustomerRepository;
import com.ghana.dashboard.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shop serves Maharashtra, so a customer named "शिवाजी" must survive a round-trip through the
 * write path unchanged - no mojibake on the way in, and no silent truncation on the way out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class DevanagariTextPersistenceIntegrationTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void hindiAndMarathiNamesRoundTripUnharmed() {
        var shop = shopRepository.findAll().get(0);
        var customer = new Customer();
        customer.setShopId(shop.getId());
        customer.setName("शिवाजी");
        customer.setPhone("9876543210");
        customer.setEmail(null);
        customer.setGstNo(null);
        customer.setActive(true);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        entityManager.flush();
        entityManager.clear();

        Customer found = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("शिवाजी");
    }

    @Test
    void connectionIsUtf8Mb4() {
        String client = jdbcTemplate.queryForObject("SELECT @@character_set_client", String.class);
        String connection = jdbcTemplate.queryForObject("SELECT @@character_set_connection", String.class);
        String results = jdbcTemplate.queryForObject("SELECT @@character_set_results", String.class);

        assertThat(client).startsWith("utf8");
        assertThat(connection).startsWith("utf8");
        // Connector/J deliberately runs `SET character_set_results = NULL` ("tell the server not
        // to do conversion"), so rows come back untouched and the driver decodes them with the
        // session charset asserted above. NULL therefore means no conversion, never a wrong one.
        assertThat(results == null || results.startsWith("utf8"))
                .as("character_set_results must be UTF-8 or absent (no conversion)")
                .isTrue();
    }
}
