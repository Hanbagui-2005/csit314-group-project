package com.csit314.platform.service;

import com.csit314.platform.model.PasswordResetToken;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.PasswordResetTokenRepository;
import com.csit314.platform.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// CUS-03: forgot password -> one-time reset link -> choose a new password
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expiryMinutes;
    private final String baseUrl;

    public PasswordResetService(UserAccountRepository userAccountRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.password-reset.expiry-minutes:30}") long expiryMinutes,
                                @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.expiryMinutes = expiryMinutes;
        this.baseUrl = baseUrl;
    }

    // returns the raw token if the email belongs to an account; callers should show the same message either way
    @Transactional
    public Optional<String> requestReset(String email) {
        Optional<UserAccount> user = userAccountRepository.findByEmail(InputRules.normaliseEmail(email));
        if (user.isEmpty()) {
            return Optional.empty();
        }

        // only the newest link should work
        tokenRepository.findByUserAndUsedAtIsNull(user.get()).forEach(PasswordResetToken::markUsed);

        String rawToken = newToken();
        tokenRepository.save(new PasswordResetToken(user.get(), hash(rawToken),
                LocalDateTime.now().plusMinutes(expiryMinutes)));

        // no mail server yet, so the link goes to the log for now
        log.info("Password reset link for {}: {}/reset-password?token={}", user.get().getEmail(), baseUrl, rawToken);
        return Optional.of(rawToken);
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String rawToken) {
        return findUsableToken(rawToken).isPresent();
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        PasswordResetToken token = findUsableToken(rawToken)
                .orElseThrow(() -> new ServiceException("This reset link is invalid or has expired. Please request a new one."));
        InputRules.checkNewPassword(newPassword, confirmPassword);

        token.getUser().setPasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed();
    }

    private Optional<PasswordResetToken> findUsableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(hash(rawToken)).filter(PasswordResetToken::isUsable);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
