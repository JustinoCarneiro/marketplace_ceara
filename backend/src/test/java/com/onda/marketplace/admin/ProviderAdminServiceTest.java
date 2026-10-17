package com.onda.marketplace.admin;

import com.onda.marketplace.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderAdminServiceTest {
    @Mock ProviderProfileRepository repository;
    ProviderAdminService service;
    @BeforeEach void setup() { service=new ProviderAdminService(repository); }
    @Test void listar_preservaUserIdNomeEStatusSemCarregarEntidadesRelacionadas() {
        UUID user=UUID.randomUUID();
        when(repository.findSummaries()).thenReturn(List.of(new ProviderSummary(user,"João Prestador","eletrica",ProviderStatus.VERIFICADO,null,true)));
        var rows=service.listar(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).isEqualTo(new ProviderAdminDto(user,"João Prestador","eletrica","VERIFICADO",null,true));
        verify(repository,never()).findSummariesByStatus(any());
    }
    @Test void listar_expoDuplicataEConciliadoSemConfundirEstados() {
        when(repository.findSummaries()).thenReturn(List.of(
                new ProviderSummary(UUID.randomUUID(),"Duda Duplicada","eletrica",ProviderStatus.VERIFICADO,null,false),
                new ProviderSummary(UUID.randomUUID(),"Caio Conciliado","eletrica",ProviderStatus.VERIFICADO,null,true)));
        assertThat(service.listar(null)).extracting(ProviderAdminDto::nome,ProviderAdminDto::cpfConciliado)
                .containsExactly(tuple("Duda Duplicada",false),tuple("Caio Conciliado",true));
    }
    @Test void listar_comFiltroPreservaConsulta() {
        when(repository.findSummariesByStatus(ProviderStatus.EM_VERIFICACAO)).thenReturn(List.of());
        assertThat(service.listar(ProviderStatus.EM_VERIFICACAO)).isEmpty();
        verify(repository,never()).findSummaries();
    }
}
