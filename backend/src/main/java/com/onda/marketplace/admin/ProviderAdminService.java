package com.onda.marketplace.admin;

import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta de prestadores para o painel admin (US25). Lista perfis (com o
 * usuário em fetch join, evitando lazy fora da transação), opcionalmente
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
                ? providerProfileRepository.findAllWithUser()
                : providerProfileRepository.findByStatusWithUser(statusVerificacao);
        return lista.stream().map(ProviderAdminDto::from).toList();
    }
}
