package com.ganera.core.ganadero;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NifValidadorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678Z",       // DNI
            "00000001R",       // DNI con ceros a la izquierda
            "X1234567L",       // NIE (X -> 0)
            "Z1234567R",       // NIE (Z -> 2)
            "B12345674",       // CIF con control numérico
            "B1234567D",       // el mismo CIF con control en letra
    })
    void aceptaIdentificadoresValidos(String nif) {
        assertThat(NifValidador.esValido(nif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678A",       // DNI, letra de control equivocada
            "1234567Z",        // DNI corto
            "X1234567Z",       // NIE, letra equivocada
            "B12345670",       // CIF, control equivocado
            "O1234567A",       // primera letra no es un tipo de organización válido
            "ABCDEFGHI",       // solo letras
            "",                // vacío
    })
    void rechazaIdentificadoresInvalidos(String nif) {
        assertThat(NifValidador.esValido(nif)).isFalse();
    }

    @Test
    void rechazaNull() {
        assertThat(NifValidador.esValido(null)).isFalse();
    }

    @Test
    void normalizaEspaciosPuntosGuionesYMayusculas() {
        assertThat(NifValidador.normalizar(" 12345678-z ")).isEqualTo("12345678Z");
        assertThat(NifValidador.normalizar("b1234567.4")).isEqualTo("B12345674");
        assertThat(NifValidador.normalizar(null)).isEqualTo("");
    }

    @Test
    void validaIgnorandoFormatoDeEntrada() {
        // El mismo DNI con espacios/minúsculas sigue siendo válido tras normalizar.
        assertThat(NifValidador.esValido(" 12345678 z ")).isTrue();
    }
}
