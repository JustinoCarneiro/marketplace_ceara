package com.onda.marketplace.proposal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProposalController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class ProposalControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  ProposalService proposalService;

    private static final UUID SR_ID   = UUID.randomUUID();
    private static final UUID PROP_ID = UUID.randomUUID();

    @Test
    void createProposal_validPayload_returns201() throws Exception {
        var dto = new ProposalDto(PROP_ID, SR_ID, UUID.randomUUID(), BigDecimal.valueOf(250), 3, "ATIVA", Instant.now());
        when(proposalService.create(eq(SR_ID), any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/service-requests/{id}/proposals", SR_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateProposalRequest(BigDecimal.valueOf(250), 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(250))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    void createProposal_missingValor_returns422() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/proposals", SR_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prazoDias\":3}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listProposals_returns200WithList() throws Exception {
        var dto = new ProposalDto(PROP_ID, SR_ID, UUID.randomUUID(), BigDecimal.valueOf(250), 3, "ATIVA", Instant.now());
        when(proposalService.listForRequest(SR_ID)).thenReturn(List.of(dto));

        mvc.perform(get("/api/v1/service-requests/{id}/proposals", SR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ATIVA"));
    }

    @Test
    void acceptProposal_returns200() throws Exception {
        var dto = new ProposalDto(PROP_ID, SR_ID, UUID.randomUUID(), BigDecimal.valueOf(250), 3, "ACEITA", Instant.now());
        when(proposalService.accept(eq(PROP_ID), any())).thenReturn(dto);

        mvc.perform(put("/api/v1/proposals/{id}/accept", PROP_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITA"));
    }

    @Test
    void rejectProposal_returns200() throws Exception {
        var dto = new ProposalDto(PROP_ID, SR_ID, UUID.randomUUID(), BigDecimal.valueOf(250), 3, "RECUSADA", Instant.now());
        when(proposalService.reject(eq(PROP_ID), any())).thenReturn(dto);

        mvc.perform(put("/api/v1/proposals/{id}/reject", PROP_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));
    }
}
