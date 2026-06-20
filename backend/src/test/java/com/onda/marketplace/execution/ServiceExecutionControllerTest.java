package com.onda.marketplace.execution;

import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceExecutionController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class ServiceExecutionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  ServiceExecutionService executionService;

    private static final UUID SR_ID = UUID.randomUUID();

    @Test
    void start_returns200() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/start", SR_ID).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void confirmCompletion_returns200() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/confirm-completion", SR_ID).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void openDispute_returns200() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/dispute", SR_ID).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_returns200() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/cancel", SR_ID).with(csrf()))
                .andExpect(status().isOk());
    }
}
