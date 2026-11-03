package com.reconcileiq.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantOrderRepository
        extends JpaRepository<MerchantOrder, Long> {

    Optional<MerchantOrder> findByMerchantOrderId(String merchantOrderId);

    List<MerchantOrder> findAllByOrderByIdDesc();

    Optional<MerchantOrder> findByPaypalOrderId(String paypalOrderId);
}