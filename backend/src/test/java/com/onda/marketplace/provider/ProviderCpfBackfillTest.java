package com.onda.marketplace.provider;

import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prestadores cadastrados antes de o cadastro gravar o hash do CPF só têm o CPF cifrado. O backfill lê esse CPF, grava o
 * hash e deixa a unicidade valer para eles também; e, numa rotação da chave do HMAC, regrava com a chave atual o hash de
 * quem o tem numa versão anterior (o prestador é o único que se refaz sozinho: o CPF dele existe cifrado). Idempotente, e
 * um registro ruim não derruba o resto.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProviderCpfBackfillTest {

    private static final String CHAVE        = "01234567890123456789012345678901";
    private static final String CHAVE_ATUAL  = "test-cpf-hmac-key-0123456789-0123456789";
    private static final String CHAVE_ANTIGA = "test-cpf-aes256-key-32-chars-here!";

    @Mock ProviderProfileRepository profileRepository;
    @Mock UserRepository            userRepository;

    final CpfEncryptor   cifra = new CpfEncryptor(CHAVE);
    final CpfHashService hash  = new CpfHashService(CHAVE_ATUAL, 2, CHAVE_ANTIGA);
    ProviderCpfBackfill backfill;

    @BeforeEach
    void setUp() {
        backfill = new ProviderCpfBackfill(profileRepository, userRepository, cifra, hash,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), true);
    }

    private ProviderCpfBackfill.PerfilSemHash sem(UUID userId, String cpfCifrado) {
        return new ProviderCpfBackfill.PerfilSemHash() {
            public UUID getUserId() { return userId; }
            public String getCpfCifrado() { return cpfCifrado; }
        };
    }

    private User usuario() {
        User u = User.builder().nome("Carlos").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    /** Prestador de antes da separação das chaves: o hash é da chave ANTIGA, na versão 1. */
    private User usuarioComHashAntigo(String cpf) {
        User u = usuario();
        u.vincularCpf(new CpfHashService(CHAVE_ANTIGA, 1).hash(cpf), 1);
        return u;
    }

    @Test
    void semPerfisPendentes_naoFazNada() {
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of());

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(r.duplicados()).isEmpty();
        assertThat(r.ilegiveis()).isZero();
    }

    @Test
    void vinculaOHashDoCpfDecifrado_comAChaveEAVersaoAtuais_ofHashDosDigitos_comOuSemMascara() {
        User u = usuario();
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(sem(u.getId(), cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHashInAndIdNot(any(), eq(u.getId()))).thenReturn(false);

        var r = backfill.preencher();

        assertThat(r.vinculados()).isEqualTo(1);
        assertThat(u.getCpfHash()).isEqualTo(hash.hash("11144477735"));
        assertThat(u.getCpfHashVersao()).isEqualTo(2);
        verify(userRepository).save(u);
    }

    @Test
    void hashDeChaveAntiga_eRegravadoComAChaveAtual_eNaoContaComoDuplicataDeSiMesmo() {
        // rotação do HMAC: o prestador se refaz sozinho (decifra o CPF). A consulta de duplicata ignora a PRÓPRIA conta —
        // o hash antigo dela bateria com a chave anterior e seria lido como "outra conta com este CPF".
        User u = usuarioComHashAntigo("11144477735");
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(sem(u.getId(), cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHashInAndIdNot(any(), eq(u.getId()))).thenReturn(false);

        var r = backfill.preencher();

        assertThat(r.vinculados()).isEqualTo(1);
        assertThat(u.getCpfHash()).isEqualTo(hash.hash("11144477735"));
        assertThat(u.getCpfHashVersao()).isEqualTo(2);
        verify(userRepository).save(u);
    }

    @Test
    void cpfJaVinculadoAOutraConta_naoGrava_eAvisaQualConta_consultandoAsDuasChaves() {
        // dois prestadores legados com o mesmo CPF: o segundo não ganha o hash e fica listado para decisão humana
        User u = usuario();
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(sem(u.getId(), cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHashInAndIdNot(any(), eq(u.getId()))).thenReturn(true);

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(r.duplicados()).containsExactly(u.getId());
        verify(userRepository, never()).save(any());
        @SuppressWarnings("unchecked") ArgumentCaptor<Collection<String>> hashes = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository).existsByCpfHashInAndIdNot(hashes.capture(), eq(u.getId()));
        assertThat(hashes.getValue()).containsExactlyInAnyOrder(
                hash.hash("11144477735"), new CpfHashService(CHAVE_ANTIGA, 1).hash("11144477735"));
    }

    @Test
    void cpfCifradoIlegivel_oSeedGravaUmPlaceholder_ePassaAoProximo() {
        User bom = usuario();
        UUID ruim = UUID.randomUUID();
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(
                sem(ruim, "seed-cpf-cifrado-placeholder"), sem(bom.getId(), cifra.encrypt("529.982.247-25"))));
        when(userRepository.findById(bom.getId())).thenReturn(Optional.of(bom));
        when(userRepository.existsByCpfHashInAndIdNot(any(), eq(bom.getId()))).thenReturn(false);

        var r = backfill.preencher();

        assertThat(r.ilegiveis()).isEqualTo(1);
        assertThat(r.vinculados()).isEqualTo(1);   // um registro ruim não derruba o resto
    }

    @Test
    void corridaNaRestricaoUnica_contaComoDuplicado_enaoDerrubaOBackfill() {
        User u = usuario();
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(sem(u.getId(), cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHashInAndIdNot(any(), eq(u.getId()))).thenReturn(false);
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_users_cpf_hash"));

        var r = backfill.preencher();

        assertThat(r.duplicados()).containsExactly(u.getId());
        assertThat(r.vinculados()).isZero();
    }

    @Test
    void contaQueJaTemOHashDaChaveAtual_noMeioDoCaminho_naoEhRefeita() {
        User u = usuario();
        u.vincularCpf("hash-ja-gravado", 2);
        when(profileRepository.semHashDoCpf(2)).thenReturn(List.of(sem(u.getId(), cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(u.getCpfHash()).isEqualTo("hash-ja-gravado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void falhaDoBackfill_nuncaImpedeASubidaDaAplicacao() {
        // um ApplicationRunner que lança derruba a subida: o backfill é de conveniência, tenta de novo na próxima subida
        when(profileRepository.semHashDoCpf(2)).thenThrow(new RuntimeException("banco indisponível"));

        org.assertj.core.api.Assertions.assertThatCode(() -> backfill.run(null)).doesNotThrowAnyException();
    }

    @Test
    void desligado_naoRodaNoArranque() throws Exception {
        var desligado = new ProviderCpfBackfill(profileRepository, userRepository, cifra, hash,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), false);

        desligado.run(null);

        org.mockito.Mockito.verifyNoInteractions(profileRepository);
    }
}
