package com.markethub.config;

import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.RoleRepository;
import com.markethub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт администратора и менеджера при старте, если их ещё нет.
 * Пароли берутся из конфигурации/переменных окружения и хешируются BCrypt,
 * поэтому в миграциях не хранится ни одного пароля.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapProperties properties, UserRepository userRepository,
                          RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing(properties.adminUsername(), properties.adminPassword(), properties.adminEmail(),
                "Администратор", Role.ADMIN);
        createIfMissing(properties.managerUsername(), properties.managerPassword(), properties.managerEmail(),
                "Менеджер", Role.MANAGER);
    }

    private void createIfMissing(String username, String password, String email, String fullName, String roleName) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (userRepository.existsByUsername(username)) {
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Роль " + roleName + " не найдена"));
        User user = new User(username, email, passwordEncoder.encode(password), fullName);
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Создан пользователь '{}' с ролью {}", username, roleName);
    }
}
