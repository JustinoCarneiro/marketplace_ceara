package com.onda.marketplace.notification;

import com.onda.marketplace.shared.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
public class PushDeviceController {
    private final PushStore store;
    private final PushGateway gateway;
    PushDeviceController(PushStore store,PushGateway gateway) { this.store=store;this.gateway=gateway; }
    public record DeviceRequest(@NotBlank @Size(min=20,max=4096) @Pattern(regexp="[A-Za-z0-9_:.-]+") String token,
            @NotBlank @Pattern(regexp="[a-f0-9]{64}") String revocationKey) {
        @Override public String toString() { return "DeviceRequest[redacted]"; }
    }
    public record TokenRequest(@NotBlank @Size(min=20,max=4096) @Pattern(regexp="[A-Za-z0-9_:.-]+") String token) {
        @Override public String toString() { return "TokenRequest[redacted]"; }
    }
    public record Reservation(String revocationKey) {
        @Override public String toString() { return "Reservation[redacted]"; }
    }
    @PostMapping("/registrations")
    public Reservation reserve(Authentication auth,@Valid @RequestBody TokenRequest req) {
        if(!gateway.enabled()) throw new BusinessException("PUSH_DISABLED","Notificações indisponíveis neste momento.");
        return new Reservation(store.reserve(UUID.fromString(auth.getName()),req.token()));
    }
    @GetMapping("/status") public Map<String,Boolean> status() { return Map.of("enabled",gateway.enabled()); }
    @PutMapping("/devices") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(Authentication auth,@Valid @RequestBody DeviceRequest req) {
        if(!gateway.enabled()) throw new BusinessException("PUSH_DISABLED","Notificações indisponíveis neste momento.");
        store.register(UUID.fromString(auth.getName()),req.token(),req.revocationKey());
    }
    @DeleteMapping("/devices") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(Authentication auth,@Valid @RequestBody DeviceRequest req) {
        store.unregister(UUID.fromString(auth.getName()),req.token());
    }
    /** Capacidade de revogação de um dispositivo; não permite ler dados ou cadastrar tokens. */
    @PostMapping("/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@Valid @RequestBody DeviceRequest req) { store.revoke(req.token(),req.revocationKey()); }
}
