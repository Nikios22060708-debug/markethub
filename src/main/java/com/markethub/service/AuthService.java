package com.markethub.service;

import com.markethub.dto.LoginRequest;
import com.markethub.dto.RegisterRequest;
import com.markethub.dto.TokenResponse;
import com.markethub.dto.UserResponse;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.RoleRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import com.markethub.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация, вход и выдача JWT.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Самостоятельная регистрация всегда даёт роль CUSTOMER. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Пользователь с логином '" + request.username() + "' уже существует");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' уже используется");
        }
        Role customer = roleRepository.findByName(Role.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Роль CUSTOMER не найдена"));
        User user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), request.fullName());
        user.getRoles().add(customer);
        User saved = userRepository.save(user);
        log.info("Зарегистрирован пользователь '{}'", saved.getUsername());
        return tokenFor(saved);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Неудачная попытка входа для '{}'", request.username());
                    return new BadCredentialsException("Неверный логин или пароль");
                });
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Учётная запись заблокирована");
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser authUser) {
        return userRepository.findById(authUser.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private TokenResponse tokenFor(User user) {
        return new TokenResponse(jwtService.generateToken(user), "Bearer", jwtService.getTtlSeconds(),
                UserResponse.from(user));
    }
}
