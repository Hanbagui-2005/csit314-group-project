package com.csit314.platform.service;

import com.csit314.platform.model.BookingStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Rating;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.RatingRepository;
import com.csit314.platform.web.dto.RatingForm;
import com.csit314.platform.web.dto.RatingSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// CUS-13
@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final BookingRepository bookingRepository;
    private final InteriorDesignerRepository designerRepository;

    public RatingService(RatingRepository ratingRepository, BookingRepository bookingRepository,
                         InteriorDesignerRepository designerRepository) {
        this.ratingRepository = ratingRepository;
        this.bookingRepository = bookingRepository;
        this.designerRepository = designerRepository;
    }

    @Transactional
    public void rate(Customer customer, Long designerId, RatingForm form) {
        if (form.score() == null || form.score() < 1 || form.score() > 5) {
            throw new ServiceException("Rating must be between 1 and 5.");
        }
        if (!bookingRepository.existsByCustomerIdAndDesignerIdAndStatus(customer.getId(), designerId, BookingStatus.ACCEPTED)) {
            throw new ServiceException("You can only rate a designer after they've accepted a booking with you.");
        }
        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("That designer no longer exists."));
        String comment = form.comment() == null || form.comment().isBlank() ? null : form.comment().trim();

        ratingRepository.findByCustomerIdAndDesignerId(customer.getId(), designerId)
                .ifPresentOrElse(
                        existing -> existing.update(form.score(), comment),
                        () -> ratingRepository.save(new Rating(customer, designer, form.score(), comment)));
    }

    public RatingSummary summaryFor(Long designerId) {
        List<Rating> reviews = ratingRepository.findByDesignerIdOrderByCreatedAtDesc(designerId);
        if (reviews.isEmpty()) {
            return new RatingSummary(null, 0, reviews);
        }
        double average = reviews.stream().mapToInt(Rating::getScore).average().orElse(0);
        return new RatingSummary(average, reviews.size(), reviews);
    }

    public boolean canRate(Customer customer, Long designerId) {
        return bookingRepository.existsByCustomerIdAndDesignerIdAndStatus(customer.getId(), designerId, BookingStatus.ACCEPTED);
    }
}
