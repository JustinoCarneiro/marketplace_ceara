package com.onda.marketplace.shared;

import java.util.Set;

/**
 * Bairros de Fortaleza aceitos no pedido (US23 parte 2) — a MESMA lista fixa que o app mostra em chips
 * ({@code NewRequestScreen.BAIRROS}; sem reverse geocoding implementado, não há como validar contra um
 * endereço real). Fora da lista, não é um bairro: impede que endereço, nome ou outro texto livre entre
 * nesse campo (achado da revisão cruzada, 2026-10-05 — o relatório admin trata o valor como dado
 * agregável e seguro, inclusive depois que a conta que abriu o pedido é excluída).
 *
 * <p>Catálogo fixo no código, não uma tabela: {@code service_categories} (US28, painel admin) existe
 * para outra finalidade e nasce vazia em todo ambiente — nenhum pedido passaria pela validação até o
 * admin cadastrar algo à mão. Mudar a lista de bairros do app exige mudar aqui também.
 */
public final class Bairro {

    public static final Set<String> VALIDOS = Set.of(
            "Aldeota", "Meireles", "Cocó", "Papicu", "Varjota",
            "Centro", "Messejana", "Parangaba", "Montese", "Barra do Ceará");

    private Bairro() {}

    /** Campo opcional: nulo ou vazio passa. Preenchido, precisa ser um dos nomes da lista (sensível a maiúsculas). */
    public static boolean valido(String bairro) {
        return bairro == null || bairro.isBlank() || VALIDOS.contains(bairro);
    }
}
