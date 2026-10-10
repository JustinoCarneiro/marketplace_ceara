package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() { service = new ProviderAdminService(repository); }

    @Test
    void listar_semFiltro_usaFindAllWithUser_eMapeiaUserId() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getNome()).thenReturn("João Prestador");
        ProviderProfile p = mock(ProviderProfile.class);
        when(p.getUser()).thenReturn(user);
        when(p.getCategoria()).thenReturn("eletrica");
        when(p.getStatusVerificacao()).thenReturn(ProviderStatus.VERIFICADO);
        when(p.getNotaMedia()).thenReturn(null);
        when(repository.findAllWithUser()).thenReturn(List.of(p));

        List<ProviderAdminDto> r = service.listar(null);

        assertThat(r).hasSize(1);
        // id do DTO é o userId (não o id do perfil) — exigido pelas ações verify/reject
        assertThat(r.get(0).id()).isEqualTo(userId);
        assertThat(r.get(0).nome()).isEqualTo("João Prestador");
        assertThat(r.get(0).statusVerificacao()).isEqualTo("VERIFICADO");
        verify(repository, never()).findByStatusWithUser(any());
    }

    @Test
    void listar_expoOCpfConciliadoDeCadaPerfil_eDuplicataNaoSeConfundeComConciliado() {
        // Um perfil de cada: se o mapeamento fixasse o valor (sempre true ou sempre false), um dos dois falharia. É o campo que
        // acende o aviso "CPF em duplicidade" e o botão de conciliar no painel.
        ProviderProfile duplicata = perfil("Duda Duplicada", false);
        ProviderProfile conciliado = perfil("Caio Conciliado", true);
        when(repository.findAllWithUser()).thenReturn(List.of(duplicata, conciliado));

        List<ProviderAdminDto> r = service.listar(null);

        assertThat(r).extracting(ProviderAdminDto::nome, ProviderAdminDto::cpfConciliado)
                .containsExactly(tuple("Duda Duplicada", false), tuple("Caio Conciliado", true));
    }

    private static ProviderProfile perfil(String nome, boolean cpfConciliado) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getNome()).thenReturn(nome);
        ProviderProfile p = mock(ProviderProfile.class);
        when(p.getUser()).thenReturn(user);
        when(p.getCategoria()).thenReturn("eletrica");
        when(p.getStatusVerificacao()).thenReturn(ProviderStatus.VERIFICADO);
        when(p.getNotaMedia()).thenReturn(null);
        when(p.isCpfConciliado()).thenReturn(cpfConciliado);
        return p;
    }

    @Test
    void listar_comFiltro_usaFindByStatusWithUser() {
        when(repository.findByStatusWithUser(ProviderStatus.EM_VERIFICACAO)).thenReturn(List.of());

        service.listar(ProviderStatus.EM_VERIFICACAO);

        verify(repository).findByStatusWithUser(ProviderStatus.EM_VERIFICACAO);
        verify(repository, never()).findAllWithUser();
    }
}
