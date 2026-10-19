package com.ganera.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Sin UserDetailsServiceAutoConfiguration: la app autentica con JWT (POST /auth/login contra Usuario) y no usa
// httpBasic ni formLogin. Sin la exclusion, Spring Boot creaba un usuario "user" en memoria e imprimia su
// contrasena en el log de arranque (despliegue en Clever, T1; SinUsuarioPorDefectoTest).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class GaneraApplication {
    public static void main(String[] args) {
        SpringApplication.run(GaneraApplication.class, args);
    }
}
