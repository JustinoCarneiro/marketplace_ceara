package com.onda.marketplace.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PushWorker {
    private final PushStore store;
    private final PushGateway gateway;
    private final TransactionTemplate tx;
    private final TransactionTemplate outside;
    PushWorker(PushStore store, PushGateway gateway, PlatformTransactionManager manager) {
        this.store=store; this.gateway=gateway;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outside=new TransactionTemplate(manager);
        outside.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    @Scheduled(fixedDelayString="${notification.push.delay-ms:5000}",initialDelayString="${notification.push.delay-ms:5000}")
    public void process() {
        if (!gateway.enabled()) return;
        for(int i=0;i<25;i++) if(!processOne()) break;
    }
    public boolean processOne() {
        if(!gateway.enabled()) return false;
        var d=tx.execute(s->store.claim());
        if(d==null) return false;
        PushGateway.Result result;
        long retryAfter=0;
        try {
            String token=tx.execute(s->store.token(d));
            result=token==null || d.attempts()>5 ? PushGateway.Result.FAILED :
                    outside.execute(s->gateway.send(token,d.kind(),d.reference(),d.id()));
            if(result==null) result=PushGateway.Result.RETRY;
        } catch(FcmPushGateway.RetryLater ex) { result=PushGateway.Result.RETRY; retryAfter=ex.seconds; }
        catch(RuntimeException ex) { result=PushGateway.Result.RETRY; }
        var outcome=result;
        long wait=retryAfter;
        tx.executeWithoutResult(s->store.finish(d,outcome,wait));
        return true;
    }
    @Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void cleanup() { tx.executeWithoutResult(s->store.cleanup()); }
}
