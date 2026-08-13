package com.onda.marketplace.message;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests/{id}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public MessageDto enviar(@PathVariable UUID id,
                             @Valid @RequestBody SendMessageRequest req,
                             Authentication auth) {
        UUID remetenteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return messageService.enviar(id, remetenteId, req.conteudo());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public List<MessageDto> listar(@PathVariable UUID id, Authentication auth) {
        UUID userId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return messageService.listar(id, userId);
    }
}
