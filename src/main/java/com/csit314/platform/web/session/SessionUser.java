package com.csit314.platform.web.session;

import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;

import java.io.Serializable;

// small snapshot of the logged-in user kept in the HTTP session (no entity, no password hash)
public record SessionUser(Long id, String name, String email, Role role) implements Serializable {

    public static SessionUser from(UserAccount user) {
        return new SessionUser(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }
}
