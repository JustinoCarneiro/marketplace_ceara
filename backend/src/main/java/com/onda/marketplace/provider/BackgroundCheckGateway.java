package com.onda.marketplace.provider;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Porta para o futuro fornecedor. checkId deve ser a chave de idempotência;
 * a implementação deve impor timeout inferior ao lease de 120 segundos,
 * não registrar CPF/resposta bruta e traduzir somente resultados documentados.
 */
public interface BackgroundCheckGateway {
    enum Result { APROVADO, REPROVADO, INCONCLUSIVO }
    Result check(UUID checkId, Supplier<String> cpf);
}
