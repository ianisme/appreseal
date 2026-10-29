package com.appreseal.security;

import com.appreseal.domain.Role;

public record CurrentUser(String id, String email, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
