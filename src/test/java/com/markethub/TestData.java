package com.markethub;

import com.markethub.entity.Address;
import com.markethub.entity.Category;
import com.markethub.entity.Product;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.security.AuthUser;

import java.math.BigDecimal;
import java.util.Set;

/** Фабрика тестовых объектов для unit-тестов. */
public final class TestData {

    private TestData() {
    }

    public static User user(Long id, String username, String... roles) {
        User user = new User(username, username + "@test.local", "hash", username);
        user.setId(id);
        for (String role : roles) {
            user.getRoles().add(new Role(role));
        }
        return user;
    }

    public static AuthUser auth(Long id, String username, String... roles) {
        return new AuthUser(id, username, Set.of(roles));
    }

    public static Category category(Long id, String name) {
        Category category = new Category(name, null);
        category.setId(id);
        return category;
    }

    public static Product product(Long id, String name, String price, int stock, Category category) {
        Product product = new Product(name, null, new BigDecimal(price), stock, category);
        product.setId(id);
        return product;
    }

    public static Address address(Long id, User owner) {
        Address address = new Address(owner, "Дом", "Москва", "Тверская, 1", "101000");
        address.setId(id);
        return address;
    }
}
