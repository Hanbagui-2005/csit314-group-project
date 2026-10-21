package com.csit314.platform.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class MockDataGeneratorTests {
    @TempDir Path directory;
    @Test void bothDatasetsHaveOneHundredUniqueMatchingInputs() {
        var customers = MockDataGenerator.customers(100);
        var designers = MockDataGenerator.designers(100);
        assertThat(customers).hasSize(100).allSatisfy(f -> assertThat(f.password()).isEqualTo(f.confirmPassword()));
        assertThat(designers).hasSize(100).allSatisfy(f -> {
            assertThat(f.password()).isEqualTo(f.confirmPassword());
            assertThat(f.companyName()).isNotBlank();
        });
        assertThat(Stream.concat(customers.stream().map(f -> f.email()), designers.stream().map(f -> f.email())).toList())
                .hasSize(200).doesNotHaveDuplicates();
    }
    @Test void exportedJsonIsReproducibleAndHasCorrectRecordCounts() throws Exception {
        MockDataGenerator.export(directory, 100);
        byte[] original = Files.readAllBytes(directory.resolve("customers.json"));
        var json = new ObjectMapper();
        assertThat(json.readTree(directory.resolve("customers.json").toFile()).size()).isEqualTo(100);
        assertThat(json.readTree(directory.resolve("designer-registrations.json").toFile()).size()).isEqualTo(100);
        MockDataGenerator.export(directory, 100);
        assertThat(Files.readAllBytes(directory.resolve("customers.json"))).isEqualTo(original);
    }
    @Test void countCanBeIncreasedAndInvalidCountsAreRejected() {
        assertThat(MockDataGenerator.customers(120)).hasSize(120);
        assertThat(MockDataGenerator.designers(120)).hasSize(120);
        assertThatThrownBy(() -> MockDataGenerator.customers(99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDataGenerator.designers(10001)).isInstanceOf(IllegalArgumentException.class);
    }
}
