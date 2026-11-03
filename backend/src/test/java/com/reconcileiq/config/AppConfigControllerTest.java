package com.reconcileiq.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppConfigController.class)
@Import(ReconcileIqConfig.class)
class AppConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configEndpointDefaultsToDemoMode() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSource").value("DEMO_SCENARIOS"))
                .andExpect(jsonPath("$.source").value("DEMO"))
                .andExpect(jsonPath("$.label").value("Demo scenarios"));
    }
}
