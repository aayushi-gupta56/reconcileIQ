package com.reconcileiq.paypal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayPalTransactionRepository
        extends JpaRepository<PayPalTransaction, Long> {

    Optional<PayPalTransaction> findByCaptureId(String captureId);

    List<PayPalTransaction> findAllByPaypalOrderId(String paypalOrderId);

    List<PayPalTransaction> findAllByMerchantOrderId(String merchantOrderId);
}