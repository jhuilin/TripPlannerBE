package com.tripplanner.security;

import com.tripplanner.entity.User;
import com.tripplanner.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /** Returns only the userId from the JWT claim — no DB query. */
    public Long resolveId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jp) return jp.userId();
        return resolve().getId();
    }

    /** Loads the full User entity from the DB. Use only when the entity itself is needed. */
    public User resolve() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jp) {
            return userRepository.findById(jp.userId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
        }
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
