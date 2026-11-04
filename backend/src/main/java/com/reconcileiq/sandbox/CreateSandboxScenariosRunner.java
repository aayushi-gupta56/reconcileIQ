package com.reconcileiq.sandbox;

import com.reconcileiq.sandbox.SandboxScenarioService.ScenarioRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Component
@Profile("sandbox-scenarios")
public class CreateSandboxScenariosRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CreateSandboxScenariosRunner.class);

    private final SandboxScenarioService scenarios;
    private final org.springframework.context.ApplicationContext context;

    public CreateSandboxScenariosRunner(
            SandboxScenarioService scenarios,
            org.springframework.context.ApplicationContext context) {
        this.scenarios = scenarios;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        log.info("Creating PayPal sandbox discrepancy scenarios (idempotent).");
        List<ScenarioRow> rows = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        try {
            rows.add(scenarios.createMatched(this::print, scanner));
            rows.add(scenarios.createAmountMismatch(this::print, scanner));
            rows.add(scenarios.createCurrencyMismatch(this::print, scanner));
            rows.add(scenarios.createMissingPayment(this::print));
            rows.add(scenarios.createUnexpectedPayment(this::print, scanner));
            rows.add(scenarios.createReferenceMismatch(this::print, scanner));
            rows.addAll(scenarios.createDuplicate(this::print, scanner));
        } finally {
            scanner.close();
        }
        printTable(rows);
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }

    private void print(String line) {
        log.info(line);
        System.out.println(line);
    }

    private void printTable(List<ScenarioRow> rows) {
        String header = String.format(
                "%-22s %-16s %-20s %-20s %-10s",
                "SCENARIO",
                "MERCHANT REF",
                "PAYPAL ORDER ID",
                "CAPTURE ID",
                "STATUS"
        );
        print("");
        print(header);
        print("-".repeat(header.length()));
        for (ScenarioRow row : rows) {
            print(String.format(
                    "%-22s %-16s %-20s %-20s %-10s",
                    nullToDash(row.scenario()),
                    nullToDash(row.merchantOrderId()),
                    nullToDash(row.paypalOrderId()),
                    nullToDash(row.captureId()),
                    nullToDash(row.status())
            ));
        }
        print("");
        print("Run twice is safe: existing merchant references are skipped.");
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
