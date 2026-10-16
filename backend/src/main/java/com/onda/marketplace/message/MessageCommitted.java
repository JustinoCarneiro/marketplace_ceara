package com.onda.marketplace.message;

import java.util.UUID;

/** Somente ids: o conteúdo é consultado após o commit, nunca distribuído antes da persistência. */
record MessageCommitted(UUID requestId, UUID messageId) {}
