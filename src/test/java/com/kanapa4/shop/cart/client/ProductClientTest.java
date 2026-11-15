package com.kanapa4.shop.cart.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.kanapa4.shop.cart.TestcontainersConfiguration;
import com.kanapa4.shop.cart.util.TestFileUtils;
import com.kanapa4.shop.cart.dto.*;
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
import static com.kanapa4.shop.cart.testdata.CartFixtures.PHONE_ID;
import static com.kanapa4.shop.cart.testdata.CartFixtures.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=100")
@Import(TestcontainersConfiguration.class)
@EnableWireMock(@ConfigureWireMock(name = "product-service", baseUrlProperties = "clients.product-service.url"))
class ProductClientTest {

    private static final String QUOTE_PATH = "/products/" + PRODUCT_ID + "/price-quote";
    private static final String QUOTE_RESPONSE = TestFileUtils.readJson("stubs/price-quote-laptop.json");
    private static final String BATCH_PATH = "/products/price-quotes";
    private static final String BATCH_RESPONSE = TestFileUtils.readJson("stubs/batch-quotes.json");
    private static final String QUOTE_REQUEST_JSON = TestFileUtils.readJson("requests/price-quote-request.json");
    private static final String BATCH_REQUEST_JSON = TestFileUtils.readJson("requests/batch-price-quotes-request.json");

    private static final BatchPriceQuoteRequest BATCH_REQUEST = new BatchPriceQuoteRequest(List.of(
            new BatchPriceQuoteItemRequest(PRODUCT_ID, List.of(
                    new SelectedOptionRequest("PROCESSOR", "I7"),
                    new SelectedOptionRequest("RAM", "RAM_32"))),
            new BatchPriceQuoteItemRequest(PHONE_ID, List.of())));
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
                .withRequestBody(equalToJson(QUOTE_REQUEST_JSON)));
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

    private void verifyBatchRequestCount(int expected) {
        wireMock.verify(expected, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    @DisplayName("batch 200: quotes are mapped in order and the items are sent in the request body")
    void getPriceQuotes_ProductsExist_ReturnsQuotesInOrder() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(BATCH_RESPONSE)));

        BatchPriceQuoteResponse response = productClient.getPriceQuotes(BATCH_REQUEST);

        assertThat(response.quotes()).hasSize(2);
        assertThat(response.quotes().getFirst().productName()).isEqualTo("Laptop Pro 15");
        assertThat(response.quotes().get(0).unitPrice().amount()).isEqualByComparingTo("5199.00");
        assertThat(response.quotes().get(0).selectedOptions()).hasSize(2);
        assertThat(response.quotes().get(1).productId()).isEqualTo(PHONE_ID);
        assertThat(response.quotes().get(1).unitPrice().amount()).isEqualByComparingTo("2499.00");
        wireMock.verify(postRequestedFor(urlPathEqualTo(BATCH_PATH))
                .withRequestBody(equalToJson(BATCH_REQUEST_JSON)));
    }

    @Test
    @DisplayName("batch 404: ProductNotFoundException with the detail from product-service, no retries")
    void getPriceQuotes_ProductNotFound_ThrowsProductNotFound() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Product not found: " + PHONE_ID + "\",\"status\":404}")));

        assertThatThrownBy(() -> productClient.getPriceQuotes(BATCH_REQUEST))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: " + PHONE_ID);
        verifyBatchRequestCount(1);
    }

    @Test
    @DisplayName("batch 400: InvalidProductSelectionException with the detail from product-service, no retries")
    void getPriceQuotes_InvalidOptions_ThrowsInvalidSelection() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Unknown option value NOPE\",\"status\":400}")));

        assertThatThrownBy(() -> productClient.getPriceQuotes(BATCH_REQUEST))
                .isInstanceOf(InvalidProductSelectionException.class)
                .hasMessage("Unknown option value NOPE");
        verifyBatchRequestCount(1);
    }

    @Test
    @DisplayName("batch 503: three attempts, then the fallback returns ServiceUnavailableException")
    void getPriceQuotes_ServiceUnavailable_RetriesAndFallsBack() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> productClient.getPriceQuotes(BATCH_REQUEST))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyBatchRequestCount(3);
    }

    @Test
    @DisplayName("batch 500: no retries, the fallback returns ServiceUnavailableException")
    void getPriceQuotes_ServerError_FallsBackWithoutRetry() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> productClient.getPriceQuotes(BATCH_REQUEST))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyBatchRequestCount(1);
    }
}