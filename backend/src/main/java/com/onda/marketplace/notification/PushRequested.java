package com.onda.marketplace.notification;

import java.util.UUID;

/** IDs somente. O listener persiste tarefas dentro da transação de origem. */
public record PushRequested(UUID requestId, UUID excludeUserId, String kind, UUID referenceId) {}
