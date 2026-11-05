package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;

// ADM-09/13 - real parallel logins against a real database, each in its own transaction.
// The BCrypt check sits between reading the account and recording the failure, so wrong
// guesses sent together all overlap. Every one of them has to count, or an attacker could
// get past the lockout just by sending guesses in parallel
@SpringBootTest
class LoginLockoutConcurrencyTests {

    private static final int PARALLEL_GUESSES = 8;

    @Autowired AuthService authService;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private Customer newCustomer() {
        Customer customer = new Customer("Parallel", "parallel-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Correct!2026"));
        customer.setStatus(AccountStatus.ACTIVE);
        return users.save(customer);
    }

    private Customer newCustomerLockedAt(LocalDateTime lockedAt) {
        Customer customer = newCustomer();
        ReflectionTestUtils.setField(customer, "failedLoginAttempts", UserAccount.MAX_FAILED_ATTEMPTS);
        ReflectionTestUtils.setField(customer, "lockedAt", lockedAt);
        return users.save(customer);
    }

    private void guessWrong(String email) {
        assertThatThrownBy(() -> authService.loginCustomer(email, "wrong-guess", "127.0.0.1"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void anotherGuessDuringAnActiveLockDoesNotExtendIt() {
        LocalDateTime lockedAt = LocalDateTime.now().minusMinutes(5).withNano(0);
        Customer customer = newCustomerLockedAt(lockedAt);

        guessWrong(customer.getEmail());

        UserAccount after = users.findById(customer.getId()).orElseThrow();
        assertThat(after.getLockedAt()).isEqualTo(lockedAt);
        assertThat(after.getFailedLoginAttempts()).isEqualTo(UserAccount.MAX_FAILED_ATTEMPTS);
    }

    @Test
    void aGuessAfterALockHasLapsedStartsCountingFresh() {
        Customer customer = newCustomerLockedAt(
                LocalDateTime.now().minusMinutes(UserAccount.LOCK_DURATION_MINUTES + 1));

        guessWrong(customer.getEmail());

        UserAccount after = users.findById(customer.getId()).orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(after.getLockedAt()).isNull();
    }

    @Test
    void wrongPasswordsSentInParallelAllCountTowardsTheLockout() throws Exception {
        Customer customer = newCustomer();
        Long id = customer.getId();
        String email = customer.getEmail();

        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_GUESSES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> guesses = new ArrayList<>();
        for (int i = 0; i < PARALLEL_GUESSES; i++) {
            guesses.add(pool.submit(() -> {
                start.await();
                guessWrong(email);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> guess : guesses) {
            guess.get();
        }
        pool.shutdown();

        UserAccount after = users.findById(id).orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isGreaterThanOrEqualTo(UserAccount.MAX_FAILED_ATTEMPTS);
        assertThat(after.isLocked()).isTrue();
    }
}
