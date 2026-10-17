package com.onda.marketplace.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
class PushEventListener {
    private final PushStore store;
    private final PushGateway gateway;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    PushEventListener(PushStore store,PushGateway gateway) { this.store=store;this.gateway=gateway; }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void enqueue(PushRequested event) { if(gateway.enabled()) { em.flush(); store.enqueue(event); } }
}
