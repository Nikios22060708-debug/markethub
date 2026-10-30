package com.markethub.web;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthAndInfoArePublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("MarketHub"));
    }

    @Test
    void metricsRequireAdmin() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        perform(get("/actuator/metrics"), registerCustomer("spy")).andExpect(status().isForbidden());

        String admin = adminToken();
        perform(get("/actuator/metrics"), admin).andExpect(status().isOk());
        perform(get("/actuator/metrics/markethub.orders.created"), admin).andExpect(status().isOk());
    }
}
