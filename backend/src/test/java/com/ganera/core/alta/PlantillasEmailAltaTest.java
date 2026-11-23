package com.ganera.core.alta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contenido de las dos plantillas de email del alta automatica (clase pura, sin enviar nada). */
class PlantillasEmailAltaTest {

    @Test
    void laBienvenidaLlevaEnlace_emailAcceso_password_avisoDeCambioY15Dias() {
        PlantillasEmailAlta.Email email = PlantillasEmailAlta.bienvenida(
                "Cliente Uno", "cliente@landing.test", "ClaveTemporal123", "https://app.ganera.es");

        assertThat(email.asunto()).contains("Bienvenido");
        assertThat(email.cuerpoHtml())
                .contains("Cliente Uno")
                .contains("https://app.ganera.es")
                .contains("cliente@landing.test")
                .contains("ClaveTemporal123")
                .contains("15 días")
                .containsIgnoringCase("cambia la contraseña");
    }

    @Test
    void laBienvenidaSinNombreSaludaDeFormaGenerica() {
        PlantillasEmailAlta.Email email = PlantillasEmailAlta.bienvenida(
                null, "cliente@landing.test", "ClaveTemporal123", "https://app.ganera.es");

        assertThat(email.cuerpoHtml()).contains("Hola,");
    }

    @Test
    void elAvisoAlEquipoLlevaNombreEmailNifYPlanYNoLlevaContrasena() {
        PlantillasEmailAlta.Email email = PlantillasEmailAlta.equipo(
                "Cliente Uno", "cliente@landing.test", "12345678Z", "gestion");

        assertThat(email.asunto()).contains("Nueva alta");
        assertThat(email.cuerpoHtml())
                .contains("Cliente Uno")
                .contains("cliente@landing.test")
                .contains("12345678Z")
                .contains("gestion");
        // El aviso al equipo nunca lleva la contrasena del cliente.
        assertThat(email.cuerpoHtml()).doesNotContainIgnoringCase("contraseña");
    }

    @Test
    void escapaElHtmlDeLosDatosDelCliente() {
        PlantillasEmailAlta.Email email = PlantillasEmailAlta.equipo(
                "<script>", "a@b.test", "x", "base");

        assertThat(email.cuerpoHtml()).contains("&lt;script&gt;").doesNotContain("<script>");
    }
}
