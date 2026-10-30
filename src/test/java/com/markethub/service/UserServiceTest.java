package com.markethub.service;

import com.markethub.dto.UserCreateRequest;
import com.markethub.dto.UserUpdateRequest;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.ConflictException;
import com.markethub.repository.OrderRepository;
import com.markethub.repository.RoleRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static com.markethub.TestData.auth;
import static com.markethub.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    private final AuthUser admin = auth(1L, "admin", Role.ADMIN);

    @Test
    void createWithoutRolesGetsCustomerRole() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@test.local")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hash");
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(new Role(Role.CUSTOMER)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = userService.create(new UserCreateRequest("bob", "bob@test.local", "secret1", null, null));

        assertThat(created.roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void unknownRoleIsBadRequest() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@test.local")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hash");
        when(roleRepository.findByName("GOD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("bob", "bob@test.local", "secret1", null, Set.of("god"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCannotDeleteOrDemoteOrDisableThemselves() {
        User self = user(1L, "admin", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));
        when(userRepository.existsByEmailAndIdNot("admin@test.local", 1L)).thenReturn(false);

        assertThatThrownBy(() -> userService.delete(1L, admin)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.updateRoles(1L, Set.of(Role.CUSTOMER), admin))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.update(1L,
                new UserUpdateRequest("admin@test.local", "A", false, null), admin))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void userWithOrdersCannotBeDeleted() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "bob", Role.CUSTOMER)));
        when(orderRepository.existsByUserId(5L)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(5L, admin)).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateChangesPasswordOnlyWhenProvided() {
        User bob = user(5L, "bob", Role.CUSTOMER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(bob));
        when(userRepository.existsByEmailAndIdNot("new@test.local", 5L)).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");

        userService.update(5L, new UserUpdateRequest("new@test.local", "Боб", true, "newpass"), admin);

        assertThat(bob.getEmail()).isEqualTo("new@test.local");
        assertThat(bob.getPasswordHash()).isEqualTo("new-hash");
        verify(passwordEncoder).encode("newpass");
    }
}
