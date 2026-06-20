package com.onda.marketplace.servicerequest;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {

    @Mock ServiceRequestRepository    requestRepository;
    @Mock ServiceMediaRepository      mediaRepository;
    @Mock UserRepository              userRepository;
    @Mock AiSuggestionService         aiService;
    @Mock StorageService              storageService;

    ServiceRequestService service;

    private final User cliente = User.builder()
            .nome("João")
            .email("joao@test.com")
            .senhaHash("$2a$hash")
            .role(UserRole.ROLE_CLIENT)
            .build();

    @BeforeEach
    void setUp() {
        service = new ServiceRequestService(requestRepository, mediaRepository, userRepository, aiService, storageService);
    }

    @Test
    void create_comSugestaoIA_retornaDescricaoSugerida() {
        var suggestion = new AiSuggestion("Instalação de chuveiro elétrico", BigDecimal.valueOf(150), BigDecimal.valueOf(300));
        when(userRepository.findById(any())).thenReturn(Optional.of(cliente));
        when(aiService.suggest(any(), any())).thenReturn(Optional.of(suggestion));
        when(requestRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new CreateServiceRequestRequest("ELETRICISTA", "Chuveiro sem funcionar", -3.7319, -38.5267);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-key-1");

        assertThat(dto.aiDescricaoSugerida()).isEqualTo("Instalação de chuveiro elétrico");
        assertThat(dto.status()).isEqualTo("PENDENTE");
    }

    @Test
    void create_iaFalha_retornaSemSugestao() {
        when(userRepository.findById(any())).thenReturn(Optional.of(cliente));
        when(aiService.suggest(any(), any())).thenReturn(Optional.empty());  // IA indisponível
        when(requestRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new CreateServiceRequestRequest("ENCANADOR", null, -3.7319, -38.5267);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-key-2");

        // Pedido criado normalmente — sem sugestão IA (fallback manual)
        assertThat(dto.status()).isEqualTo("PENDENTE");
        assertThat(dto.aiDescricaoSugerida()).isNull();
        verify(requestRepository).save(any());
    }

    @Test
    void create_chaveIdempotenteDuplicada_retornaExistente() {
        var existing = new ServiceRequest();
        existing.setCategoria("ELETRICISTA");
        existing.setStatus(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

        var req = new CreateServiceRequestRequest("ELETRICISTA", null, -3.7, -38.5);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-dup");

        assertThat(dto.status()).isEqualTo("PENDENTE");
        verify(requestRepository, never()).save(any());  // não salva de novo
    }
}
