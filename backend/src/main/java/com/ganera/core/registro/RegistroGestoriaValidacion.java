package com.ganera.core.registro;

import java.util.regex.Pattern;

/**
 * Validacion pura de POST /gestorias/registro, sin dependencias de Spring -- 100% testeable sin
 * MockMvc, siguiendo el mismo estilo que OnboardingController.secretoValido. Deliberadamente no
 * se usa spring-boot-starter-validation (@Valid/@Email) aunque ya este en el classpath: todas las
 * causas de fallo (formato, fuerza de password, campo en blanco, email duplicado) deben colapsar
 * en el mismo mensaje generico, y un ControllerAdvice para eso seria mas codigo que este metodo
 * estatico simple.
 */
class RegistroGestoriaValidacion {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int PASSWORD_LONGITUD_MINIMA = 8;

    private RegistroGestoriaValidacion() {
    }

    static boolean emailValido(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    static boolean passwordValida(String password) {
        return password != null && password.length() >= PASSWORD_LONGITUD_MINIMA;
    }

    private static boolean noEnBlanco(String valor) {
        return valor != null && !valor.isBlank();
    }

    static boolean solicitudValida(RegistroGestoriaRequest request) {
        return request != null
                && noEnBlanco(request.nombreGestoria())
                && noEnBlanco(request.nombreUsuario())
                && emailValido(request.email())
                && passwordValida(request.password())
                && request.rangoClientes() != null;
    }
}
