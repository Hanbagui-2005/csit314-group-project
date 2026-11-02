package com.csit314.platform.service;

import com.csit314.platform.model.*;
import com.csit314.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Taiga #95, #99 and #103. Team-confirmed approval state: ACTIVE; rejection state: REJECTED. */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceUnitTests {
    @Mock UserAccountRepository users;
    @Mock InteriorDesignerRepository designers;
    UserManagementService service;
    @BeforeEach void setUp() { service = new UserManagementService(users, designers); }
    private InteriorDesigner pending() {
        return new InteriorDesigner("Contact", "studio@example.test", "hash", new Company("Studio", "Text", "123"));
    }

    @Test void emptyUserStoreProducesEmptySummaryList() {
        when(users.findAll(any(Sort.class))).thenReturn(List.of());
        assertThat(service.listAllUsers()).isEmpty();
        verify(users).findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    @Test void summariesExposeDisplayNameRoleAndStatusWithoutPassword() {
        when(users.findAll(any(Sort.class))).thenReturn(List.of(
                new Customer("Client", "client@example.test", "secret-hash"), pending()));
        var list = service.listAllUsers();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).name()).isEqualTo("Client");
        assertThat(list.get(0).role()).isEqualTo("Customer");
        assertThat(list.get(1).name()).isEqualTo("Studio");
        assertThat(list.get(1).status()).isEqualTo("Pending");
        assertThat(list.toString()).doesNotContain("secret-hash");
    }
    @Test void pendingDesignerIsApprovedAndTimestamped() {
        InteriorDesigner designer = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        assertThat(service.approveDesigner(1L)).isSameAs(designer);
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(designer.getReviewedAt()).isNotNull();
        assertThat(designer.getRejectionReason()).isNull();
    }
    // CUS-09
    @Test void approvingWithNoLicenseNumberLeavesDesignerUnverified() {
        InteriorDesigner designer = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        service.approveDesigner(1L);
        assertThat(designer.isVerified()).isFalse();
    }
    @Test void approvingWithALicenseNumberMarksDesignerVerified() {
        InteriorDesigner designer = pending();
        designer.getCompany().setLicenseNumber("LIC-12345");
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        service.approveDesigner(1L);
        assertThat(designer.isVerified()).isTrue();
    }
    @Test void missingDesignerCannotBeApprovedOrRejected() {
        when(designers.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approveDesigner(42L)).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.rejectDesigner(42L, "Reason")).hasMessageContaining("not found");
    }
    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "REJECTED", "SUSPENDED"})
    void reviewedDesignerCannotBeReviewedAgain(AccountStatus state) {
        InteriorDesigner designer = pending(); designer.setStatus(state);
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        assertThatThrownBy(() -> service.approveDesigner(1L)).hasMessageContaining("already been reviewed");
        assertThatThrownBy(() -> service.rejectDesigner(1L, "Reason")).hasMessageContaining("already been reviewed");
        assertThat(designer.getStatus()).isEqualTo(state);
    }
    @Test void rejectionStoresTrimmedReasonAndTimestamp() {
        InteriorDesigner designer = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        service.rejectDesigner(1L, "  Missing documents  ");
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.REJECTED);
        assertThat(designer.getRejectionReason()).isEqualTo("Missing documents");
        assertThat(designer.getReviewedAt()).isNotNull();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void missingRejectionReasonDoesNotReadOrChangeDesigner(String reason) {
        assertThatThrownBy(() -> service.rejectDesigner(1L, reason)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(designers);
    }
    @Test void rejectionReasonLengthBoundaryIsEnforced() {
        assertThatThrownBy(() -> service.rejectDesigner(1L, "x".repeat(1001)))
                .hasMessageContaining("1000");
        verifyNoInteractions(designers);
        InteriorDesigner designer = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        service.rejectDesigner(1L, "x".repeat(1000));
        assertThat(designer.getRejectionReason()).hasSize(1000);
    }

    // ADM-06
    @Test void anActiveAccountCanBeSuspended() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        customer.setStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        service.suspend(1L);
        assertThat(customer.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test void onlyAnActiveAccountCanBeSuspended() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        customer.setStatus(AccountStatus.SUSPENDED);
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> service.suspend(1L)).isInstanceOf(ServiceException.class);
    }

    @Test void aSuspendedAccountCanBeReactivated() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        customer.setStatus(AccountStatus.SUSPENDED);
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        service.reactivate(1L);
        assertThat(customer.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test void anAdminAccountCannotBeSuspended() {
        Admin admin = new Admin("Platform Admin", "admin@platform.local", "hash");
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.suspend(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Admin accounts can't be suspended");
        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test void onlyASuspendedAccountCanBeReactivated() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        customer.setStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> service.reactivate(1L)).isInstanceOf(ServiceException.class);
    }

    // ADM-08
    @Test void bulkApproveSkipsFailuresAndReportsHowManySucceeded() {
        InteriorDesigner first = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(first));
        when(designers.findById(2L)).thenReturn(Optional.empty());
        int succeeded = service.bulkApproveDesigners(List.of(1L, 2L));
        assertThat(succeeded).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test void bulkSuspendSkipsFailuresAndReportsHowManySucceeded() {
        Customer active = new Customer("Client", "client@example.test", "hash");
        active.setStatus(AccountStatus.ACTIVE);
        Customer alreadySuspended = new Customer("Other", "other@example.test", "hash");
        alreadySuspended.setStatus(AccountStatus.SUSPENDED);
        when(users.findById(1L)).thenReturn(Optional.of(active));
        when(users.findById(2L)).thenReturn(Optional.of(alreadySuspended));
        int succeeded = service.bulkSuspend(List.of(1L, 2L));
        assertThat(succeeded).isEqualTo(1);
    }

    // ADM-09
    @Test void aLockedAccountCanBeUnlocked() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        customer.recordFailedLogin(); customer.recordFailedLogin(); customer.recordFailedLogin();
        customer.recordFailedLogin(); customer.recordFailedLogin();
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        assertThat(customer.isLocked()).isTrue();
        service.unlock(1L);
        assertThat(customer.isLocked()).isFalse();
        assertThat(customer.getFailedLoginAttempts()).isZero();
    }

    @Test void unlockingAnAlreadyUnlockedAccountFails() {
        Customer customer = new Customer("Client", "client@example.test", "hash");
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> service.unlock(1L)).isInstanceOf(ServiceException.class);
    }

    // ADM-05
    @Test void verificationCanBeToggledOnAndOff() {
        InteriorDesigner designer = pending();
        when(designers.findById(1L)).thenReturn(Optional.of(designer));
        assertThat(designer.isVerified()).isFalse();
        service.toggleVerified(1L);
        assertThat(designer.isVerified()).isTrue();
        service.toggleVerified(1L);
        assertThat(designer.isVerified()).isFalse();
    }

    // ADM-12
    @Test void pendingDesignerCountDelegatesToTheRepository() {
        when(users.countByRoleAndStatus(Role.INTERIOR_DESIGNER, AccountStatus.PENDING)).thenReturn(4L);
        assertThat(service.pendingDesignerCount()).isEqualTo(4L);
    }

    // ADM-14
    @Test void threeOrMoreRecentSignUpsFromTheSameIpRaiseAnAlert() {
        Customer a = withRegistrationIp("A", "a@example.test", "1.2.3.4");
        Customer b = withRegistrationIp("B", "b@example.test", "1.2.3.4");
        Customer c = withRegistrationIp("C", "c@example.test", "1.2.3.4");
        when(users.findAll()).thenReturn(List.of(a, b, c));

        var alerts = service.registrationAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).ipAddress()).isEqualTo("1.2.3.4");
        assertThat(alerts.get(0).count()).isEqualTo(3);
        assertThat(alerts.get(0).emails()).containsExactly("a@example.test", "b@example.test", "c@example.test");
    }

    @Test void fewerThanThreeSignUpsFromTheSameIpDoNotRaiseAnAlert() {
        Customer a = withRegistrationIp("A", "a@example.test", "1.2.3.4");
        Customer b = withRegistrationIp("B", "b@example.test", "1.2.3.4");
        when(users.findAll()).thenReturn(List.of(a, b));
        assertThat(service.registrationAlerts()).isEmpty();
    }

    @Test void accountsWithNoRegistrationIpAreIgnored() {
        Customer a = withRegistrationIp("A", "a@example.test", null);
        when(users.findAll()).thenReturn(List.of(a));
        assertThat(service.registrationAlerts()).isEmpty();
    }

    private Customer withRegistrationIp(String name, String email, String ip) {
        Customer customer = new Customer(name, email, "hash");
        customer.setRegistrationIp(ip);
        ReflectionTestUtils.setField(customer, "createdAt", LocalDateTime.now());
        return customer;
    }
}
