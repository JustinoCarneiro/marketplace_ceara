package com.onda.marketplace.payment;

public interface GatewayService {
    // Retorna o ID da transação no gateway (usado para reconciliação via webhook)
    String cobrar(Transaction transaction);
}
