package com.sonofnos.payments.client.dto;

import java.math.BigDecimal;

public record TransferResult(String accountId, BigDecimal newBalance, String currency, String reference) {
}
