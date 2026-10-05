package com.onda.marketplace.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Produz hash determinístico (HMAC-SHA256) do CPF para indexação de unicidade.
 * LGPD: somente o hash entra no banco — o CPF em claro nunca é persistido para clientes.
 * O mesmo CPF sempre gera o mesmo hash com a mesma chave, permitindo constraint UNIQUE.
 *
 * <p><b>Chave própria, com versão.</b> A chave do HMAC é distinta da que cifra o CPF do prestador ({@code CPF_HASH_KEY} ×
 * {@code CPF_ENCRYPTION_KEY}): uma chave por finalidade, e trocar uma não invalida a outra. Cada hash guarda a versão da
 * chave ({@code users.cpf_hash_versao}); numa rotação, a chave que acabou de sair fica como {@code previous} (versão
 * {@code atual - 1}) só para reconhecer os hashes antigos até que todos sejam regravados — o prestador na subida (decifra
 * o CPF) e o cliente na próxima confirmação de identidade (só ele sabe o CPF em claro).
 */
public class CpfHashService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int    TAMANHO_MINIMO_DA_CHAVE = 32;

    private final byte[] chaveAtual;
    private final int    versaoAtual;
    private final byte[] chaveAnterior;   // null = não há conta na versão anterior (ou nunca houve)

    public CpfHashService(String chaveAtual, int versaoAtual, String chaveAnterior) {
        if (chaveAtual == null || chaveAtual.length() < TAMANHO_MINIMO_DA_CHAVE) {
            throw new IllegalArgumentException(
                    "A chave do hash do CPF (CPF_HASH_KEY) precisa de pelo menos " + TAMANHO_MINIMO_DA_CHAVE + " caracteres.");
        }
        if (versaoAtual < 1) {
            throw new IllegalArgumentException("A versão da chave do hash do CPF começa em 1.");
        }
        if (chaveAnterior != null && chaveAnterior.equals(chaveAtual)) {
            throw new IllegalArgumentException("CPF_HASH_KEY_PREVIOUS não pode ser igual à chave atual: não haveria o que rotacionar.");
        }
        this.chaveAtual    = chaveAtual.getBytes(StandardCharsets.UTF_8);
        this.versaoAtual   = versaoAtual;
        this.chaveAnterior = chaveAnterior == null ? null : chaveAnterior.getBytes(StandardCharsets.UTF_8);
    }

    /** Só a chave atual (sem rotação em curso). */
    public CpfHashService(String chaveAtual, int versaoAtual) {
        this(chaveAtual, versaoAtual, null);
    }

    public int versaoAtual() {
        return versaoAtual;
    }

    /** Há chave para reconhecer hashes da versão anterior? Sem ela, essas contas não se comparam mais. */
    public boolean temChaveAnterior() {
        return chaveAnterior != null;
    }

    /** Hash do CPF com a chave ATUAL — é o que se grava. */
    public String hash(String cpf) {
        return calcular(chaveAtual, cpf);
    }

    /**
     * Todos os hashes sob os quais este CPF pode já estar gravado: o da chave atual e, numa rotação em curso, o da anterior.
     * É o que se consulta para saber se o CPF já tem dono.
     */
    public List<String> hashesPossiveis(String cpf) {
        List<String> hashes = new ArrayList<>(2);
        hashes.add(hash(cpf));
        if (chaveAnterior != null) {
            hashes.add(calcular(chaveAnterior, cpf));
        }
        return hashes;
    }

    /** Este hash gravado (com esta versão) é deste CPF? Versão sem chave configurada não confere. */
    public boolean confere(String cpf, String hashGravado, int versaoGravada) {
        if (hashGravado == null) {
            return false;
        }
        if (versaoGravada == versaoAtual) {
            return hashGravado.equals(hash(cpf));
        }
        if (versaoGravada == versaoAtual - 1 && chaveAnterior != null) {
            return hashGravado.equals(calcular(chaveAnterior, cpf));
        }
        return false;
    }

    private static String calcular(byte[] chave, String cpf) {
        String normalized = cpf.replaceAll("[^0-9]", "");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(chave, ALGORITHM));
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash do CPF", e);
        }
    }
}
