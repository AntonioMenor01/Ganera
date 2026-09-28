package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.gestoria.Gestoria;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decision 15: la comprobacion defensiva del enlace Contacto-Explotacion. Por entradas normales
 * es inalcanzable (los dos extremos ya se cargan con finders con gestoriaId explicito), asi que se
 * prueba el helper directamente con entidades cuyas Gestorias no coinciden: si un finder futuro
 * dejara de filtrar por Gestoria, esta comprobacion es la que tiene que parar el enlace.
 */
class ContactoServiceMismaGestoriaTest {

    private static final long GESTORIA_A = 1L;
    private static final long GESTORIA_B = 2L;

    @Test
    void aceptaCuandoContactoExplotacionYUsuarioSonDeLaMismaGestoria() {
        assertThatCode(() -> ContactoService.comprobarMismaGestoria(
                contacto(GESTORIA_A), explotacion(GESTORIA_A), GESTORIA_A))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaExplotacionDeOtraGestoria() {
        assertThatThrownBy(() -> ContactoService.comprobarMismaGestoria(
                contacto(GESTORIA_A), explotacion(GESTORIA_B), GESTORIA_A))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazaContactoDeOtraGestoria() {
        assertThatThrownBy(() -> ContactoService.comprobarMismaGestoria(
                contacto(GESTORIA_B), explotacion(GESTORIA_A), GESTORIA_A))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazaCuandoAmbosExtremosCoincidenPeroNoSonDelUsuarioAutenticado() {
        assertThatThrownBy(() -> ContactoService.comprobarMismaGestoria(
                contacto(GESTORIA_B), explotacion(GESTORIA_B), GESTORIA_A))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazaGestoriaNulaEnCualquierExtremoOEnElUsuario() {
        assertThatThrownBy(() -> ContactoService.comprobarMismaGestoria(
                new Contacto(), explotacion(GESTORIA_A), GESTORIA_A))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> ContactoService.comprobarMismaGestoria(
                contacto(GESTORIA_A), explotacion(GESTORIA_A), null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static Contacto contacto(long gestoriaId) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria(gestoriaId));
        return contacto;
    }

    private static Explotacion explotacion(long gestoriaId) {
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria(gestoriaId));
        return explotacion;
    }

    private static Gestoria gestoria(long id) {
        Gestoria gestoria = new Gestoria("Gestoria " + id);
        gestoria.setId(id);
        return gestoria;
    }
}
