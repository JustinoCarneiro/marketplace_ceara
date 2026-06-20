package com.onda.marketplace.proposal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping("/api/v1/service-requests/{id}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalDto create(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProposalRequest req,
            Authentication auth) {
        UUID prestadorId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return proposalService.create(id, req, prestadorId);
    }

    @GetMapping("/api/v1/service-requests/{id}/proposals")
    public List<ProposalDto> list(@PathVariable UUID id) {
        return proposalService.listForRequest(id);
    }

    @PutMapping("/api/v1/proposals/{id}/accept")
    public ProposalDto accept(@PathVariable UUID id, Authentication auth) {
        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return proposalService.accept(id, clienteId);
    }

    @PutMapping("/api/v1/proposals/{id}/reject")
    public ProposalDto reject(@PathVariable UUID id, Authentication auth) {
        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return proposalService.reject(id, clienteId);
    }
}
