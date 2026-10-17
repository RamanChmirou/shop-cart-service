package com.kanapa4.shop.cart;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=100")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@EnableWireMock(@ConfigureWireMock(name = "product-service", baseUrlProperties = "clients.product-service.url"))
class CartApiIntegrationTest {

    private static final String PRODUCT_ID = "11111111-0000-0000-0000-000000000001";
    private static final String QUOTE_PATH = "/products/" + PRODUCT_ID + "/price-quote";

    private static final String QUOTE_RESPONSE = """
            {
              "productId": "11111111-0000-0000-0000-000000000001",
              "productName": "Laptop Pro 15",
              "unitPrice": { "amount": 5199.00, "currency": "PLN", "vatRate": "VAT_23" },
              "selectedOptions": [
                { "groupCode": "PROCESSOR", "valueCode": "I7", "valueLabel": "Intel Core i7", "priceDelta": 800.00 },
                { "groupCode": "RAM", "valueCode": "RAM_32", "valueLabel": "32 GB", "priceDelta": 400.00 }
              ]
            }
            """;

    private static final String ADD_LAPTOP_BODY = """
            {
              "productId": "11111111-0000-0000-0000-000000000001",
              "quantity": %d,
              "selectedOptions": [
                { "groupCode": "PROCESSOR", "valueCode": "I7" },
                { "groupCode": "RAM", "valueCode": "RAM_32" }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @InjectWireMock("product-service")
    private WireMockServer productService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        productService.resetAll();
        userId = UUID.randomUUID();
    }

    private void givenProductServiceReturnsLaptopQuote() {
        productService.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(QUOTE_RESPONSE)));
    }

    private ResultActions addLaptop(int quantity) throws Exception {
        return mockMvc.perform(post("/carts/{userId}/items", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ADD_LAPTOP_BODY.formatted(quantity)));
    }

    private String addLaptopAndGetItemId(int quantity) throws Exception {
        givenProductServiceReturnsLaptopQuote();
        String body = addLaptop(quantity)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.items[0].id");
    }

    private int countItems() {
        return jdbcTemplate.queryForObject(
                "select count(*) from cart_items i join carts c on c.id = i.cart_id where c.user_id = ?",
                Integer.class, userId);
    }

    private int countCarts() {
        return jdbcTemplate.queryForObject(
                "select count(*) from carts where user_id = ?", Integer.class, userId);
    }

    @Test
    @DisplayName("GET cart of a new user is empty and nothing is stored")
    void getCart_NewUser_ReturnsEmptyCart() throws Exception {
        mockMvc.perform(get("/carts/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.currency").value("PLN"));

        assertThat(countCarts()).isZero();
    }

    @Test
    @DisplayName("POST item stores the cart, the price snapshot and the options as JSONB")
    void addItem_NewProduct_PersistsSnapshot() throws Exception {
        givenProductServiceReturnsLaptopQuote();

        addLaptop(2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Laptop Pro 15"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(10398.0));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select i.unit_price, i.currency, i.vat_rate, i.quantity, i.product_name "
                        + "from cart_items i join carts c on c.id = i.cart_id where c.user_id = ?", userId);
        assertThat((BigDecimal) row.get("unit_price")).isEqualByComparingTo("5199.00");
        assertThat(row.get("currency")).isEqualTo("PLN");
        assertThat(row.get("vat_rate")).isEqualTo("VAT_23");
        assertThat(row.get("quantity")).isEqualTo(2);

        String options = jdbcTemplate.queryForObject(
                "select i.selected_options::text from cart_items i join carts c on c.id = i.cart_id "
                        + "where c.user_id = ?", String.class, userId);
        assertThat(options).contains("PROCESSOR", "I7", "RAM_32", "800");

        mockMvc.perform(get("/carts/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].selectedOptions.length()").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(5199.0));
    }

    @Test
    @DisplayName("POST the same product twice keeps one row and sums the quantity")
    void addItem_SameProductTwice_MergesIntoOneRow() throws Exception {
        givenProductServiceReturnsLaptopQuote();

        addLaptop(1).andExpect(status().isCreated());
        addLaptop(2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(15597.0));

        assertThat(countItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH item changes the quantity in the database")
    void updateQuantity_PersistsNewQuantity() throws Exception {
        String itemId = addLaptopAndGetItemId(1);

        mockMvc.perform(patch("/carts/{userId}/items/{itemId}", userId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        mockMvc.perform(get("/carts/{userId}", userId))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.totalAmount").value(25995.0));
    }

    @Test
    @DisplayName("DELETE item removes the row")
    void removeItem_DeletesRow() throws Exception {
        String itemId = addLaptopAndGetItemId(1);

        mockMvc.perform(delete("/carts/{userId}/items/{itemId}", userId, itemId))
                .andExpect(status().isNoContent());

        assertThat(countItems()).isZero();
        mockMvc.perform(get("/carts/{userId}", userId))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("DELETE item of another user's cart gives 404 and keeps the row")
    void removeItem_OtherUsersItem_Return404() throws Exception {
        String itemId = addLaptopAndGetItemId(1);

        mockMvc.perform(delete("/carts/{userId}/items/{itemId}", UUID.randomUUID(), itemId))
                .andExpect(status().isNotFound());

        assertThat(countItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE cart removes all items")
    void clear_RemovesAllItems() throws Exception {
        addLaptopAndGetItemId(2);

        mockMvc.perform(delete("/carts/{userId}", userId))
                .andExpect(status().isNoContent());

        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("product not found: 404 and no cart is created")
    void addItem_ProductNotFound_Return404AndStoresNothing() throws Exception {
        productService.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Product not found: " + PRODUCT_ID + "\",\"status\":404}")));

        addLaptop(1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: " + PRODUCT_ID));

        assertThat(countCarts()).isZero();
    }

    @Test
    @DisplayName("product-service down: 503 after retries and no cart is created")
    void addItem_ProductServiceDown_Return503AndStoresNothing() throws Exception {
        productService.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(503)));

        addLaptop(1)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("product-service is currently unavailable"));

        assertThat(countCarts()).isZero();
    }

    @Test
    @DisplayName("database allows only one ACTIVE cart per user")
    void database_SecondActiveCartForUser_IsRejected() {
        String insert = "insert into carts (id, user_id, status, version) values (?, ?, 'ACTIVE', 0)";
        jdbcTemplate.update(insert, UUID.randomUUID(), userId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a CHECKED_OUT cart does not block a new ACTIVE cart")
    void addItem_AfterCheckout_StartsNewCart() throws Exception {
        addLaptopAndGetItemId(2);
        jdbcTemplate.update("update carts set status = 'CHECKED_OUT' where user_id = ?", userId);

        addLaptop(1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1));

        assertThat(countCarts()).isEqualTo(2);
    }
}