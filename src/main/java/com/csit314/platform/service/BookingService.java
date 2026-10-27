package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Booking;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.BookingRequestForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

// CUS-11
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final InteriorDesignerRepository designerRepository;
    private final ProjectRepository projectRepository;

    public BookingService(BookingRepository bookingRepository,
                          InteriorDesignerRepository designerRepository,
                          ProjectRepository projectRepository) {
        this.bookingRepository = bookingRepository;
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Booking requestBooking(Customer customer, Long designerId, BookingRequestForm form) {
        if (form.preferredDate() == null) {
            throw new ServiceException("Pick a preferred date.");
        }
        if (form.preferredDate().isBefore(LocalDate.now())) {
            throw new ServiceException("Preferred date can't be in the past.");
        }

        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("That designer no longer exists."));
        if (designer.getStatus() != AccountStatus.ACTIVE) {
            throw new ServiceException("This designer isn't taking bookings right now.");
        }

        Project project = null;
        if (form.projectId() != null) {
            project = projectRepository.findById(form.projectId())
                    .orElseThrow(() -> new ServiceException("That project no longer exists."));
            if (!project.getDesigner().getId().equals(designerId)) {
                throw new ServiceException("That project doesn't belong to this designer.");
            }
        }

        String notes = form.notes() == null || form.notes().isBlank() ? null : form.notes().trim();
        return bookingRepository.save(new Booking(customer, designer, project, form.preferredDate(), notes));
    }

    public List<Booking> bookingsFor(InteriorDesigner designer) {
        return bookingRepository.findByDesignerIdOrderByCreatedAtDesc(designer.getId());
    }

    public List<Booking> bookingsFor(Customer customer) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    // ADM-11 - gives customers a way to cancel, which is what the dispute/cancellation rate counts
    @Transactional
    public void cancel(Customer customer, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ServiceException("Booking not found."));
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ServiceException("You can only manage your own bookings.");
        }
        try {
            booking.cancel();
        } catch (IllegalStateException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Transactional
    public void accept(InteriorDesigner designer, Long bookingId) {
        respond(designer, bookingId, Booking::accept);
    }

    @Transactional
    public void decline(InteriorDesigner designer, Long bookingId) {
        respond(designer, bookingId, Booking::decline);
    }

    private void respond(InteriorDesigner designer, Long bookingId, Consumer<Booking> action) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ServiceException("Booking not found."));
        if (!booking.getDesigner().getId().equals(designer.getId())) {
            throw new ServiceException("You can only manage your own bookings.");
        }
        action.accept(booking);
    }
}
