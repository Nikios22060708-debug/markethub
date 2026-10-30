package com.markethub.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminIntegrationTest extends AbstractIntegrationTest {

    @Test
    void onlyAdminCanManageUsers() throws Exception {
        String customer = registerCustomer("curious");
        perform(get("/api/users"), customer).andExpect(status().isForbidden());
        perform(get("/api/users"), managerToken()).andExpect(status().isForbidden());
        perform(get("/api/roles"), customer).andExpect(status().isForbidden());
    }

    @Test
    void adminUserCrud() throws Exception {
        String admin = adminToken();

        perform(get("/api/roles"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        long id = createdId(postJson("/api/users", """
                {"username": "seller", "email": "seller@test.local", "password": "secret123", "roles": ["MANAGER"]}
                """, admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("MANAGER")));

        perform(get("/api/users").param("size", "50"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", hasItem("seller")));
        perform(get("/api/users/" + id), admin).andExpect(jsonPath("$.email").value("seller@test.local"));

        perform(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "seller2@test.local", "fullName": "Продавец", "enabled": false}
                """), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        perform(put("/api/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"roles": ["CUSTOMER", "MANAGER"]}
                """), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(2)));

        perform(put("/api/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"roles": ["SUPERUSER"]}
                """), admin).andExpect(status().isBadRequest());

        perform(delete("/api/users/" + id), admin).andExpect(status().isNoContent());
        perform(get("/api/users/" + id), admin).andExpect(status().isNotFound());
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        String admin = adminToken();
        long id = createdId(postJson("/api/users", """
                {"username": "blocked", "email": "blocked@test.local", "password": "secret123"}
                """, admin));
        perform(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "blocked@test.local", "enabled": false}
                """), admin).andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "blocked", "password": "secret123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDeleteThemselves() throws Exception {
        String admin = adminToken();
        Number adminId = com.jayway.jsonpath.JsonPath.read(
                body(perform(get("/api/auth/me"), admin).andReturn()), "$.id");

        perform(delete("/api/users/" + adminId.longValue()), admin).andExpect(status().isBadRequest());
    }
}
