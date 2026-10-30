package com.markethub.service;

import com.markethub.dto.LoginRequest;
import com.markethub.dto.RegisterRequest;
import com.markethub.dto.TokenResponse;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.repository.RoleRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.markethub.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private AuthService authService;

    private final RegisterRequest register = new RegisterRequest("alice", "alice@test.local", "secret1", "Алиса");

    @Test
    void registerCreatesCustomerAndReturnsToken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.local")).thenReturn(false);
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(new Role(Role.CUSTOMER)));
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtService.getTtlSeconds()).thenReturn(900L);

        TokenResponse response = authService.register(register);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateUsernameAndEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(register)).isInstanceOf(ConflictException.class);

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.local")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(register)).isInstanceOf(ConflictException.class);
    }

    @Test
    void loginWithWrongPasswordFails() {
        User alice = user(10L, "alice", Role.CUSTOMER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginOfDisabledUserFails() {
        User alice = user(10L, "alice", Role.CUSTOMER);
        alice.setEnabled(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "secret1")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginReturnsToken() {
        User alice = user(10L, "alice", Role.CUSTOMER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);
        when(jwtService.generateToken(alice)).thenReturn("jwt");

        assertThat(authService.login(new LoginRequest("alice", "secret1")).token()).isEqualTo("jwt");
    }
}
