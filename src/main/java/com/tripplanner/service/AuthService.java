package com.tripplanner.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tripplanner.dto.request.GoogleAuthRequest;
import com.tripplanner.dto.request.RefreshTokenRequest;
import com.tripplanner.dto.response.AuthResponse;
import com.tripplanner.entity.RefreshToken;
import com.tripplanner.entity.User;
import com.tripplanner.repository.RefreshTokenRepository;
import com.tripplanner.repository.UserRepository;
import com.tripplanner.security.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private GoogleIdTokenVerifier googleVerifier;

    @PostConstruct
    public void init() {
        googleVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyGoogleToken(request.idToken());

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepository.findByGoogleId(googleId).orElseGet(() ->
                userRepository.findByEmail(email).map(existing -> {
                    existing.setGoogleId(googleId);
                    if (existing.getName() == null) existing.setName(name);
                    return userRepository.save(existing);
                }).orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(name)
                        .googleId(googleId)
                        .build()))
        );

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired, please log in again");
        }

        refreshTokenRepository.delete(stored);
        return issueTokenPair(stored.getUser());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail(), user.getName());
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idToken) {
        try {
            GoogleIdToken token = googleVerifier.verify(idToken);
            if (token == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            return token.getPayload();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to verify Google ID token", e);
        }
    }
}
