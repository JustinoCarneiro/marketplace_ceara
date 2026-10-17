package com.onda.marketplace.notification;

import java.util.UUID;

public interface PushGateway {
    enum Result { SENT, UNREGISTERED, RETRY, FAILED }
    boolean enabled();
    Result send(String token, String kind, UUID reference, UUID deliveryId);
}
