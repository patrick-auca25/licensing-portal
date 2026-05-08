package rw.bnr.licensing.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.UserRepository;

import java.util.List;

/**
 * UserDetailsServiceImpl — Spring Security calls this to load a user
 * from the database during authentication.
 *
 * Spring Security needs a UserDetails object to:
 * 1. Verify the password during login
 * 2. Load the authorities (roles) for authorization decisions
 *
 * We prefix the role with "ROLE_" because Spring Security's
 * hasRole('REVIEWER') internally checks for "ROLE_REVIEWER".
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("User account is deactivated: " + email);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
