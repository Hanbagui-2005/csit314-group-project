package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Booking;
import com.csit314.platform.model.BookingStatus;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.BookingRequestForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #36 [CUS-11] - requesting a consultation booking. */
@ExtendWith(MockitoExtension.class)
class BookingServiceUnitTests {

    @Mock BookingRepository bookingRepository;
    @Mock InteriorDesignerRepository designerRepository;
    @Mock ProjectRepository projectRepository;
    BookingService service;

    Customer customer;
    InteriorDesigner designer;
    InteriorDesigner otherDesigner;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, designerRepository, projectRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
        designer.setStatus(AccountStatus.ACTIVE);

        otherDesigner = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(otherDesigner, "id", 3L);
        otherDesigner.setStatus(AccountStatus.ACTIVE);
    }

    private BookingRequestForm form(LocalDate date) {
        return new BookingRequestForm(date, "Notes", null);
    }

    @Test
    void requestsABookingForAFutureDate() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service.requestBooking(customer, 2L, form(LocalDate.now().plusDays(7)));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getCustomer()).isEqualTo(customer);
        assertThat(result.getDesigner()).isEqualTo(designer);
    }

    @Test
    void rejectsAMissingDate() {
        assertThatThrownBy(() -> service.requestBooking(customer, 2L, form(null)))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(designerRepository);
    }

    @Test
    void rejectsAPastDate() {
        assertThatThrownBy(() -> service.requestBooking(customer, 2L, form(LocalDate.now().minusDays(1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("past");
    }

    @Test
    void rejectsBookingASuspendedDesigner() {
        designer.setStatus(AccountStatus.SUSPENDED);
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));

        assertThatThrownBy(() -> service.requestBooking(customer, 2L, form(LocalDate.now().plusDays(1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("taking bookings");
    }

    @Test
    void rejectsAProjectThatBelongsToADifferentDesigner() {
        when(designerRepository.findById(3L)).thenReturn(Optional.of(otherDesigner));
        Category category = new Category("Kitchen");
        Project project = new Project("Kitchen remodel", "x", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.requestBooking(customer, 3L,
                new BookingRequestForm(LocalDate.now().plusDays(1), null, 100L)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void ownerDesignerCanAcceptTheirOwnBooking() {
        Booking booking = new Booking(customer, designer, null, LocalDate.now().plusDays(1), null);
        ReflectionTestUtils.setField(booking, "id", 500L);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        service.accept(designer, 500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    @Test
    void anotherDesignerCannotRespondToSomeoneElsesBooking() {
        Booking booking = new Booking(customer, designer, null, LocalDate.now().plusDays(1), null);
        ReflectionTestUtils.setField(booking, "id", 500L);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.decline(otherDesigner, 500L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own bookings");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ADM-11
    @Test
    void customerCanCancelTheirOwnPendingBooking() {
        Booking booking = new Booking(customer, designer, null, LocalDate.now().plusDays(1), null);
        ReflectionTestUtils.setField(booking, "id", 500L);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        service.cancel(customer, 500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void anotherCustomerCannotCancelSomeoneElsesBooking() {
        Booking booking = new Booking(customer, designer, null, LocalDate.now().plusDays(1), null);
        ReflectionTestUtils.setField(booking, "id", 500L);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        Customer otherCustomer = new Customer("Other", "other-customer@example.test", "encoded");
        ReflectionTestUtils.setField(otherCustomer, "id", 99L);

        assertThatThrownBy(() -> service.cancel(otherCustomer, 500L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own bookings");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void anAlreadyCancelledBookingCannotBeCancelledAgain() {
        Booking booking = new Booking(customer, designer, null, LocalDate.now().plusDays(1), null);
        ReflectionTestUtils.setField(booking, "id", 500L);
        booking.cancel();
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(customer, 500L))
                .isInstanceOf(ServiceException.class);
    }
}
