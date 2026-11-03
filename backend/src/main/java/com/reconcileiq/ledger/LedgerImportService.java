package com.reconcileiq.ledger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LedgerImportService {

    private static final Set<String> REQUIRED_HEADERS =
            Set.of(
                    "merchant_order_id",
                    "amount",
                    "currency",
                    "status"
            );

    private final LedgerEntryRepository repository;

    public LedgerImportService(
            LedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LedgerDtos.ImportResponse importCsv(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Upload a non-empty CSV file."
            );
        }

        if (file.getOriginalFilename() == null ||
                !file.getOriginalFilename()
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".csv")) {

            throw new IllegalArgumentException(
                    "Only CSV files are supported."
            );
        }

        List<LedgerEntry> validEntries =
                new ArrayList<>();

        List<String> errors =
                new ArrayList<>();

        int rowNumber = 1; // Header is row 1.

        try (
            Reader reader = new InputStreamReader(
                    file.getInputStream(),
                    StandardCharsets.UTF_8
            );

            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build()
                    .parse(reader)
        ) {

            if (!parser.getHeaderMap()
                    .keySet()
                    .containsAll(REQUIRED_HEADERS)) {

                throw new IllegalArgumentException(
                        "Required CSV headers: " +
                        "merchant_order_id, amount, currency, status. " +
                        "Optional: payment_reference, " +
                        "transaction_date, description."
                );
            }

            for (CSVRecord record : parser) {

                rowNumber++;

                try {
                    LedgerEntry entry = parseRecord(record);

                    validEntries.add(entry);

                } catch (RuntimeException e) {

                    String reason = e.getMessage() == null
                            ? "Invalid row"
                            : e.getMessage();

                    errors.add(
                            "Row " + rowNumber + ": " + reason
                    );
                }
            }

        } catch (IOException e) {

            throw new IllegalArgumentException(
                    "Could not read the uploaded CSV file."
            );
        }

        if (!validEntries.isEmpty()) {
            repository.saveAll(validEntries);
        }

        return new LedgerDtos.ImportResponse(
                validEntries.size(),
                errors.size(),
                List.copyOf(errors),
                "Valid rows imported. Review any rejected rows."
        );
    }

    private LedgerEntry parseRecord(CSVRecord record) {

        String orderId = required(
                record,
                "merchant_order_id"
        );

        BigDecimal amount = new BigDecimal(
                required(record, "amount")
        );

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amount cannot be negative."
            );
        }

        String currency = required(
                record,
                "currency"
        ).toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter code."
            );
        }

        String status = required(
                record,
                "status"
        ).toUpperCase(Locale.ROOT);

        String paymentReference = optional(
                record,
                "payment_reference"
        );

        String description = optional(
                record,
                "description"
        );

        LocalDateTime transactionDate = parseDate(
                optional(record, "transaction_date")
        );

        return new LedgerEntry(
                orderId,
                paymentReference,
                amount,
                currency,
                status,
                transactionDate,
                description
        );
    }

    private static String required(
            CSVRecord record,
            String key) {

        String value = optional(record, key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    key + " is required."
            );
        }

        return value.trim();
    }

    private static String optional(
            CSVRecord record,
            String key) {

        if (!record.isMapped(key)) {
            return null;
        }

        String value = record.get(key);

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static LocalDateTime parseDate(String value) {

        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);

        } catch (DateTimeParseException e) {

            throw new IllegalArgumentException(
                    "transaction_date must use ISO format, " +
                    "for example 2026-10-25T14:30:00."
            );
        }
    }
}