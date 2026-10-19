package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Despliegue en Clever, T3: el origen del frontend que admite CORS es un origen exacto, nunca un comodin. */
class OrigenFrontendTest {

    @Test
    void admiteUnOrigenExactoHttps() {
        assertThat(OrigenFrontend.validar("https://app.ganera.es", true)).isEqualTo("https://app.ganera.es");
        assertThat(OrigenFrontend.validar("https://app.ganera.es:8443", true)).isEqualTo("https://app.ganera.es:8443");
    }

    @Test
    void sinExigirHttpsAdmiteElDeDesarrollo() {
        assertThat(OrigenFrontend.validar("http://localhost:5173", false)).isEqualTo("http://localhost:5173");
    }

    @Test
    void exigiendoHttpsRechazaHttp() {
        assertThatThrownBy(() -> OrigenFrontend.validar("http://app.ganera.es", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_ORIGEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "*", "https://*.ganera.es", "https://app.ganera.es/", "https://app.ganera.es/tramites",
            "https://app.ganera.es?x=1", "https://app.ganera.es#x", "https://usuario@app.ganera.es", "app.ganera.es",
            "ftp://app.ganera.es", "https://", "https://app.ganera.es, https://otra.example.com", " https://app.ganera.es",
            // Puerto por defecto o vacio: el navegador nunca lo manda en Origin, asi que no casaria nunca.
            "https://app.ganera.es:443", "http://localhost:80", "https://app.ganera.es:"})
    void rechazaLoQueNoEsUnOrigenExacto(String origen) {
        assertThatThrownBy(() -> OrigenFrontend.validar(origen, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_ORIGEN");
    }

    @Test
    void rechazaNull() {
        assertThatThrownBy(() -> OrigenFrontend.validar(null, false)).isInstanceOf(IllegalStateException.class);
    }
}
