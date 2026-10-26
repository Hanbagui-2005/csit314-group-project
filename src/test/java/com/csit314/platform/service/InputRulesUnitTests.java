package com.csit314.platform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Validation used by both registration datasets and password updates. */
class InputRulesUnitTests {
    @Test void validEmailIsTrimmedAndLowerCased() {
        assertThat(InputRules.requireEmail(" Person@Example.Test ")).isEqualTo("person@example.test");
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"plain-text", "a@", "@example.test", "a b@example.test", "a@example", "a@@example.test"})
    void malformedEmailsAreRejected(String email) {
        assertThatThrownBy(() -> InputRules.requireEmail(email)).isInstanceOf(ServiceException.class);
    }
    @Test void requiredTextAcceptsBoundaryAndRejectsOverflow() {
        assertThat(InputRules.requireText(" x ", "Name", 1)).isEqualTo("x");
        assertThatThrownBy(() -> InputRules.requireText("xx", "Name", 1)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> InputRules.requireText(" ", "Name", 1)).isInstanceOf(ServiceException.class);
    }
    @Test void optionalTextHandlesMissingValueAndMaximumLength() {
        assertThat(InputRules.optionalText(null, 3, "Text")).isNull();
        assertThat(InputRules.optionalText(" ", 3, "Text")).isNull();
        assertThat(InputRules.optionalText(" abc ", 3, "Text")).isEqualTo("abc");
        assertThatThrownBy(() -> InputRules.optionalText("abcd", 3, "Text")).isInstanceOf(ServiceException.class);
    }
    @Test void passwordBoundaryAndConfirmationAreEnforced() {
        assertThatCode(() -> InputRules.checkNewPassword("abcd1234", "abcd1234")).doesNotThrowAnyException();
        assertThatThrownBy(() -> InputRules.checkNewPassword("abcd123", "abcd123")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> InputRules.checkNewPassword(null, null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> InputRules.checkNewPassword("abcd1234", "different")).isInstanceOf(ServiceException.class);
    }
    // CUS-16
    @Test void passwordNeedsBothALetterAndADigit() {
        assertThatThrownBy(() -> InputRules.checkNewPassword("12345678", "12345678")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> InputRules.checkNewPassword("abcdefgh", "abcdefgh")).isInstanceOf(ServiceException.class);
        assertThatCode(() -> InputRules.checkNewPassword("abcdefg1", "abcdefg1")).doesNotThrowAnyException();
    }
}
