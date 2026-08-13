package com.onda.marketplace.discovery;

import com.onda.marketplace.provider.ProviderPublicService;
import com.onda.marketplace.servicerequest.ServiceRequestService;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiscoveryController.class)
@Import(TestSecurityConfig.class)
class DiscoveryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  DiscoveryService discoveryService;
    @MockBean  ServiceRequestService serviceRequestService;
    @MockBean  ProviderPublicService providerPublicService;

    @Test
    void nearby_validParams_returns200WithList() throws Exception {
        var dto = new NearbyProviderDto(UUID.randomUUID(), "Carlos", "ELETRICISTA", null, "VERIFICADO", null, 1234.5);
        when(discoveryService.findNearby(any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/v1/providers/nearby")
                        .param("lat", "-3.7319")
                        .param("lng", "-38.5267")
                        .param("raio", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("ELETRICISTA"))
                .andExpect(jsonPath("$[0].distanciaMetros").value(1234.5));
    }

    @Test
    void nearby_missingLatLng_returns400() throws Exception {
        mvc.perform(get("/api/v1/providers/nearby"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearby_emptyResult_returns200EmptyList() throws Exception {
        when(discoveryService.findNearby(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/providers/nearby")
                        .param("lat", "-3.7319")
                        .param("lng", "-38.5267"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void availableRequests_retornaFilaAberta() throws Exception {
        var dto = new com.onda.marketplace.servicerequest.AvailableRequestDto(
                UUID.randomUUID(), "Chuveiro sem funcionar", "Chuveiro sem funcionar",
                "ELETRICISTA", "PENDENTE", null, null, java.time.Instant.now());
        when(serviceRequestService.listarDisponiveis()).thenReturn(List.of(dto));

        mvc.perform(get("/api/v1/providers/available-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    void perfil_prestadorExistente_retorna200() throws Exception {
        UUID userId = UUID.randomUUID();
        var dto = new com.onda.marketplace.provider.ProviderPublicDto(
                userId, "Zé Elétrica", "Elétrica", "Eletricista há 12 anos",
                java.math.BigDecimal.valueOf(4.8), 10, 8, "VERIFICADO", List.of(),
                15, java.math.BigDecimal.valueOf(150), java.math.BigDecimal.valueOf(300));
        when(providerPublicService.buscarPorUserId(userId)).thenReturn(dto);

        mvc.perform(get("/api/v1/providers/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Zé Elétrica"))
                .andExpect(jsonPath("$.notaMedia").value(4.8));
    }
}
