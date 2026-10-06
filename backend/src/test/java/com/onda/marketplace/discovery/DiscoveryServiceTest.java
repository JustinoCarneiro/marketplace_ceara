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
        when(profileRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), isNull(), anyInt(), any(UUID.class)))
                .thenReturn(List.of(view));

        var query = new NearbyQuery(-3.7319, -38.5267, 5000.0, null, 20);
        List<NearbyProviderDto> result = discoveryService.findNearby(query, UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoria()).isEqualTo("ENCANADOR");
        assertThat(result.get(0).distanciaMetros()).isEqualTo(800.0);
    }

    @Test
    void findNearby_comCategoria_passaFiltroAoRepository() {
        when(profileRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), eq("ELETRICISTA"), anyInt(), any(UUID.class)))
                .thenReturn(List.of());
        UUID quemBusca = UUID.randomUUID();

        var query = new NearbyQuery(-3.7319, -38.5267, 2000.0, "ELETRICISTA", 10);
        discoveryService.findNearby(query, quemBusca);

        verify(profileRepository).findNearby(-3.7319, -38.5267, 2000.0, "ELETRICISTA", 10, quemBusca);
    }

    @Test
    void findNearby_passaQuemBuscaAoRepository_aExclusaoDoProprioUsuarioEhNaPropriaConsulta() {
        // Achado da revisão cruzada (2026-10-05): a exclusão do próprio usuário mudou de lugar — era um filtro em
        // Java DEPOIS do LIMIT (com limite=1, se ele fosse o 1º resultado, o filtro o removia e não trazia o 2º: a
        // busca voltava vazia havendo outro prestador próximo). Agora é WHERE na própria consulta, antes do LIMIT —
        // o efeito é coisa do Postgres (índice GiST, ProviderProfileRepository), aqui só se prova que o id chega lá.
        UUID euMesmo = UUID.randomUUID();
        when(profileRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), isNull(), anyInt(), eq(euMesmo)))
                .thenReturn(List.of());

        discoveryService.findNearby(new NearbyQuery(-3.7319, -38.5267, 5000.0, null, 20), euMesmo);

        verify(profileRepository).findNearby(-3.7319, -38.5267, 5000.0, null, 20, euMesmo);
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
