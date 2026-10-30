package com.markethub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Учётные записи администратора и менеджера, создаваемые при первом запуске (app.bootstrap.*). */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminUsername, String adminPassword, String adminEmail,
                                  String managerUsername, String managerPassword, String managerEmail) {
}
