package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ShortlistRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// a separate bean, not a private method on FavouriteService - Spring's proxy-based AOP
// only intercepts calls that come from outside the bean, so @Transactional(REQUIRES_NEW)
// on a method FavouriteService called on itself would silently be ignored
@Component
class ShortlistWriter {

    private final ShortlistRepository shortlistRepository;

    ShortlistWriter(ShortlistRepository shortlistRepository) {
        this.shortlistRepository = shortlistRepository;
    }

    // IDR-10/11 - runs in its own transaction, independent of the caller's, so a
    // constraint violation here only rolls back this insert, not whatever the caller's
    // transaction was doing. The violation is deliberately NOT caught in here: save() is
    // itself @Transactional and joins this same (REQUIRES_NEW) transaction, so it marks
    // THIS transaction rollback-only the moment the exception crosses its boundary - a
    // catch block here would run, but this method's own commit would still fail with
    // UnexpectedRollbackException. Letting the exception propagate instead makes Spring
    // roll this transaction back cleanly, and the caller - running in its own, separate,
    // untouched transaction - can catch it safely once this method returns to it.
    //
    // Anthony's review raised a real concern with this REQUIRES_NEW commiting
    // independently of the caller's transaction: if the caller's transaction later
    // failed for some unrelated reason after this had already committed, a counter
    // the caller was maintaining elsewhere (Project.shortlistCount) could end up
    // permanently out of sync with this table. PROPAGATION_NESTED (a savepoint inside
    // the same transaction, rolling back together with it) would avoid that - but it
    // isn't usable here: the default Hibernate JPA dialect this project uses doesn't
    // support savepoints (confirmed by trying it: NestedTransactionNotSupportedException).
    // The actual fix for the orphaning risk is Project.shortlistCount itself: it's a
    // Hibernate @Formula (a live count(*) against this table) now, not a counter
    // anything increments, so there's nothing left for a REQUIRES_NEW commit to
    // desynchronise from - see its own field comment
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(Customer customer, Project project) {
        shortlistRepository.save(new Shortlist(customer, project));
    }
}
