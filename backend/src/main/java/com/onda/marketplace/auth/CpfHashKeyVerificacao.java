package com.onda.marketplace.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Verificador da chave do hash do CPF, por versão (ver {@link CpfHashService#verificadorChaveAtual}).
 * Gravado na 1ª subida de cada versão; {@link CpfHashKeyCheck} confere nas seguintes.
 */
@Entity
@Table(name = "cpf_hash_key_verificacoes")
public class CpfHashKeyVerificacao {

    @Id
    private Integer versao;

    @Column(nullable = false, length = 64)
    private String verificador;

    protected CpfHashKeyVerificacao() {}

    public CpfHashKeyVerificacao(Integer versao, String verificador) {
        this.versao = versao;
        this.verificador = verificador;
    }

    public Integer getVersao() {
        return versao;
    }

    public String getVerificador() {
        return verificador;
    }
}
