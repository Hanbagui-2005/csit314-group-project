package com.csit314.platform.service;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.Complaint;
import com.csit314.platform.model.ComplaintStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.dto.ComplaintForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// ADM-10
@ExtendWith(MockitoExtension.class)
class ComplaintServiceUnitTests {

    @Mock ComplaintRepository complaintRepository;
    @Mock InteriorDesignerRepository designerRepository;
    @Mock BookingRepository bookingRepository;
    ComplaintService service;

    Customer customer;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new ComplaintService(complaintRepository, designerRepository, bookingRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
    }

    @Test
    void submitsAComplaintAgainstAnExistingDesignerTheyveBooked() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.existsByCustomerIdAndDesignerId(1L, 2L)).thenReturn(true);
        when(complaintRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Complaint result = service.submit(customer, 2L, new ComplaintForm("They never showed up."));

        assertThat(result.getCustomer()).isEqualTo(customer);
        assertThat(result.getDesigner()).isEqualTo(designer);
        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.OPEN);
    }

    @Test
    void rejectsABlankDescription() {
        assertThatThrownBy(() -> service.submit(customer, 2L, new ComplaintForm("  ")))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(designerRepository, complaintRepository);
    }

    @Test
    void rejectsAComplaintAgainstAMissingDesigner() {
        when(designerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(customer, 99L, new ComplaintForm("Issue")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no longer exists");
    }

    // ADM-10 - no booking, no complaint
    @Test
    void rejectsAComplaintAboutADesignerNeverBooked() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.existsByCustomerIdAndDesignerId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(customer, 2L, new ComplaintForm("Issue")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("booked");
        verifyNoInteractions(complaintRepository);
    }

    // ADM-10 - at most one open complaint per customer per designer at a time
    @Test
    void rejectsASecondOpenComplaintAboutTheSameDesigner() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.existsByCustomerIdAndDesignerId(1L, 2L)).thenReturn(true);
        when(complaintRepository.existsByCustomerIdAndDesignerIdAndStatus(1L, 2L, ComplaintStatus.OPEN))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(customer, 2L, new ComplaintForm("Another issue")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("already have an open complaint");
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void resolvingAnOpenComplaintMarksItResolved() {
        Complaint complaint = new Complaint(customer, designer, "Issue");
        ReflectionTestUtils.setField(complaint, "id", 10L);
        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));

        service.resolve(10L);

        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
    }

    @Test
    void dismissingAnOpenComplaintMarksItDismissed() {
        Complaint complaint = new Complaint(customer, designer, "Issue");
        ReflectionTestUtils.setField(complaint, "id", 10L);
        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));

        service.dismiss(10L);

        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.DISMISSED);
    }

    @Test
    void resolvingAMissingComplaintFails() {
        when(complaintRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(404L)).isInstanceOf(ServiceException.class);
    }

    // an admin shouldn't be able to flip a decided complaint back and forth
    @Test
    void anAlreadyDismissedComplaintCannotBeResolved() {
        Complaint complaint = new Complaint(customer, designer, "Issue");
        ReflectionTestUtils.setField(complaint, "id", 10L);
        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
        service.dismiss(10L);

        assertThatThrownBy(() -> service.resolve(10L)).isInstanceOf(ServiceException.class);
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.DISMISSED);
    }

    @Test
    void anAlreadyResolvedComplaintCannotBeDismissed() {
        Complaint complaint = new Complaint(customer, designer, "Issue");
        ReflectionTestUtils.setField(complaint, "id", 10L);
        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
        service.resolve(10L);

        assertThatThrownBy(() -> service.dismiss(10L)).isInstanceOf(ServiceException.class);
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
    }
}
