package com.onda.marketplace.audit;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuditServiceTest {

    @Mock AdminAuditLogRepository repository;
    @Mock UserRepository          userRepository;

    AuditService service;

    @BeforeEach
    void setUp() { service = new AuditService(repository, userRepository); }

    @Test
    void registrar_capturaSnapshotDoNomeDoAdmin_ePersiste() {
        UUID adminId = UUID.randomUUID();
        UUID alvo    = UUID.randomUUID();
        User admin = User.builder().nome("Ana Admin").email("ana@x.com")
                .senhaHash("h").role(UserRole.ROLE_ADMIN).build();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);

        service.registrar(adminId, "SUSPENDER_USUARIO", "user", alvo, null);

        verify(repository).save(captor.capture());
        AdminAuditLog log = captor.getValue();
        assertThat(log.getAdminId()).isEqualTo(adminId);
        assertThat(log.getAdminNome()).isEqualTo("Ana Admin");
        assertThat(log.getAcao()).isEqualTo("SUSPENDER_USUARIO");
        assertThat(log.getEntidade()).isEqualTo("user");
        assertThat(log.getEntidadeId()).isEqualTo(alvo);
    }

    @Test
    void registrar_adminInexistente_usaFallbackDeNome() {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(adminId)).thenReturn(Optional.empty());
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);

        service.registrar(adminId, " X ", "y", null, null);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAdminNome()).isEqualTo("—");
    }

    @Test
    void listar_mapeiaEntidadesParaDto() {
        AdminAuditLog l = new AdminAuditLog(
                UUID.randomUUID(), "Ana", "CRIAR_CATEGORIA", "service_category", UUID.randomUUID(), "Elétrica");
        when(repository.findAllByOrderByCriadoEmDesc()).thenReturn(List.of(l));

        List<AdminAuditLogDto> r = service.listar();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).adminNome()).isEqualTo("Ana");
        assertThat(r.get(0).acao()).isEqualTo("CRIAR_CATEGORIA");
        assertThat(r.get(0).entidade()).isEqualTo("service_category");
    }
}
