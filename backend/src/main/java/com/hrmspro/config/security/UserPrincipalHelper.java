package com.hrmspro.config.security;

import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalHelper {
    private final AppUserRepository appUserRepository;

    public UserPrincipalHelper(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Resolve the {@link AppUser} for the given {@link UserPrincipal}.
     * The principal only contains the userId; we fetch the entity from the DB.
     */
    public AppUser getAppUser(UserPrincipal principal) {
        return appUserRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("AppUser not found for authenticated principal"));
    }
}
