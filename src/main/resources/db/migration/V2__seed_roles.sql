-- Справочник ролей. Пользователи-администраторы создаются при старте (AdminBootstrap),
-- чтобы пароль не хранился в миграциях.
INSERT INTO roles (name) VALUES ('ADMIN');
INSERT INTO roles (name) VALUES ('MANAGER');
INSERT INTO roles (name) VALUES ('CUSTOMER');
