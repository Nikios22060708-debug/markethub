package com.markethub.service;

import com.markethub.dto.PageResponse;
import com.markethub.dto.RoleResponse;
import com.markethub.dto.UserCreateRequest;
import com.markethub.dto.UserResponse;
import com.markethub.dto.UserUpdateRequest;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderRepository;
import com.markethub.repository.RoleRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Управление пользователями и ролями (только для ADMIN).
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       OrderRepository orderRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }

    public UserResponse get(Long id) {
        return UserResponse.from(getEntity(id));
    }

    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(Sort.by("id")).stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Пользователь с логином '" + request.username() + "' уже существует");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' уже используется");
        }
        Set<String> roleNames = request.roles() == null || request.roles().isEmpty()
                ? Set.of(Role.CUSTOMER) : request.roles();
        User user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), request.fullName());
        user.setRoles(resolveRoles(roleNames));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, AuthUser currentUser) {
        User user = getEntity(id);
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ConflictException("Email '" + request.email() + "' уже используется");
        }
        if (id.equals(currentUser.id()) && !request.enabled()) {
            throw new BadRequestException("Нельзя заблокировать собственную учётную запись");
        }
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setEnabled(request.enabled());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateRoles(Long id, Set<String> roleNames, AuthUser currentUser) {
        User user = getEntity(id);
        if (id.equals(currentUser.id()) && !roleNames.contains(Role.ADMIN)) {
            throw new BadRequestException("Нельзя снять роль ADMIN с самого себя");
        }
        user.setRoles(resolveRoles(roleNames));
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id, AuthUser currentUser) {
        User user = getEntity(id);
        if (id.equals(currentUser.id())) {
            throw new BadRequestException("Нельзя удалить собственную учётную запись");
        }
        if (orderRepository.existsByUserId(id)) {
            throw new ConflictException("У пользователя есть заказы — заблокируйте его вместо удаления");
        }
        userRepository.delete(user);
    }

    private User getEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь " + id + " не найден"));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            String normalized = name == null ? "" : name.trim().toUpperCase();
            roles.add(roleRepository.findByName(normalized)
                    .orElseThrow(() -> new BadRequestException("Неизвестная роль: " + name)));
        }
        return roles;
    }
}
