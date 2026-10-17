package com.onda.marketplace.admin;

import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta de prestadores para o painel admin (US25). Lista projeções em um join
 * explícito, sem carregar entidades de usuário, opcionalmente
 * filtrados por {@code statusVerificacao}.
 */
@Service
public class ProviderAdminService {

    private final ProviderProfileRepository providerProfileRepository;

    public ProviderAdminService(ProviderProfileRepository providerProfileRepository) {
        this.providerProfileRepository = providerProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<ProviderAdminDto> listar(ProviderStatus statusVerificacao) {
        var lista = (statusVerificacao == null)
                ? providerProfileRepository.findSummaries()
                : providerProfileRepository.findSummariesByStatus(statusVerificacao);
        return lista.stream().map(ProviderAdminDto::from).toList();
    }
}
