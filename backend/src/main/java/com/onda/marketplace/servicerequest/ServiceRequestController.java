package com.onda.marketplace.servicerequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    // Só cliente origina/edita pedido (US04). Sem isto, uma conta ROLE_PROVIDER criava e
    // publicava pedidos como se fosse cliente — a posse já era checada, mas o papel não.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public ServiceRequestDto create(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateServiceRequestRequest req,
            Authentication auth) {

        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return service.create(clienteId, req, idempotencyKey);
    }

    @PostMapping("/{id}/media")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public MediaUploadResponse addMedia(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tipo") String tipo,
            Authentication auth) {
        return service.addMedia(id, file, tipo, userId(auth));
    }

    /** "Meus pedidos" do cliente logado (mobile). */
    @GetMapping("/my")
    public List<MyRequestDto> meusPedidos(Authentication auth) {
        return service.listarMeusPedidos(userId(auth));
    }

    /** Atendimento ativo do prestador logado — resolve a tab "Em Andamento" (mobile). */
    @GetMapping("/active")
    public ActiveJobDto atendimentoAtivo(Authentication auth) {
        return new ActiveJobDto(service.buscarPedidoAtivoDoPrestador(userId(auth)));
    }

    /** Detalhe do pedido — só para quem participa dele (cliente dono ou prestador aceito). */
    @GetMapping("/{id}")
    public ServiceRequestDetailDto detalhe(@PathVariable UUID id, Authentication auth) {
        return service.detalhar(id, userId(auth));
    }

    /** Sugestão da IA persistida no pedido; campos nulos = fallback manual na tela. */
    @GetMapping("/{id}/ai-suggestion")
    public AiSuggestionDto sugestaoIa(@PathVariable UUID id, Authentication auth) {
        return service.buscarSugestaoIa(id, userId(auth));
    }

    /** Confirma a descrição final do pedido depois da revisão com a IA. */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('CLIENT')")
    public ServiceRequestDto publicar(
            @PathVariable UUID id,
            @Valid @RequestBody PublishRequest req,
            Authentication auth) {
        return service.publicar(id, userId(auth), req);
    }

    private static UUID userId(Authentication auth) {
        return auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
    }
}
