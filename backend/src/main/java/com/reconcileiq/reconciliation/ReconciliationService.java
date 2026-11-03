
package com.reconcileiq.reconciliation;

import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.demo.DemoScenarioSeeder;
import com.reconcileiq.orders.MerchantOrder;
import com.reconcileiq.orders.MerchantOrderRepository;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.paypal.PayPalTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final MerchantOrderRepository merchantOrderRepository;
    private final PayPalTransactionRepository paypalTransactionRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;
    private final ReconciliationResultTransactionRepository
            resultTransactionRepository;
    private final ReconcileIqProperties properties;

    public ReconciliationService(
            MerchantOrderRepository merchantOrderRepository,
            PayPalTransactionRepository paypalTransactionRepository,
            ReconciliationRunRepository runRepository,
            ReconciliationResultRepository resultRepository,
            ReconciliationResultTransactionRepository
                    resultTransactionRepository,
            ReconcileIqProperties properties
    ) {
        this.merchantOrderRepository = merchantOrderRepository;
        this.paypalTransactionRepository = paypalTransactionRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.resultTransactionRepository = resultTransactionRepository;
        this.properties = properties;
    }

    @Transactional
    public ReconciliationRun runReconciliation() {

        List<MerchantOrder> merchantOrders =
                merchantOrderRepository.findAll();

        List<PayPalTransaction> paypalTransactions =
                paypalTransactionRepository.findAll()
                        .stream()
                        .filter(tx ->
                                "COMPLETED".equalsIgnoreCase(tx.getStatus()))
                        .toList();

        ReconciliationRun run = new ReconciliationRun();
        run.setTotalOrders(merchantOrders.size());
        run = runRepository.save(run);

        // Build global reference indexes.
        Map<String, MerchantOrder> ordersByPaypalId =
                merchantOrders.stream()
                        .filter(o -> o.getPaypalOrderId() != null)
                        .collect(Collectors.toMap(
                                MerchantOrder::getPaypalOrderId,
                                Function.identity(),
                                (a, b) -> a
                        ));

        Map<String, MerchantOrder> ordersByMerchantId =
                merchantOrders.stream()
                        .filter(o -> o.getMerchantOrderId() != null)
                        .collect(Collectors.toMap(
                                MerchantOrder::getMerchantOrderId,
                                Function.identity(),
                                (a, b) -> a
                        ));

        // Resolve transaction ownership before processing orders.
        Map<Long, MerchantOrder> transactionOwners =
                new HashMap<>();

        Set<Long> unexpectedTransactionIds =
                new HashSet<>();

        for (PayPalTransaction tx : paypalTransactions) {

            MerchantOrder paypalOwner =
                    ordersByPaypalId.get(tx.getPaypalOrderId());

            MerchantOrder merchantOwner =
                    ordersByMerchantId.get(tx.getMerchantOrderId());

            if (paypalOwner != null && merchantOwner != null) {

                if (Objects.equals(
                        paypalOwner.getMerchantOrderId(),
                        merchantOwner.getMerchantOrderId())) {

                    transactionOwners.put(tx.getId(), paypalOwner);

                } else {
                    // The two references point to different orders.
                    // Do not assign this transaction to either order.
                    unexpectedTransactionIds.add(tx.getId());
                }

            } else if (paypalOwner != null) {

                transactionOwners.put(tx.getId(), paypalOwner);

            } else if (merchantOwner != null) {

                transactionOwners.put(tx.getId(), merchantOwner);

            } else {

                unexpectedTransactionIds.add(tx.getId());
            }
        }

        Set<Long> processedTransactionIds = new HashSet<>();

        int matched = 0;
        int discrepancies = 0;
        int unexpectedPayments = 0;

        for (MerchantOrder order : merchantOrders) {

            List<PayPalTransaction> candidates =
                    paypalTransactions.stream()
                            .filter(tx -> {
                                MerchantOrder owner =
                                        transactionOwners.get(tx.getId());

                                return owner != null
                                        && Objects.equals(
                                                owner.getMerchantOrderId(),
                                                order.getMerchantOrderId()
                                        );
                            })
                            .toList();

            // Transactions whose identifiers conflict with each other
            // are added as mismatch evidence for the PayPal-side owner.
            List<PayPalTransaction> conflictingCandidates =
                    paypalTransactions.stream()
                            .filter(tx ->
                                    unexpectedTransactionIds.contains(tx.getId())
                                            && Objects.equals(
                                                    ordersByPaypalId
                                                            .get(tx.getPaypalOrderId())
                                                            == null
                                                            ? null
                                                            : ordersByPaypalId
                                                            .get(tx.getPaypalOrderId())
                                                            .getMerchantOrderId(),
                                                    order.getMerchantOrderId()
                                            ))
                            .toList();

            List<PayPalTransaction> allCandidates =
                    new ArrayList<>(candidates);

            allCandidates.addAll(conflictingCandidates);

            ReconciliationResult result =
                    reconcileOrder(order, allCandidates, run.getId());
            tagSource(result);

            result = resultRepository.save(result);

            saveTransactionAssociations(result, allCandidates);

            for (PayPalTransaction tx : allCandidates) {
                processedTransactionIds.add(tx.getId());
            }

            if (result.getStatus() == ReconciliationStatus.MATCHED) {
                matched++;
            } else {
                discrepancies++;
            }
        }

        // Persist transactions that have no corresponding merchant order,
        // and transactions whose references conflict but could not be
        // attached to a merchant order.
        for (PayPalTransaction tx : paypalTransactions) {

            if (processedTransactionIds.contains(tx.getId())) {
                continue;
            }

            ReconciliationResult result = new ReconciliationResult();

            result.setReconciliationRunId(run.getId());
            result.setPaypalOrderId(tx.getPaypalOrderId());
            result.setCaptureId(tx.getCaptureId());
            result.setActualAmount(tx.getAmount());
            result.setActualCurrency(tx.getCurrency());
            result.setStatus(ReconciliationStatus.UNEXPECTED_PAYMENT);
            result.setMatchedTransactionCount(1);

            result.setExplanation(
                    unexpectedTransactionIds.contains(tx.getId())
                            ? "PayPal transaction has conflicting or unrecognized "
                              + "merchant references."
                            : "PayPal transaction has no corresponding merchant order."
            );
            tagSource(result);

            result = resultRepository.save(result);

            saveTransactionAssociations(result, List.of(tx));

            unexpectedPayments++;
        }

        run.setMatchedCount(matched);
        run.setDiscrepancyCount(discrepancies);
        run.setUnmatchedPaymentsCount(unexpectedPayments);

        return runRepository.save(run);
    }

    private void tagSource(ReconciliationResult result) {
        boolean demo = DemoScenarioSeeder.isDemoRecord(
                result.getMerchantOrderId(),
                result.getPaypalOrderId(),
                result.getCaptureId());
        result.setSource(demo ? "DEMO" : "SANDBOX");
    }

    private void saveTransactionAssociations(
            ReconciliationResult result,
            List<PayPalTransaction> transactions
    ) {
        for (PayPalTransaction tx : transactions) {
            resultTransactionRepository.save(
                    new ReconciliationResultTransaction(
                            result.getId(),
                            tx
                    )
            );
        }
    }

    private ReconciliationResult reconcileOrder(
            MerchantOrder order,
            List<PayPalTransaction> candidates,
            Long runId
    ) {

        ReconciliationResult result = new ReconciliationResult();

        result.setReconciliationRunId(runId);
        result.setMerchantOrderId(order.getMerchantOrderId());
        result.setPaypalOrderId(order.getPaypalOrderId());
        result.setExpectedAmount(order.getExpectedAmount());
        result.setExpectedCurrency(order.getCurrency());
        result.setMatchedTransactionCount(candidates.size());

        if (candidates.isEmpty()) {

            result.setStatus(ReconciliationStatus.MISSING_PAYMENT);
            result.setExplanation(
                    "No PayPal transaction was found for this merchant order."
            );

            return result;
        }

        // Reference mismatch takes precedence over duplicate classification.
        boolean hasReferenceMismatch = candidates.stream()
                .anyMatch(tx ->
                        !Objects.equals(
                                order.getPaypalOrderId(),
                                tx.getPaypalOrderId()
                        )
                        ||
                        !Objects.equals(
                                order.getMerchantOrderId(),
                                tx.getMerchantOrderId()
                        )
                );

        if (hasReferenceMismatch) {

            PayPalTransaction transaction = candidates.get(0);

            result.setStatus(ReconciliationStatus.REFERENCE_MISMATCH);
            result.setCaptureId(transaction.getCaptureId());
            result.setActualAmount(transaction.getAmount());
            result.setActualCurrency(transaction.getCurrency());
            result.setExplanation(
                    "One or more PayPal transactions contain references "
                    + "that do not match this merchant order."
            );

            return result;
        }

        // Duplicate transactions.
        if (candidates.size() > 1) {

            boolean sameCurrency = candidates.stream()
                    .map(PayPalTransaction::getCurrency)
                    .distinct()
                    .count() == 1;

            if (sameCurrency) {

                BigDecimal totalActualAmount = candidates.stream()
                        .map(PayPalTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                result.setActualAmount(totalActualAmount);
                result.setActualCurrency(
                        candidates.get(0).getCurrency()
                );

            } else {

                // Do not add amounts across different currencies.
                result.setActualAmount(null);
                result.setActualCurrency(null);
            }

            result.setStatus(ReconciliationStatus.DUPLICATE_PAYMENT);
            result.setExplanation(
                    "Multiple PayPal transactions were associated with "
                    + "this merchant order."
            );

            return result;
        }

        // Exactly one transaction.
        PayPalTransaction transaction = candidates.get(0);

        result.setCaptureId(transaction.getCaptureId());
        result.setActualAmount(transaction.getAmount());
        result.setActualCurrency(transaction.getCurrency());

        if (order.getExpectedAmount() == null
                || transaction.getAmount() == null
                || order.getExpectedAmount()
                        .compareTo(transaction.getAmount()) != 0) {

            result.setStatus(ReconciliationStatus.AMOUNT_MISMATCH);
            result.setExplanation(
                    "Expected amount differs from the captured PayPal amount."
            );

            return result;
        }

        if (order.getCurrency() == null
                || transaction.getCurrency() == null
                || !order.getCurrency()
                        .equalsIgnoreCase(transaction.getCurrency())) {

            result.setStatus(ReconciliationStatus.CURRENCY_MISMATCH);
            result.setExplanation(
                    "Expected currency differs from the captured PayPal currency."
            );

            return result;
        }

        result.setStatus(ReconciliationStatus.MATCHED);
        result.setExplanation(
                "Merchant order and PayPal capture match successfully."
        );

        return result;
    }
}
