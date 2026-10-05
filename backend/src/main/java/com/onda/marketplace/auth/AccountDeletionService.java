package com.onda.marketplace.auth;

import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exclusão de conta pelo próprio usuário (US36, LGPD art. 18, VI). Em vez de apagar a linha de
 * {@code users} — transações, pedidos, propostas, mensagens e o aceite de termos (imutável) apontam para
 * ela —, a conta é <b>anonimizada no lugar</b>: tudo que identifica a pessoa sai e o acesso se encerra; o
 * histórico financeiro e de reputação fica sem identificação. A lista do que sai e do que fica está em
 * {@link AccountDeletionRepository}; as premissas jurídicas, em {@code docs/PENDENCIAS_JURIDICAS.md}.
 *
 * <p>Tudo numa transação só: ou a conta inteira é anonimizada, ou nada muda. O aviso por e-mail só sai
 * depois do commit ({@link AccountDeletedMailListener}) e nunca desfaz a exclusão.
 */
@Service
@SuppressWarnings("null")
public class AccountDeletionService {

    /** Pedidos nestes status têm a outra parte esperando (e, em geral, dinheiro em jogo). */
    private static final Set<ServiceRequestStatus> EM_CURSO = Set.of(
            ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO, ServiceRequestStatus.EM_DISPUTA);

    private final UserRepository            userRepository;
    private final ProviderProfileRepository profileRepository;
    private final AccountDeletionRepository exclusao;
    private final PasswordEncoder           passwordEncoder;
    private final ApplicationEventPublisher eventos;

    public AccountDeletionService(UserRepository userRepository,
                                  ProviderProfileRepository profileRepository,
                                  AccountDeletionRepository exclusao,
                                  PasswordEncoder passwordEncoder,
                                  ApplicationEventPublisher eventos) {
        this.userRepository    = userRepository;
        this.profileRepository = profileRepository;
        this.exclusao          = exclusao;
        this.passwordEncoder   = passwordEncoder;
        this.eventos           = eventos;
    }

    @Transactional
    public void excluir(UUID userId, String senha) {
        // Com trava: o toque duplo no botão não roda a limpeza (nem manda o e-mail) duas vezes.
        User user = userRepository.findByIdComTrava(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));

        if (user.isExcluido()) {
            return;
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new BusinessException("ADMIN_CANNOT_DELETE",
                    "Administradores não excluem a conta por aqui.");
        }
        if (!passwordEncoder.matches(senha, user.getSenhaHash())) {
            throw new BusinessException("INVALID_PASSWORD", "Senha incorreta.");
        }
        exigirSemPendencias(userId);

        Optional<ProviderProfile> perfil = profileRepository.findByUserId(userId);
        // Antifraude: excluir a conta não pode ser um jeito de burlar um banimento. Decidido ANTES de
        // anonimizar o perfil, que passa a SUSPENSO.
        boolean manterCpfHash = !user.isAtivo() || perfil.map(this::barrado).orElse(false);
        String email = user.getEmail();
        String nome  = user.getNome();

        apagarDadosVinculados(userId);
        perfil.ifPresent(ProviderProfile::anonimizar);
        user.anonimizar(emailAnonimo(), senhaInutilizada(), manterCpfHash);

        eventos.publishEvent(new AccountDeleted(email, nome));
    }

    /** Recusa, sem apagar nada, o que deixaria alguém no escuro ou dinheiro sem destino. */
    private void exigirSemPendencias(UUID userId) {
        if (exclusao.clienteTemPedidoEm(userId, EM_CURSO)) {
            throw pendencia("Você tem pedidos aceitos, em andamento ou em disputa. "
                    + "Conclua ou cancele esses pedidos antes de excluir a conta.");
        }
        if (exclusao.clienteTemReembolsoPendente(userId)) {
            throw pendencia("Há um reembolso seu em processamento. "
                    + "Aguarde a conclusão para excluir a conta.");
        }
        if (exclusao.prestadorTemServicoEm(userId, EM_CURSO)) {
            throw pendencia("Você tem serviços aceitos, em andamento ou em disputa. "
                    + "Conclua-os antes de excluir a conta.");
        }
        if (exclusao.prestadorTemRepasseAReceber(userId)) {
            throw pendencia("Há um repasse a receber em processamento. Aguarde o pagamento para excluir a "
                    + "conta: apagar a chave Pix deixaria o dinheiro sem destino.");
        }
    }

    private static BusinessException pendencia(String mensagem) {
        return new BusinessException("ACCOUNT_HAS_ACTIVE_ORDERS", mensagem);
    }

    private boolean barrado(ProviderProfile perfil) {
        return perfil.getStatusVerificacao() == ProviderStatus.REPROVADO
                || perfil.getStatusVerificacao() == ProviderStatus.SUSPENSO;
    }

    private void apagarDadosVinculados(UUID userId) {
        exclusao.encerrarPropostasAtivasDoPrestador(userId);
        exclusao.encerrarPropostasAtivasDosPedidosDoCliente(userId);
        exclusao.cancelarPedidosSemCompromissoDoCliente(userId, Instant.now());
        exclusao.apagarDadosPessoaisDosPedidosDoCliente(userId);
        exclusao.apagarMidiaDosPedidosDoCliente(userId);
        exclusao.removerConteudoDasMensagensDoUsuario(userId);
        exclusao.removerComentariosDasAvaliacoesDoUsuario(userId);
        exclusao.apagarSessoesDoUsuario(userId);
        exclusao.apagarCodigosDeRecuperacaoDoUsuario(userId);
    }

    /**
     * Único (a coluna é UNIQUE) e num domínio reservado ({@code .invalid}, RFC 2606): nunca entrega e-mail.
     * Aleatório, NÃO derivado do id: o id de um prestador é público, e um endereço previsível deixaria alguém
     * cadastrá-lo antes e travar a exclusão da conta alheia.
     */
    private static String emailAnonimo() {
        return "removido-" + UUID.randomUUID() + "@excluido.invalid";
    }

    /**
     * Hash de um segredo aleatório que ninguém conhece: a coluna é NOT NULL e a conta nunca mais autentica.
     * Um UUID só (36 caracteres): o BCrypt considera no máximo 72 bytes.
     */
    private String senhaInutilizada() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }
}
