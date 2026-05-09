package rw.bnr.licensing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.dto.UserRequest;
import rw.bnr.licensing.dto.UserResponse;
import rw.bnr.licensing.enums.UserRole;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public UserResponse createUser(UserRequest request, User admin) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "A user with email " + request.getEmail() + " already exists.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(UserRole.valueOf(request.getRole()))
                .active(true)
                .build();

        User saved = userRepository.save(user);
        auditService.logUserCreated(admin, saved);
        log.info("User created: {} ({}) by admin {}", saved.getEmail(), saved.getRole(), admin.getEmail());
        return toResponse(saved);
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setActive(false);
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .build();
    }
}
