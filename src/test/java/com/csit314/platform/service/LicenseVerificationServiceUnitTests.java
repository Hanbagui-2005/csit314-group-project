package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Admin;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.LicenseVerification;
import com.csit314.platform.repository.LicenseVerificationRepository;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseVerificationServiceUnitTests {

    @Mock EntityManager entityManager;
    @Mock UserAccountRepository userAccountRepository;
    @Mock LicenseVerificationRepository verificationRepository;

    LicenseVerificationService service;
    Admin admin;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new LicenseVerificationService(
                entityManager,
                userAccountRepository,
                verificationRepository);

        admin = new Admin(
                "Admin", "admin@example.test", "encoded");

        Company company = new Company(
                "Studio", "Description", "12345");
        company.setLicenseNumber("LIC-12345");

        designer = new InteriorDesigner(
                "Designer", "designer@example.test", "encoded", company);
    }

    private void stubAdmin() {
        when(userAccountRepository.findById(1L))
                .thenReturn(Optional.of(admin));
    }

    private void stubDesigner() {
        when(entityManager.find(
                InteriorDesigner.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(designer);
    }

    private void stubAccounts() {
        stubAdmin();
        stubDesigner();
    }

    private LicenseVerification savedRecord() {
        ArgumentCaptor<LicenseVerification> captor =
                ArgumentCaptor.forClass(LicenseVerification.class);
        verify(verificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void pendingDesignerCanBeVerifiedWithRecordedEvidence() {
        stubAccounts();

        service.verify(
                7L, 1L, "LIC-12345",
                "  Checked official company registration.  ");

        assertThat(designer.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(designer.isVerified()).isTrue();

        LicenseVerification record = savedRecord();
        assertThat(record.getDesigner()).isSameAs(designer);
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.VERIFIED);
        assertThat(record.getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(record.getActorEmail()).isEqualTo("admin@example.test");
        assertThat(record.getNote())
                .isEqualTo("Checked official company registration.");

        verify(entityManager).refresh(designer);
    }

    @Test
    void revocationRecordsTheAdminAndReason() {
        stubAccounts();
        designer.setVerified(true);

        service.revoke(
                7L, 1L, "LIC-12345",
                "  Registration details no longer match.  ");

        assertThat(designer.isVerified()).isFalse();

        LicenseVerification record = savedRecord();
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.REVOKED);
        assertThat(record.getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(record.getActorEmail()).isEqualTo("admin@example.test");
        assertThat(record.getNote())
                .isEqualTo("Registration details no longer match.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cannotVerifyWithoutALicenseNumber(String licenseNumber) {
        stubAccounts();
        designer.getCompany().setLicenseNumber(licenseNumber);

        assertThatThrownBy(() -> service.verify(
                7L, 1L, licenseNumber, "Checked registration."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("license number");

        assertThat(designer.isVerified()).isFalse();
        verifyNoInteractions(verificationRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void verificationAndRevocationRequireANote(String note) {
        stubAdmin();

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", note))
                .isInstanceOf(ServiceException.class);

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-12345", note))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void rejectsOverlongNotesBeforeChangingAnything() {
        stubAdmin();
        String note = "X".repeat(2001);

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", note))
                .isInstanceOf(ServiceException.class);

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-12345", note))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void cannotVerifyUsingAnOutdatedLicenseNumber() {
        stubAccounts();

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-OLD", "Checked registration."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("changed");

        assertThat(designer.isVerified()).isFalse();
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void cannotRevokeUsingAnOutdatedLicenseNumber() {
        stubAccounts();
        designer.setVerified(true);

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-OLD", "Incorrect details."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("changed");

        assertThat(designer.isVerified()).isTrue();
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void cannotVerifyAnAlreadyVerifiedDesigner() {
        stubAccounts();
        designer.setVerified(true);

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", "Checked registration."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("already verified");

        verifyNoInteractions(verificationRepository);
    }

    @Test
    void cannotRevokeAnUnverifiedDesigner() {
        stubAccounts();

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-12345", "Incorrect details."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not currently verified");

        verifyNoInteractions(verificationRepository);
    }

    @Test
    void canRevokeLegacyVerificationWithoutALicenseNumber() {
        stubAccounts();
        designer.getCompany().setLicenseNumber(null);
        designer.setVerified(true);

        service.revoke(7L, 1L, null, "Missing supporting license.");

        assertThat(designer.isVerified()).isFalse();
        LicenseVerification record = savedRecord();
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.REVOKED);
        assertThat(record.getLicenseNumber()).isNull();
    }

    @Test
    void nonAdminCannotVerifyOrRevoke() {
        Customer customer = new Customer(
                "Customer", "customer@example.test", "encoded");
        when(userAccountRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", "Checked registration."))
                .isInstanceOf(ServiceException.class);

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-12345", "Incorrect details."))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void suspendedAdminCannotVerifyOrRevoke() {
        stubAdmin();
        admin.setStatus(AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", "Checked registration."))
                .isInstanceOf(ServiceException.class);

        assertThatThrownBy(() -> service.revoke(
                7L, 1L, "LIC-12345", "Incorrect details."))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void cannotVerifyAMissingDesigner() {
        stubAdmin();

        assertThatThrownBy(() -> service.verify(
                7L, 1L, "LIC-12345", "Checked registration."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not found");

        verifyNoInteractions(verificationRepository);
    }

    @Test
    void laterLicenseChangesDoNotAlterTheRecordedNumber() {
        stubAccounts();

        service.verify(
                7L, 1L, "LIC-12345", "Checked registration.");
        LicenseVerification record = savedRecord();

        designer.getCompany().setLicenseNumber("LIC-67890");

        assertThat(record.getLicenseNumber()).isEqualTo("LIC-12345");
    }
}
