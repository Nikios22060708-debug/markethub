package com.markethub.web;

import com.jayway.jsonpath.JsonPath;
import com.markethub.logging.RequestLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Базовый класс интеграционных тестов: полный контекст Spring, H2 + миграции Flyway,
 * настоящая цепочка Spring Security с JWT. Каждый тест откатывается (@Transactional).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASSWORD = "admin123";
    protected static final String MANAGER = "manager";
    protected static final String MANAGER_PASSWORD = "manager123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestLoggingFilter requestLoggingFilter;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestLoggingFilter)
                .apply(springSecurity())
                .build();
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(result), "$.token");
    }

    protected String adminToken() throws Exception {
        return login(ADMIN, ADMIN_PASSWORD);
    }

    protected String managerToken() throws Exception {
        return login(MANAGER, MANAGER_PASSWORD);
    }

    /** Регистрирует нового покупателя и возвращает его токен. */
    protected String registerCustomer(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@test.local", "password": "secret123", "fullName": "Test"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.token");
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    protected ResultActions postJson(String url, String json, String token) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    protected long createdId(ResultActions actions) throws Exception {
        Number id = JsonPath.read(body(actions.andReturn()), "$.id");
        return id.longValue();
    }

    /** id товара из демо-каталога (миграция V3) по части названия. */
    protected long productIdByName(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/products").param("q", query))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(body(result), "$.content[0].id");
        return id.longValue();
    }

    protected int productStock(long productId) throws Exception {
        MvcResult result = mvc.perform(get("/api/products/" + productId)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(body(result), "$.stock");
    }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
