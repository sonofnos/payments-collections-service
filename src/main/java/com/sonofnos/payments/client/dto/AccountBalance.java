package com.sonofnos.payments.client.dto;

import java.math.BigDecimal;

public record AccountBalance(String accountId, BigDecimal amount, String currency) {
}
