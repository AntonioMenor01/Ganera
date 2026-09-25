package com.ganera.core.registro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroGestoriaValidacionTest {

    @Test
    void emailConFormatoValidoEsValido() {
        assertThat(RegistroGestoriaValidacion.emailValido("gestoria@ejemplo.com")).isTrue();
    }

    @Test
    void emailSinArrobaNoEsValido() {
        assertThat(RegistroGestoriaValidacion.emailValido("gestoria-ejemplo.com")).isFalse();
    }

    @Test
    void emailSinDominioNoEsValido() {
        assertThat(RegistroGestoriaValidacion.emailValido("gestoria@ejemplo")).isFalse();
    }

    @Test
    void emailNuloNoEsValido() {
        assertThat(RegistroGestoriaValidacion.emailValido(null)).isFalse();
    }

    @Test
    void passwordDeOchoOMasCaracteresEsValida() {
        assertThat(RegistroGestoriaValidacion.passwordValida("12345678")).isTrue();
    }

    @Test
    void passwordDeMenosDeOchoCaracteresNoEsValida() {
        assertThat(RegistroGestoriaValidacion.passwordValida("1234567")).isFalse();
    }

    @Test
    void passwordNulaNoEsValida() {
        assertThat(RegistroGestoriaValidacion.passwordValida(null)).isFalse();
    }

    @Test
    void solicitudCompletaYValidaEsValida() {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "Gestoria Valida", "Empleado Uno", "empleado@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);

        assertThat(RegistroGestoriaValidacion.solicitudValida(request)).isTrue();
    }

    @Test
    void solicitudConNombreGestoriaEnBlancoNoEsValida() {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "  ", "Empleado Uno", "empleado@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);

        assertThat(RegistroGestoriaValidacion.solicitudValida(request)).isFalse();
    }

    @Test
    void solicitudSinRangoDeClientesNoEsValida() {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "Gestoria Valida", "Empleado Uno", "empleado@gestoria.com", "password123", null);

        assertThat(RegistroGestoriaValidacion.solicitudValida(request)).isFalse();
    }

    @Test
    void solicitudNulaNoEsValida() {
        assertThat(RegistroGestoriaValidacion.solicitudValida(null)).isFalse();
    }
}
