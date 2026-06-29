package com.onda.marketplace.audit;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Trilha de auditoria de ações administrativas (US22 / TS09).
 *
 * <p>Append-only: registra quem/o quê/quando e nunca atualiza nem remove. O nome
 * do admin é capturado como snapshot no momento da ação.
 */
@Service
@SuppressWarnings("null")
public class AuditService {

    private final AdminAuditLogRepository repository;
    private final UserRepository          userRepository;

    public AuditService(AdminAuditLogRepository repository, UserRepository userRepository) {
        this.repository     = repository;
        this.userRepository = userRepository;
    }

    /**
     * Registra uma ação administrativa.
     *
     * @param adminId    autor da ação (do token JWT)
     * @param acao       verbo da ação (ex.: {@code SUSPENDER_USUARIO})
     * @param entidade   tipo do alvo (ex.: {@code user}, {@code provider})
     * @param entidadeId id do alvo (pode ser nulo)
     * @param detalhe    contexto opcional (ex.: a decisão/ação aplicada)
     */
    @Transactional
    public void registrar(UUID adminId, String acao, String entidade, UUID entidadeId, String detalhe) {
        String nome = userRepository.findById(adminId).map(User::getNome).orElse("—");
        repository.save(new AdminAuditLog(adminId, nome, acao, entidade, entidadeId, detalhe));
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogDto> listar() {
        return repository.findAllByOrderByCriadoEmDesc().stream()
                .map(AdminAuditLogDto::from)
                .toList();
    }
}
