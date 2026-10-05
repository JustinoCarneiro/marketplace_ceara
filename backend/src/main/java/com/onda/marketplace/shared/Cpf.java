package com.onda.marketplace.shared;

/**
 * CPF: forma só de dígitos e validação dos dígitos verificadores (módulo 11, regra da Receita). A unicidade da pessoa
 * (antifraude) é por hash do CPF; sem validar os dígitos ela se burla com um número inventado, e sem normalizar a máscara
 * a mesma pessoa teria dois hashes.
 */
public final class Cpf {

    private Cpf() {}

    /** Só os dígitos, na ordem: "111.444.777-35" e " 111 444 777 35 " viram "11144477735". */
    public static String soDigitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("[^0-9]", "");
    }

    /** 11 dígitos, não todos iguais e com os dois dígitos verificadores certos. Letras ou tamanho errado: inválido. */
    public static boolean valido(String cpf) {
        if (cpf == null || !cpf.matches("[0-9.\\-\\s]+")) {
            return false;
        }
        String d = soDigitos(cpf);
        return d.length() == 11
                && d.chars().distinct().count() > 1
                && digito(d, 9) == d.charAt(9) - '0'
                && digito(d, 10) == d.charAt(10) - '0';
    }

    private static int digito(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
