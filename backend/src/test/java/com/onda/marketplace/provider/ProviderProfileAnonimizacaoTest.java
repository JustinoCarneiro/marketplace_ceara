package com.onda.marketplace.provider;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Exclusão de conta (US36): o perfil do prestador perde tudo que é pessoal e sai da busca. */
class ProviderProfileAnonimizacaoTest {

    @Test
    void anonimizar_apagaDadosPessoais_eTiraOPrestadorDaOperacao() {
        ProviderProfile perfil = ProviderProfiles.comStatus(ProviderStatus.VERIFICADO);
        perfil.setBio("Eletricista há 12 anos em Fortaleza.");
        perfil.setChavePixCifrada("chave-pix-cifrada");
        perfil.setLocalizacao(new GeometryFactory().createPoint(new Coordinate(-38.52, -3.73)));
        perfil.setNotaMedia(new BigDecimal("4.80"));

        perfil.anonimizar();

        assertThat(perfil.getBio()).isNull();
        assertThat(perfil.getChavePixCifrada()).isNull();
        assertThat(perfil.getLocalizacao()).isNull();
        assertThat(perfil.getCpfCifrado()).isNull();
        // fora da busca (só VERIFICADO aparece) e sem poder propor (ProviderVerificationGuard)
        assertThat(perfil.getStatusVerificacao()).isEqualTo(ProviderStatus.SUSPENSO);
        // a reputação é do histórico, não é dado pessoal
        assertThat(perfil.getNotaMedia()).isEqualByComparingTo("4.80");
        assertThat(perfil.getCategoria()).isEqualTo("Elétrica");
    }
}
