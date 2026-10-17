package com.onda.marketplace.notification;

import org.springframework.stereotype.Component;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import java.util.UUID;

@Component
@DependsOnDatabaseInitialization
class FcmPushSender implements PushSender {
    private final PushStore store;
    private final PushGateway gateway;
    FcmPushSender(PushStore store,PushGateway gateway) { this.store=store;this.gateway=gateway; }
    public boolean ativo() { return gateway.enabled() && store.hasAdminDevice(); }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void enviar(String kind, UUID ref) {
        if(!gateway.enabled()) return;
        var devices=store.adminDevices();
        if(devices.isEmpty()) throw new NotificationDeliveryException("Nenhum dispositivo administrativo disponível.",null);
        boolean failed=false;
        for(UUID id:devices) {
            try {
                String token=store.adminToken(id);
                if(token==null || gateway.send(token,kind,ref,UUID.randomUUID())!=PushGateway.Result.SENT) failed=true;
            } catch(RuntimeException ex) { failed=true; }
        }
        if(failed) throw new NotificationDeliveryException("Alerta push não entregue; verificar canal administrativo.",null);
    }
}
