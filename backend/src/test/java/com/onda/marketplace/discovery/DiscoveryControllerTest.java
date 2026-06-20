package com.onda.marketplace.discovery;

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
}
