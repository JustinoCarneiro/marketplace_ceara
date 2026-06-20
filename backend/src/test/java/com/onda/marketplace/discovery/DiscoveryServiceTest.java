package com.onda.marketplace.discovery;

import com.onda.marketplace.provider.ProviderProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock ProviderProfileRepository profileRepository;

    DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(profileRepository);
    }

    @Test
    void findNearby_returnsProjectedDtos() {
        NearbyProviderView view = mockView(UUID.randomUUID(), "João", "ENCANADOR", 800.0);
        when(profileRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), isNull(), anyInt()))
                .thenReturn(List.of(view));

        var query = new NearbyQuery(-3.7319, -38.5267, 5000.0, null, 20);
        List<NearbyProviderDto> result = discoveryService.findNearby(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoria()).isEqualTo("ENCANADOR");
        assertThat(result.get(0).distanciaMetros()).isEqualTo(800.0);
    }

    @Test
    void findNearby_comCategoria_passaFiltroAoRepository() {
        when(profileRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), eq("ELETRICISTA"), anyInt()))
                .thenReturn(List.of());

        var query = new NearbyQuery(-3.7319, -38.5267, 2000.0, "ELETRICISTA", 10);
        discoveryService.findNearby(query);

        verify(profileRepository).findNearby(-3.7319, -38.5267, 2000.0, "ELETRICISTA", 10);
    }

    private NearbyProviderView mockView(UUID id, String nome, String categoria, double distancia) {
        return new NearbyProviderView() {
            public UUID getId()                  { return id; }
            public String getNome()              { return nome; }
            public String getCategoria()         { return categoria; }
            public String getBio()               { return null; }
            public String getStatusVerificacao() { return "VERIFICADO"; }
            public java.math.BigDecimal getNotaMedia() { return null; }
            public Double getDistanciaMetros()   { return distancia; }
        };
    }
}
