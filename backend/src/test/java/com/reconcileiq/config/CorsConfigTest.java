package com.reconcileiq.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconcileiq.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(CorsConfig.class)
class CorsConfigTest {

    private static final String PROD = "https://reconcileiq-frontend.onrender.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void simpleRequestFromProductionOriginGetsCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Origin", PROD))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD));
    }

    @Test
    void localDevOriginIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightWithContentTypeAndAuthorizationSucceeds() throws Exception {
        // The test controller only maps GET, so the preflight asks for GET.
        // Preflight matching follows Access-Control-Request-Method.
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", PROD)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
