package com.csit314.platform.qa;

import com.csit314.platform.web.dto.CustomerRegistrationForm;
import com.csit314.platform.web.dto.DesignerRegistrationForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.IntStream;

/** Taiga #107/#115. Deterministic synthetic input; application services hash passwords on import. */
public final class MockDataGenerator {
    private MockDataGenerator() {}
    private static void validateCount(int count) {
        if (count < 100 || count > 10000) throw new IllegalArgumentException("Count must be between 100 and 10000.");
    }
    public static List<CustomerRegistrationForm> customers(int count) {
        validateCount(count);
        return IntStream.rangeClosed(1, count).mapToObj(i -> new CustomerRegistrationForm(
                "QA Customer %03d".formatted(i), "qa.customer%03d@example.test".formatted(i),
                "QaCustomer!%06dA".formatted(i), "QaCustomer!%06dA".formatted(i))).toList();
    }
    public static List<DesignerRegistrationForm> designers(int count) {
        validateCount(count);
        return IntStream.rangeClosed(1, count).mapToObj(i -> new DesignerRegistrationForm(
                "QA Designer %03d".formatted(i), "qa.designer%03d@example.test".formatted(i),
                "QaDesigner!%06dA".formatted(i), "QaDesigner!%06dA".formatted(i),
                "QA Design Company %03d".formatted(i), "Synthetic studio for registration testing.",
                "555%07d".formatted(i), "LIC-%06d".formatted(i))).toList();
    }
    public static void export(Path directory, int count) throws IOException {
        Files.createDirectories(directory);
        var json = new ObjectMapper().writerWithDefaultPrettyPrinter();
        json.writeValue(directory.resolve("customers.json").toFile(), customers(count));
        json.writeValue(directory.resolve("designer-registrations.json").toFile(), designers(count));
    }
    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        Path directory = Path.of(args.length > 1 ? args[1] : "qa-data");
        export(directory, count);
        System.out.printf("Generated %d Customer and %d Designer registration inputs in %s%n", count, count, directory);
        System.out.println("For application validation and persistence proof, run LargeMockDatasetTests.");
    }
}
