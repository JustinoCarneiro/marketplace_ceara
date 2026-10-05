package com.onda.marketplace.discovery;

import com.onda.marketplace.provider.ProviderPublicDto;
import com.onda.marketplace.provider.ProviderPublicService;
import com.onda.marketplace.servicerequest.AvailableRequestDto;
import com.onda.marketplace.servicerequest.ServiceRequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/providers")
public class DiscoveryController {

    private final DiscoveryService       discoveryService;
    private final ServiceRequestService  serviceRequestService;
    private final ProviderPublicService  providerPublicService;

    public DiscoveryController(DiscoveryService discoveryService,
                               ServiceRequestService serviceRequestService,
                               ProviderPublicService providerPublicService) {
        this.discoveryService       = discoveryService;
        this.serviceRequestService  = serviceRequestService;
        this.providerPublicService  = providerPublicService;
    }

    @GetMapping("/nearby")
    public List<NearbyProviderDto> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double raio,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "20")  int    limite,
            Authentication auth) {

        return discoveryService.findNearby(new NearbyQuery(lat, lng, raio, categoria, limite),
                userId(auth));
    }

    /** Fila de pedidos abertos pro prestador propor (AvailableRequestsScreen). */
    @GetMapping("/available-requests")
    @PreAuthorize("hasRole('PROVIDER')")
    public List<AvailableRequestDto> availableRequests(Authentication auth) {
        return serviceRequestService.listarDisponiveis(userId(auth));
    }

    /** Perfil público do prestador (ProviderProfileScreen). */
    @GetMapping("/{userId}")
    public ProviderPublicDto perfil(@PathVariable UUID userId) {
        return providerPublicService.buscarPorUserId(userId);
    }

    // Mesmo padrão dos demais controllers: sem sessão (só em slice de teste) não há quem excluir
    private static UUID userId(Authentication auth) {
        return auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
    }
}
