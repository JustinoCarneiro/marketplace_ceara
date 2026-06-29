package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UserAdminServiceTest {

    @Mock UserRepository userRepository;

    UserAdminService service;

    @BeforeEach
    void setUp() { service = new UserAdminService(userRepository); }

    private static User user(String nome, String email, UserRole role) {
        return User.builder().nome(nome).email(email).senhaHash("hash").role(role).build();
    }

    @Test
    void suspender_usuarioComum_desativaESalva() {
        UUID id = UUID.randomUUID();
        User u = user("Cliente", "c@x.com", UserRole.ROLE_CLIENT);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.suspender(id);

        assertThat(u.isAtivo()).isFalse();
        verify(userRepository).save(u);
    }

    @Test
    void suspender_admin_lancaException_eNaoSalva() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user("Admin", "a@x.com", UserRole.ROLE_ADMIN)));

        assertThatThrownBy(() -> service.suspender(id))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CANNOT_SUSPEND_ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void reativar_ativaESalva() {
        UUID id = UUID.randomUUID();
        User u = user("Cliente", "c@x.com", UserRole.ROLE_CLIENT);
        u.suspender();
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.reativar(id);

        assertThat(u.isAtivo()).isTrue();
        verify(userRepository).save(u);
    }

    @Test
    void suspender_usuarioInexistente_lancaException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspender(id))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void listar_filtraPorNomeOuEmail() {
        when(userRepository.findAll()).thenReturn(List.of(
                user("Maria Silva", "maria@x.com", UserRole.ROLE_CLIENT),
                user("João Souza", "joao@x.com", UserRole.ROLE_PROVIDER)));

        List<UserAdminDto> r = service.listar("maria");

        assertThat(r).hasSize(1);
        assertThat(r.get(0).email()).isEqualTo("maria@x.com");
        assertThat(r.get(0).status()).isEqualTo("ATIVO");
    }
}
