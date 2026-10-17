package com.kanapa4.shop.cart.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.kanapa4.shop.cart.TestcontainersConfiguration;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.dto.SelectedOptionRequest;
import com.kanapa4.shop.cart.exception.InvalidProductSelectionException;
import com.kanapa4.shop.cart.exception.ProductNotFoundException;
import com.kanapa4.shop.cart.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.kanapa4.shop.cart.testdata.CartFixtures.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=100")
@Import(TestcontainersConfiguration.class)
@EnableWireMock(@ConfigureWireMock(name = "product-service", baseUrlProperties = "clients.product-service.url"))
class ProductClientTest {

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

    private static final PriceQuoteRequest REQUEST = new PriceQuoteRequest(List.of(
            new SelectedOptionRequest("PROCESSOR", "I7"),
            new SelectedOptionRequest("RAM", "RAM_32")));

    @InjectWireMock("product-service")
    private WireMockServer wireMock;

    @Autowired
    private ProductClient productClient;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    private void verifyRequestCount(int expected) {
        wireMock.verify(expected, postRequestedFor(urlPathEqualTo(QUOTE_PATH)));
    }

    @Test
    @DisplayName("200: the quote is mapped and the selected options are sent in the request body")
    void getPriceQuote_ProductExists_ReturnsQuote() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(QUOTE_RESPONSE)));

        PriceQuoteResponse response = productClient.getPriceQuote(PRODUCT_ID, REQUEST);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.productName()).isEqualTo("Laptop Pro 15");
        assertThat(response.unitPrice().amount()).isEqualByComparingTo("5199.00");
        assertThat(response.unitPrice().currency()).isEqualTo("PLN");
        assertThat(response.selectedOptions()).hasSize(2);
        wireMock.verify(postRequestedFor(urlPathEqualTo(QUOTE_PATH))
                .withRequestBody(equalToJson("""
                        { "selectedOptions": [
                            { "groupCode": "PROCESSOR", "valueCode": "I7" },
                            { "groupCode": "RAM", "valueCode": "RAM_32" } ] }
                        """)));
    }

    @Test
    @DisplayName("404: ProductNotFoundException with the detail from product-service, no retries")
    void getPriceQuote_ProductNotFound_ThrowsProductNotFound() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Product not found: " + PRODUCT_ID + "\",\"status\":404}")));

        assertThatThrownBy(() -> productClient.getPriceQuote(PRODUCT_ID, REQUEST))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: " + PRODUCT_ID);
        verifyRequestCount(1);
    }

    @Test
    @DisplayName("400: InvalidProductSelectionException with the detail from product-service, no retries")
    void getPriceQuote_InvalidOptions_ThrowsInvalidSelection() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Unknown option value NOPE\",\"status\":400}")));

        assertThatThrownBy(() -> productClient.getPriceQuote(PRODUCT_ID, REQUEST))
                .isInstanceOf(InvalidProductSelectionException.class)
                .hasMessage("Unknown option value NOPE");
        verifyRequestCount(1);
    }

    @Test
    @DisplayName("503: three attempts, then the fallback returns ServiceUnavailableException")
    void getPriceQuote_ServiceUnavailable_RetriesAndFallsBack() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> productClient.getPriceQuote(PRODUCT_ID, REQUEST))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyRequestCount(3);
    }

    @Test
    @DisplayName("connection reset: three attempts, then the fallback returns ServiceUnavailableException")
    void getPriceQuote_ConnectionReset_RetriesAndFallsBack() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> productClient.getPriceQuote(PRODUCT_ID, REQUEST))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyRequestCount(3);
    }

    @Test
    @DisplayName("500: no retries, the fallback returns ServiceUnavailableException")
    void getPriceQuote_ServerError_FallsBackWithoutRetry() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> productClient.getPriceQuote(PRODUCT_ID, REQUEST))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyRequestCount(1);
    }

    @Test
    @DisplayName("two 503 and then 200: the retry succeeds")
    void getPriceQuote_TemporaryFailure_RetrySucceeds() {
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .inScenario("temporary failure")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .inScenario("temporary failure")
                .whenScenarioStateIs("second attempt")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("third attempt"));
        wireMock.stubFor(post(urlPathEqualTo(QUOTE_PATH))
                .inScenario("temporary failure")
                .whenScenarioStateIs("third attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(QUOTE_RESPONSE)));

        PriceQuoteResponse response = productClient.getPriceQuote(PRODUCT_ID, REQUEST);

        assertThat(response.productName()).isEqualTo("Laptop Pro 15");
        verifyRequestCount(3);
    }
}