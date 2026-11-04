package com.csit314.platform.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class GeneratedPasswordUnitTests {

    @Test
    void generatesATwentyCharacterPassword() {
        assertThat(GeneratedPassword.generate()).hasSize(20);
    }

    @Test
    void generatesADifferentPasswordEachCall() {
        assertThat(GeneratedPassword.generate()).isNotEqualTo(GeneratedPassword.generate());
    }
}
