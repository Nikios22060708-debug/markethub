package com.markethub.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий покупки: адрес → заказ → оплата → доставка → отзыв, плюс отмены и проверки доступа.
 */
class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String ADDRESS_JSON = """
            {"label": "Дом", "city": "Казань", "street": "Баумана, 1", "zipCode": "420111"}
            """;

    @Test
    void fullPurchaseLifecycle() throws Exception {
        String customer = registerCustomer("buyer");
        String manager = managerToken();
        long productId = productIdByName("BassPods");
        int stockBefore = productStock(productId);

        long addressId = createdId(postJson("/api/addresses", ADDRESS_JSON, customer).andExpect(status().isCreated()));

        long orderId = createdId(postJson("/api/orders", orderJson(addressId, productId, 2), customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalAmount").value(25980.0))
                .andExpect(jsonPath("$.items", hasSize(1))));
        assertThat(productStock(productId)).isEqualTo(stockBefore - 2);

        long paymentId = createdId(postJson("/api/payments", """
                {"orderId": %d, "method": "CARD"}
                """.formatted(orderId), customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(25980.0)));

        postJson("/api/payments", """
                {"orderId": %d, "method": "CARD"}
                """.formatted(orderId), customer).andExpect(status().isConflict());

        perform(get("/api/payments/" + paymentId), customer).andExpect(status().isOk());
        perform(get("/api/payments"), customer).andExpect(status().isForbidden());
        perform(get("/api/payments"), manager).andExpect(status().isOk());

        changeStatus(orderId, "SHIPPED", customer).andExpect(status().isForbidden());
        changeStatus(orderId, "SHIPPED", manager).andExpect(jsonPath("$.status").value("SHIPPED"));
        changeStatus(orderId, "DELIVERED", manager).andExpect(jsonPath("$.status").value("DELIVERED"));
        changeStatus(orderId, "NEW", manager).andExpect(status().isConflict());

        perform(post("/api/orders/" + orderId + "/cancel"), customer).andExpect(status().isConflict());

        long reviewId = createdId(postJson("/api/products/" + productId + "/reviews", """
                {"rating": 5, "comment": "Отличный звук"}
                """, customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(true)));

        postJson("/api/products/" + productId + "/reviews", """
                {"rating": 1}
                """, customer).andExpect(status().isConflict());

        mvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        perform(put("/api/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rating": 4, "comment": "Хорошо"}
                        """), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4));
        mvc.perform(get("/api/reviews/" + reviewId)).andExpect(jsonPath("$.rating").value(4));
        perform(delete("/api/reviews/" + reviewId), manager).andExpect(status().isNoContent());

        // Товар есть в заказе — удаление снимает его с продажи
        perform(delete("/api/products/" + productId), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void cancellingOrderRestoresStockAndRefundsPayment() throws Exception {
        String customer = registerCustomer("canceller");
        long productId = productIdByName("GaN");
        int stockBefore = productStock(productId);
        long addressId = createdId(postJson("/api/addresses", ADDRESS_JSON, customer));
        long orderId = createdId(postJson("/api/orders", orderJson(addressId, productId, 3), customer));
        long paymentId = createdId(postJson("/api/payments", """
                {"orderId": %d, "method": "SBP"}
                """.formatted(orderId), customer));

        perform(post("/api/orders/" + orderId + "/cancel"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(productStock(productId)).isEqualTo(stockBefore);
        perform(get("/api/payments/" + paymentId), customer).andExpect(jsonPath("$.status").value("REFUNDED"));

        String admin = adminToken();
        perform(delete("/api/orders/" + orderId), customer).andExpect(status().isForbidden());
        perform(delete("/api/orders/" + orderId), admin).andExpect(status().isNoContent());
        perform(get("/api/orders/" + orderId), admin).andExpect(status().isNotFound());
    }

    @Test
    void managerRefundCancelsPaidOrder() throws Exception {
        String customer = registerCustomer("refunder");
        long productId = productIdByName("Lite");
        long addressId = createdId(postJson("/api/addresses", ADDRESS_JSON, customer));
        long orderId = createdId(postJson("/api/orders", orderJson(addressId, productId, 1), customer));
        long paymentId = createdId(postJson("/api/payments", """
                {"orderId": %d, "method": "CARD"}
                """.formatted(orderId), customer));

        perform(post("/api/payments/" + paymentId + "/refund"), customer).andExpect(status().isForbidden());
        perform(post("/api/payments/" + paymentId + "/refund"), managerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        perform(get("/api/orders/" + orderId), customer).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customersAreIsolatedFromEachOther() throws Exception {
        String alice = registerCustomer("alice");
        String bob = registerCustomer("bob");
        long productId = productIdByName("Lite");
        long aliceAddress = createdId(postJson("/api/addresses", ADDRESS_JSON, alice));
        long aliceOrder = createdId(postJson("/api/orders", orderJson(aliceAddress, productId, 1), alice));

        perform(get("/api/orders/" + aliceOrder), bob).andExpect(status().isForbidden());
        perform(get("/api/addresses/" + aliceAddress), bob).andExpect(status().isForbidden());
        postJson("/api/orders", orderJson(aliceAddress, productId, 1), bob).andExpect(status().isForbidden());
        postJson("/api/payments", """
                {"orderId": %d, "method": "CARD"}
                """.formatted(aliceOrder), bob).andExpect(status().isForbidden());

        perform(get("/api/orders"), bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        perform(get("/api/orders").param("status", "NEW"), alice)
                .andExpect(jsonPath("$.content", hasSize(1)));
        perform(get("/api/orders").param("status", "NEW"), managerToken())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("NEW"))));
    }

    @Test
    void addressCrudAndOrderAddressChange() throws Exception {
        String customer = registerCustomer("mover");
        long first = createdId(postJson("/api/addresses", ADDRESS_JSON, customer));
        long second = createdId(postJson("/api/addresses", """
                {"label": "Работа", "city": "Москва", "street": "Арбат, 10", "zipCode": "119002"}
                """, customer));

        perform(get("/api/addresses"), customer).andExpect(jsonPath("$", hasSize(2)));
        perform(put("/api/addresses/" + second).contentType(MediaType.APPLICATION_JSON).content("""
                {"label": "Офис", "city": "Москва", "street": "Арбат, 12", "zipCode": "119002"}
                """), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.street").value("Арбат, 12"));

        long orderId = createdId(postJson("/api/orders", orderJson(first, productIdByName("Lite"), 1), customer));
        perform(put("/api/orders/" + orderId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\": " + second + "}"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(second));

        perform(delete("/api/addresses/" + second), customer).andExpect(status().isConflict());
        perform(delete("/api/addresses/" + first), customer).andExpect(status().isNoContent());
        perform(get("/api/addresses/" + first), customer).andExpect(status().isNotFound());
    }

    @Test
    void orderValidationAndStockLimits() throws Exception {
        String customer = registerCustomer("greedy");
        long addressId = createdId(postJson("/api/addresses", ADDRESS_JSON, customer));
        long productId = productIdByName("ProBook");

        postJson("/api/orders", """
                {"addressId": %d, "items": []}
                """.formatted(addressId), customer).andExpect(status().isBadRequest());
        postJson("/api/orders", orderJson(addressId, productId, 999), customer).andExpect(status().isConflict());
        postJson("/api/orders", orderJson(addressId, 999_999, 1), customer).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(long orderId, String status, String token)
            throws Exception {
        return perform(patch("/api/orders/" + orderId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}"), token);
    }

    private static String orderJson(long addressId, long productId, int quantity) {
        return """
                {"addressId": %d, "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(addressId, productId, quantity);
    }
}
