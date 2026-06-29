package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestão de usuários pelo admin (US26): listar, suspender e reativar.
 *
 * <p>A suspensão usa o flag {@code ativo} do próprio usuário (não há tabela de
 * status à parte). Um administrador nunca pode ser suspenso (evita lockout).
 */
@Service
@SuppressWarnings("null")
public class UserAdminService {

    private final UserRepository userRepository;

    public UserAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Lista usuários, com busca opcional por nome/e-mail (US26). */
    @Transactional(readOnly = true)
    public List<UserAdminDto> listar(String q) {
        String termo = (q == null) ? "" : q.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> termo.isEmpty()
                        || u.getNome().toLowerCase().contains(termo)
                        || u.getEmail().toLowerCase().contains(termo))
                .map(UserAdminDto::from)
                .toList();
    }

    @Transactional
    public void suspender(UUID id) {
        User user = buscar(id);
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new BusinessException("CANNOT_SUSPEND_ADMIN",
                    "Não é permitido suspender um administrador.");
        }
        user.suspender();
        userRepository.save(user);
    }

    @Transactional
    public void reativar(UUID id) {
        User user = buscar(id);
        user.reativar();
        userRepository.save(user);
    }

    private User buscar(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Usuário não encontrado."));
    }
}
