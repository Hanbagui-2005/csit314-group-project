package com.csit314.platform.service;

import com.csit314.platform.model.BookingStatus;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Rating;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.RatingRepository;
import com.csit314.platform.web.dto.RatingForm;
import com.csit314.platform.web.dto.RatingSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #38 [CUS-13] - gated on having an accepted booking, the closest thing this app
 * has to "actually worked with this designer". */
@ExtendWith(MockitoExtension.class)
class RatingServiceUnitTests {

    @Mock RatingRepository ratingRepository;
    @Mock BookingRepository bookingRepository;
    @Mock InteriorDesignerRepository designerRepository;
    RatingService service;

    Customer customer;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new RatingService(ratingRepository, bookingRepository, designerRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
    }

    @Test
    void ratingRequiresAnAcceptedBooking() {
        when(bookingRepository.existsByCustomerIdAndDesignerIdAndStatus(1L, 2L, BookingStatus.ACCEPTED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.rate(customer, 2L, new RatingForm(5, "Great!")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("accepted a booking");
        verifyNoInteractions(designerRepository);
    }

    @Test
    void rejectsAnOutOfRangeScore() {
        assertThatThrownBy(() -> service.rate(customer, 2L, new RatingForm(6, null)))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.rate(customer, 2L, new RatingForm(0, null)))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void savesANewRatingWhenEligible() {
        when(bookingRepository.existsByCustomerIdAndDesignerIdAndStatus(1L, 2L, BookingStatus.ACCEPTED))
                .thenReturn(true);
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(ratingRepository.findByCustomerIdAndDesignerId(1L, 2L)).thenReturn(Optional.empty());

        service.rate(customer, 2L, new RatingForm(5, "Great!"));

        verify(ratingRepository).save(any(Rating.class));
    }

    @Test
    void reRatingUpdatesTheExistingRatingInsteadOfCreatingASecondOne() {
        when(bookingRepository.existsByCustomerIdAndDesignerIdAndStatus(1L, 2L, BookingStatus.ACCEPTED))
                .thenReturn(true);
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        Rating existing = new Rating(customer, designer, 3, "Okay");
        when(ratingRepository.findByCustomerIdAndDesignerId(1L, 2L)).thenReturn(Optional.of(existing));

        service.rate(customer, 2L, new RatingForm(5, "Actually great"));

        assertThat(existing.getScore()).isEqualTo(5);
        assertThat(existing.getComment()).isEqualTo("Actually great");
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void summaryAveragesAllScoresForADesigner() {
        Rating a = new Rating(customer, designer, 4, null);
        Rating b = new Rating(customer, designer, 2, null);
        when(ratingRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(a, b));

        RatingSummary summary = service.summaryFor(2L);

        assertThat(summary.average()).isEqualTo(3.0);
        assertThat(summary.count()).isEqualTo(2);
    }

    @Test
    void summaryHasNullAverageWithNoRatings() {
        when(ratingRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        RatingSummary summary = service.summaryFor(2L);

        assertThat(summary.average()).isNull();
        assertThat(summary.count()).isZero();
    }
}
