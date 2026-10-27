package com.csit314.platform.service;

import com.csit314.platform.model.Complaint;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.web.dto.ComplaintForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// ADM-10
@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final InteriorDesignerRepository designerRepository;

    public ComplaintService(ComplaintRepository complaintRepository, InteriorDesignerRepository designerRepository) {
        this.complaintRepository = complaintRepository;
        this.designerRepository = designerRepository;
    }

    @Transactional
    public Complaint submit(Customer customer, Long designerId, ComplaintForm form) {
        String description = InputRules.requireText(form.description(), "Complaint", 2000);
        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("That designer no longer exists."));
        return complaintRepository.save(new Complaint(customer, designer, description));
    }

    public List<Complaint> all() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void resolve(Long complaintId) {
        find(complaintId).resolve();
    }

    @Transactional
    public void dismiss(Long complaintId) {
        find(complaintId).dismiss();
    }

    private Complaint find(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ServiceException("Complaint not found."));
    }
}
