package com.markethub.web;

import com.markethub.dto.RoleResponse;
import com.markethub.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Роли — справочник, создаются миграцией; изменение ролей пользователя — в UserController. */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final UserService userService;

    public RoleController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return userService.listRoles();
    }
}
