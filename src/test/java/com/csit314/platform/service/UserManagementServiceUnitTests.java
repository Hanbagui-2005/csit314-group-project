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
}
