package com.onda.marketplace.discovery;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/nearby")
    public List<NearbyProviderDto> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double raio,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "20")  int    limite) {

        return discoveryService.findNearby(new NearbyQuery(lat, lng, raio, categoria, limite));
    }
}
