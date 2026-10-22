package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.payment.api.dto.BankReceipt;

public interface SpaDepositFacade {
    String receiveDeposit(String transferCode, BankReceipt receipt);
}
