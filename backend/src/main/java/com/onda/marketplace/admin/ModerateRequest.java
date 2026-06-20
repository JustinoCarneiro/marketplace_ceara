package com.onda.marketplace.admin;

import jakarta.validation.constraints.NotNull;

public record ModerateRequest(@NotNull ModerationAction action) {}
