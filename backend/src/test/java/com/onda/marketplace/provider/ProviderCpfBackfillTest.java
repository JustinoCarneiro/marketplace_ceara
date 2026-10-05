package com.onda.marketplace.provider;

import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prestadores cadastrados antes de o cadastro gravar o hash do CPF só têm o CPF cifrado. O backfill lê esse CPF, grava o
 * hash e deixa a unicidade valer para eles também. Idempotente, e um registro ruim não derruba o resto.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProviderCpfBackfillTest {

    private static final String CHAVE = "01234567890123456789012345678901";

    @Mock ProviderProfileRepository profileRepository;
    @Mock UserRepository            userRepository;

    final CpfEncryptor   cifra = new CpfEncryptor(CHAVE);
    final CpfHashService hash  = new CpfHashService("chave-hmac-de-teste-com-mais-de-32-caracteres!");
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
        return User.builder().nome("Carlos").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
    }

    @Test
    void semPerfisPendentes_naoFazNada() {
        when(profileRepository.semHashDoCpf()).thenReturn(List.of());

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(r.duplicados()).isEmpty();
        assertThat(r.ilegiveis()).isZero();
    }

    @Test
    void vinculaOHashDoCpfDecifrado_ofHashDosDigitos_comOuSemMascara() {
        UUID id = UUID.randomUUID();
        User u = usuario();
        when(profileRepository.semHashDoCpf()).thenReturn(List.of(sem(id, cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHash(any())).thenReturn(false);

        var r = backfill.preencher();

        assertThat(r.vinculados()).isEqualTo(1);
        assertThat(u.getCpfHash()).isEqualTo(hash.hash("11144477735"));
        verify(userRepository).save(u);
    }

    @Test
    void cpfJaVinculadoAOutraConta_naoGrava_eAvisaQualConta() {
        // dois prestadores legados com o mesmo CPF: o segundo não ganha o hash e fica listado para decisão humana
        UUID id = UUID.randomUUID();
        when(profileRepository.semHashDoCpf()).thenReturn(List.of(sem(id, cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario()));
        when(userRepository.existsByCpfHash(any())).thenReturn(true);

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(r.duplicados()).containsExactly(id);
        verify(userRepository, never()).save(any());
    }

    @Test
    void cpfCifradoIlegivel_oSeedGravaUmPlaceholder_ePassaAoProximo() {
        UUID ruim = UUID.randomUUID();
        UUID bom = UUID.randomUUID();
        User u = usuario();
        when(profileRepository.semHashDoCpf()).thenReturn(List.of(
                sem(ruim, "seed-cpf-cifrado-placeholder"), sem(bom, cifra.encrypt("529.982.247-25"))));
        when(userRepository.findById(bom)).thenReturn(Optional.of(u));
        when(userRepository.existsByCpfHash(any())).thenReturn(false);

        var r = backfill.preencher();

        assertThat(r.ilegiveis()).isEqualTo(1);
        assertThat(r.vinculados()).isEqualTo(1);   // um registro ruim não derruba o resto
    }

    @Test
    void corridaNaRestricaoUnica_contaComoDuplicado_enaoDerrubaOBackfill() {
        UUID id = UUID.randomUUID();
        when(profileRepository.semHashDoCpf()).thenReturn(List.of(sem(id, cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario()));
        when(userRepository.existsByCpfHash(any())).thenReturn(false);
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_users_cpf_hash"));

        var r = backfill.preencher();

        assertThat(r.duplicados()).containsExactly(id);
        assertThat(r.vinculados()).isZero();
    }

    @Test
    void contaQueJaGanhouOHashNoMeioDoCaminho_naoEhRefeita() {
        UUID id = UUID.randomUUID();
        User u = usuario();
        u.setCpfHash("hash-ja-gravado");
        when(profileRepository.semHashDoCpf()).thenReturn(List.of(sem(id, cifra.encrypt("111.444.777-35"))));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        var r = backfill.preencher();

        assertThat(r.vinculados()).isZero();
        assertThat(u.getCpfHash()).isEqualTo("hash-ja-gravado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void falhaDoBackfill_nuncaImpedeASubidaDaAplicacao() {
        // um ApplicationRunner que lança derruba a subida: o backfill é de conveniência, tenta de novo na próxima subida
        when(profileRepository.semHashDoCpf()).thenThrow(new RuntimeException("banco indisponível"));

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
