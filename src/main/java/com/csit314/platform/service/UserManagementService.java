package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.UserSummary;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// User Admin actions on registered accounts
@Service
public class UserManagementService {

    private final UserAccountRepository userAccountRepository;
    private final InteriorDesignerRepository designerRepository;

    public UserManagementService(UserAccountRepository userAccountRepository,
                                 InteriorDesignerRepository designerRepository) {
        this.userAccountRepository = userAccountRepository;
        this.designerRepository = designerRepository;
    }

    // ADM-02: every registered account, newest first
    @Transactional(readOnly = true)
    public List<UserSummary> listAllUsers() {
        return userAccountRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(user -> new UserSummary(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getRole().getLabel(),
                        user.getStatus().getLabel()))
                .toList();
    }

    // ADM-03
    @Transactional
    public InteriorDesigner approveDesigner(Long designerId) {
        InteriorDesigner designer = findPendingDesigner(designerId);
        designer.approve();
        return designer;
    }

    // ADM-04
    @Transactional
    public InteriorDesigner rejectDesigner(Long designerId, String reason) {
        String trimmedReason = InputRules.requireText(reason, "A reason for rejecting", 1000);
        InteriorDesigner designer = findPendingDesigner(designerId);
        designer.reject(trimmedReason);
        return designer;
    }

    private InteriorDesigner findPendingDesigner(Long designerId) {
        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("Interior Designer registration not found."));
        if (designer.getStatus() != AccountStatus.PENDING) {
            throw new ServiceException("This registration has already been reviewed.");
        }
        return designer;
    }
}
