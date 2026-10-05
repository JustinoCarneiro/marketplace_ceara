package com.onda.marketplace.auth;

import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderProfiles;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exclusão de conta (US36). As regras de decisão ficam aqui; o que depende de SQL de verdade (o efeito
 * dos UPDATE/DELETE em lote em cada tabela) é provado no E2E contra o Postgres — repositório mockado
 * não executa JPQL.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AccountDeletionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SENHA = "senha1234";
    private static final String HASH = "$2a$hash-da-senha";
    private static final Set<ServiceRequestStatus> EM_CURSO = Set.of(
            ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO, ServiceRequestStatus.EM_DISPUTA);

    @Mock UserRepository            userRepository;
    @Mock ProviderProfileRepository profileRepository;
    @Mock AccountDeletionRepository exclusao;
    @Mock PasswordEncoder           passwordEncoder;
    @Mock ApplicationEventPublisher eventos;

    AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(userRepository, profileRepository, exclusao,
                passwordEncoder, eventos);
    }

    // ---------- apoio ----------

    private User cadastrado(UserRole role) {
        return cadastradoCom(USER_ID, role);
    }

    private User cadastradoCom(UUID id, UserRole role) {
        User u = User.builder().nome("Maria Silva").email("maria@exemplo.com")
                .senhaHash(HASH).role(role).build();
        u.setCpfHash("hmac-do-cpf");
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        // a leitura é com trava de linha: dois cliques seguidos se enfileiram em vez de rodarem juntos
        when(userRepository.findByIdComTrava(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void senhaCorreta() {
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(true);
    }

    private void semPerfilDePrestador() {
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    }

    private void semNenhumaPendencia() {
        when(exclusao.clienteTemPedidoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.clienteTemReembolsoPendente(USER_ID)).thenReturn(false);
        when(exclusao.prestadorTemServicoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.prestadorTemRepasseAReceber(USER_ID)).thenReturn(false);
    }

    /** Uma recusa não pode deixar rastro: nenhuma limpeza, nenhum aviso, nenhuma sessão derrubada. */
    private void nadaFoiApagado() {
        verify(exclusao, never()).encerrarPropostasAtivasDoPrestador(any());
        verify(exclusao, never()).encerrarPropostasAtivasDosPedidosDoCliente(any());
        verify(exclusao, never()).cancelarPedidosSemCompromissoDoCliente(any(), any());
        verify(exclusao, never()).apagarDadosPessoaisDosPedidosDoCliente(any());
        verify(exclusao, never()).apagarMotivoDeDisputaDosPedidosOndeEhPrestador(any());
        verify(exclusao, never()).sanearBairroForaDaListaDosPedidosDoCliente(any(), any());
        verify(exclusao, never()).apagarMidiaDosPedidosDoCliente(any());
        verify(exclusao, never()).removerConteudoDasMensagensDoUsuario(any());
        verify(exclusao, never()).removerComentariosDasAvaliacoesDoUsuario(any());
        verify(exclusao, never()).apagarSessoesDoUsuario(any());
        verify(exclusao, never()).apagarCodigosDeRecuperacaoDoUsuario(any());
        verifyNoInteractions(eventos);
    }

    // ---------- recusas ----------

    @Test
    void usuarioInexistente_lancaUserNotFound() {
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void administrador_naoExcluiAPropriaConta() {
        cadastrado(UserRole.ROLE_ADMIN);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ADMIN_CANNOT_DELETE");
        verifyNoInteractions(passwordEncoder);
        nadaFoiApagado();
    }

    @Test
    void senhaIncorreta_recusa_eNadaMuda() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PASSWORD");

        assertThat(u.isExcluido()).isFalse();
        assertThat(u.getEmail()).isEqualTo("maria@exemplo.com");
        // a senha errada barra ANTES de qualquer consulta de pendência
        verify(exclusao, never()).clienteTemPedidoEm(any(), anyCollection());
        nadaFoiApagado();
    }

    @Test
    void clienteComPedidoAceitoEmAndamentoOuEmDisputa_recusa() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        when(exclusao.clienteTemPedidoEm(USER_ID, EM_CURSO)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_HAS_ACTIVE_ORDERS")
                .hasMessageContaining("aceitos, em andamento ou em disputa");

        assertThat(u.isExcluido()).isFalse();
        nadaFoiApagado();
    }

    @Test
    void clienteComReembolsoPendente_recusa() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        when(exclusao.clienteTemPedidoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.clienteTemReembolsoPendente(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_HAS_ACTIVE_ORDERS")
                .hasMessageContaining("reembolso");

        assertThat(u.isExcluido()).isFalse();
        nadaFoiApagado();
    }

    @Test
    void prestadorComServicoAceitoEmAndamentoOuEmDisputa_recusa() {
        User u = cadastrado(UserRole.ROLE_PROVIDER);
        senhaCorreta();
        when(exclusao.clienteTemPedidoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.clienteTemReembolsoPendente(USER_ID)).thenReturn(false);
        when(exclusao.prestadorTemServicoEm(USER_ID, EM_CURSO)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_HAS_ACTIVE_ORDERS")
                .hasMessageContaining("aceitos, em andamento ou em disputa");

        assertThat(u.isExcluido()).isFalse();
        nadaFoiApagado();
    }

    @Test
    void prestadorComRepassePendente_recusa_paraNaoApagarAChavePixDeQuemAindaTemAReceber() {
        User u = cadastrado(UserRole.ROLE_PROVIDER);
        senhaCorreta();
        when(exclusao.clienteTemPedidoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.clienteTemReembolsoPendente(USER_ID)).thenReturn(false);
        when(exclusao.prestadorTemServicoEm(any(), anyCollection())).thenReturn(false);
        when(exclusao.prestadorTemRepasseAReceber(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_HAS_ACTIVE_ORDERS")
                .hasMessageContaining("repasse");

        assertThat(u.isExcluido()).isFalse();
        nadaFoiApagado();
    }

    // ---------- sucesso ----------

    @Test
    void cliente_exclui_limpaOQueEraDele_eAnonimizaAConta() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        semNenhumaPendencia();
        semPerfilDePrestador();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash-inutilizavel");

        service.excluir(USER_ID, SENHA);

        verify(exclusao).encerrarPropostasAtivasDosPedidosDoCliente(USER_ID);
        verify(exclusao).cancelarPedidosSemCompromissoDoCliente(any(), any(Instant.class));
        verify(exclusao).apagarDadosPessoaisDosPedidosDoCliente(USER_ID);
        // achado da revisão cruzada (2026-10-05): o texto de disputa que o usuário escreveu como PRESTADOR
        // (não cliente) também precisa sair — openDispute aceita qualquer uma das duas partes
        verify(exclusao).apagarMotivoDeDisputaDosPedidosOndeEhPrestador(USER_ID);
        verify(exclusao).sanearBairroForaDaListaDosPedidosDoCliente(eq(USER_ID), any());
        verify(exclusao).apagarMidiaDosPedidosDoCliente(USER_ID);
        verify(exclusao).removerConteudoDasMensagensDoUsuario(USER_ID);
        verify(exclusao).removerComentariosDasAvaliacoesDoUsuario(USER_ID);
        verify(exclusao).apagarSessoesDoUsuario(USER_ID);
        verify(exclusao).apagarCodigosDeRecuperacaoDoUsuario(USER_ID);

        assertThat(u.isExcluido()).isTrue();
        assertThat(u.isAtivo()).isFalse();
        assertThat(u.getNome()).isEqualTo("Usuário removido");
        assertThat(u.getEmail()).matches("removido-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}@excluido\\.invalid");
        // aleatório, não derivado do id (que é público): ninguém consegue cadastrar o endereço antes e travar a exclusão
        assertThat(u.getEmail()).doesNotContain(USER_ID.toString());
        assertThat(u.getSenhaHash()).isEqualTo("$2a$hash-inutilizavel");
        assertThat(u.getCpfHash()).isNull();        // conta limpa: pode voltar com o mesmo CPF
    }

    @Test
    void cancelaOsPedidosSemCompromisso_cravandoOInstanteDaExclusao() {
        cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        semNenhumaPendencia();
        semPerfilDePrestador();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");
        Instant antes = Instant.now();

        service.excluir(USER_ID, SENHA);

        // o UPDATE em lote não dispara o @PreUpdate: sem o instante explícito, o updated_at ficaria velho
        ArgumentCaptor<Instant> agora = ArgumentCaptor.forClass(Instant.class);
        verify(exclusao).cancelarPedidosSemCompromissoDoCliente(any(), agora.capture());
        assertThat(agora.getValue()).isBetween(antes, Instant.now());
    }

    @Test
    void cliente_exclui_avisaPorEmail_comOEmailENomeOriginais() {
        cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        semNenhumaPendencia();
        semPerfilDePrestador();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        ArgumentCaptor<AccountDeleted> evento = ArgumentCaptor.forClass(AccountDeleted.class);
        verify(eventos).publishEvent(evento.capture());
        // capturados ANTES de anonimizar: depois, o e-mail e o nome já não existem
        assertThat(evento.getValue().email()).isEqualTo("maria@exemplo.com");
        assertThat(evento.getValue().nome()).isEqualTo("Maria Silva");
        assertThat(evento.getValue().toString()).doesNotContain("maria@exemplo.com").doesNotContain("Maria");
    }

    @Test
    void senhaInutilizada_naoEhASenhaDoUsuario() {
        cadastrado(UserRole.ROLE_CLIENT);
        senhaCorreta();
        semNenhumaPendencia();
        semPerfilDePrestador();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        // o hash novo vem de um segredo aleatório que ninguém conhece — nunca da senha digitada
        ArgumentCaptor<String> segredo = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(segredo.capture());
        assertThat(segredo.getValue()).isNotEqualTo(SENHA).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void senhaInutilizada_ehAleatoria_duasExclusoesNaoCompartilhamSegredo() {
        // uma constante no código-fonte (que é público) deixaria toda conta excluída com a mesma senha conhecida
        UUID outro = UUID.randomUUID();
        User a = cadastrado(UserRole.ROLE_CLIENT);
        User b = cadastradoCom(outro, UserRole.ROLE_CLIENT);
        senhaCorreta();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);
        service.excluir(outro, SENHA);

        ArgumentCaptor<String> segredos = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).encode(segredos.capture());
        assertThat(segredos.getAllValues()).doesNotHaveDuplicates();
        // o mesmo vale para o e-mail anônimo: nunca repete (a coluna é UNIQUE)
        assertThat(a.getEmail()).isNotEqualTo(b.getEmail());
    }

    @Test
    void contaSuspensa_mantemOHashDoCpf_antifraude() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        u.suspender();
        senhaCorreta();
        semNenhumaPendencia();
        semPerfilDePrestador();
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        assertThat(u.isExcluido()).isTrue();
        assertThat(u.getCpfHash()).isEqualTo("hmac-do-cpf");
    }

    @Test
    void prestadorReprovado_mantemOHashDoCpf_antifraude() {
        User u = cadastrado(UserRole.ROLE_PROVIDER);
        senhaCorreta();
        semNenhumaPendencia();
        when(profileRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(ProviderProfiles.comStatus(ProviderStatus.REPROVADO)));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        assertThat(u.getCpfHash()).isEqualTo("hmac-do-cpf");
    }

    @Test
    void prestadorSuspensoPelaModeracao_mantemOHashDoCpf_antifraude() {
        // a CONTA está ativa; o que foi barrado foi o prestador — excluir também não pode burlar isso
        User u = cadastrado(UserRole.ROLE_PROVIDER);
        senhaCorreta();
        semNenhumaPendencia();
        when(profileRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(ProviderProfiles.comStatus(ProviderStatus.SUSPENSO)));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        assertThat(u.getCpfHash()).isEqualTo("hmac-do-cpf");
    }

    @Test
    void prestadorVerificado_exclui_fechaPropostasAtivas_eAnonimizaOPerfil() {
        User u = cadastrado(UserRole.ROLE_PROVIDER);
        ProviderProfile perfil = ProviderProfiles.comStatus(ProviderStatus.VERIFICADO);
        perfil.setBio("Eletricista há 12 anos");
        perfil.setChavePixCifrada("chave-cifrada");
        senhaCorreta();
        semNenhumaPendencia();
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(perfil));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

        service.excluir(USER_ID, SENHA);

        verify(exclusao).encerrarPropostasAtivasDoPrestador(USER_ID);
        assertThat(perfil.getBio()).isNull();
        assertThat(perfil.getChavePixCifrada()).isNull();
        assertThat(perfil.getStatusVerificacao()).isEqualTo(ProviderStatus.SUSPENSO);
        assertThat(u.getCpfHash()).isNull();   // prestador sem histórico de banimento: sem retenção
    }

    @Test
    void contaJaExcluida_naoFazNada() {
        User u = cadastrado(UserRole.ROLE_CLIENT);
        u.anonimizar("removido@excluido.invalid", "$2a$x", false);

        service.excluir(USER_ID, SENHA);

        // idempotente: nem pede senha, nem limpa de novo, nem reenvia o aviso
        verifyNoInteractions(passwordEncoder, profileRepository);
        nadaFoiApagado();
        verify(exclusao, never()).clienteTemPedidoEm(any(), anyCollection());
    }
}
