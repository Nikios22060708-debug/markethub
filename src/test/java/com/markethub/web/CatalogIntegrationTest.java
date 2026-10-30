package com.markethub.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String CATEGORY_JSON = """
            {"name": "%s", "description": "test"}
            """;

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(6)));
    }

    @Test
    void productSearchFiltersPaginatesAndSorts() throws Exception {
        mvc.perform(get("/api/products")
                        .param("q", "phone")
                        .param("sort", "price,desc")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Phone X Max"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/api/products").param("minPrice", "1000").param("maxPrice", "20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].price", everyItem(lessThanOrEqualTo(20000.0))));
    }

    @Test
    void invalidSearchParametersAreBadRequest() throws Exception {
        mvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort", "noSuchField,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("categoryId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotModifyCatalog() throws Exception {
        String token = registerCustomer("shopper");
        postJson("/api/categories", CATEGORY_JSON.formatted("Хаки"), token).andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(CATEGORY_JSON.formatted("Аноним")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerManagesCategories() throws Exception {
        String token = managerToken();
        long id = createdId(postJson("/api/categories", CATEGORY_JSON.formatted("Планшеты"), token)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location")));

        postJson("/api/categories", CATEGORY_JSON.formatted("Планшеты"), token).andExpect(status().isConflict());

        perform(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(CATEGORY_JSON.formatted("Tablets")), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tablets"));

        mvc.perform(get("/api/categories/" + id)).andExpect(status().isOk());
        perform(delete("/api/categories/" + id), token).andExpect(status().isNoContent());
        mvc.perform(get("/api/categories/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        String token = managerToken();
        long categoryId = createdId(postJson("/api/categories", CATEGORY_JSON.formatted("Камеры"), token));
        postJson("/api/products", productJson("Камера", "15000.00", 3, categoryId), token)
                .andExpect(status().isCreated());

        perform(delete("/api/categories/" + categoryId), token).andExpect(status().isConflict());
    }

    @Test
    void managerManagesProducts() throws Exception {
        String token = managerToken();
        long categoryId = createdId(postJson("/api/categories", CATEGORY_JSON.formatted("Мониторы"), token));

        long productId = createdId(postJson("/api/products", productJson("Monitor 27", "25000.00", 4, categoryId), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Мониторы"))
                .andExpect(jsonPath("$.reviewCount").value(0)));

        perform(put("/api/products/" + productId).contentType(MediaType.APPLICATION_JSON)
                .content(productJson("Monitor 27 Pro", "27000.00", 6, categoryId)), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Monitor 27 Pro"))
                .andExpect(jsonPath("$.stock").value(6));

        perform(delete("/api/products/" + productId), token).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
    }

    @Test
    void productValidationAndMissingCategory() throws Exception {
        String token = managerToken();
        postJson("/api/products", """
                {"name": "", "price": -1, "stock": -5, "categoryId": null}
                """, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..errors.name").isNotEmpty())
                .andExpect(jsonPath("$..errors.price").isNotEmpty());

        postJson("/api/products", productJson("Ghost", "10.00", 1, 999_999), token)
                .andExpect(status().isNotFound());
    }

    static String productJson(String name, String price, int stock, long categoryId) {
        return """
                {"name": "%s", "description": "test product", "price": %s, "stock": %d, "categoryId": %d}
                """.formatted(name, price, stock, categoryId);
    }
}
