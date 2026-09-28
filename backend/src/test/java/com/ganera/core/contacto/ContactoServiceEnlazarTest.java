package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision 15, a traves de enlazar(): prueba que el servicio LLAMA a comprobarMismaGestoria y no
 * solo que el helper funciona. Los repositorios (mocks, no SDKs externos) simulan un finder con
 * scope roto que devuelve una Explotacion de otra Gestoria; por entradas reales es inalcanzable,
 * precisamente por eso la comprobacion es defensa en profundidad y necesita este test.
 */
class ContactoServiceEnlazarTest {

    private static final long GESTORIA_A = 1L;
    private static final long GESTORIA_B = 2L;
    private static final long CONTACTO_ID = 10L;
    private static final long EXPLOTACION_ID = 20L;

    private final ContactoRepository contactoRepository = mock(ContactoRepository.class);
    private final ContactoExplotacionRepository contactoExplotacionRepository =
            mock(ContactoExplotacionRepository.class);
    private final ExplotacionRepository explotacionRepository = mock(ExplotacionRepository.class);
    private final GestoriaRepository gestoriaRepository = mock(GestoriaRepository.class);

    private final ContactoService servicio = new ContactoService(
            contactoRepository, contactoExplotacionRepository, explotacionRepository, gestoriaRepository);

    @Test
    void enlazarRechazaUnaExplotacionDeOtraGestoriaAunqueElFinderLaDevuelvaYNoGuardaNada() {
        when(contactoRepository.findByIdAndGestoriaId(CONTACTO_ID, GESTORIA_A))
                .thenReturn(Optional.of(contacto(GESTORIA_A)));
        when(explotacionRepository.findByIdAndGestoriaId(EXPLOTACION_ID, GESTORIA_A))
                .thenReturn(Optional.of(explotacion(GESTORIA_B)));

        assertThatThrownBy(() -> servicio.enlazar(GESTORIA_A, CONTACTO_ID, EXPLOTACION_ID, RolContacto.TITULAR))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(contactoExplotacionRepository, never()).save(any());
        verify(contactoExplotacionRepository, never()).saveAndFlush(any());
    }

    private static Contacto contacto(long gestoriaId) {
        Contacto contacto = new Contacto();
        contacto.setId(CONTACTO_ID);
        contacto.setGestoria(gestoria(gestoriaId));
        return contacto;
    }

    private static Explotacion explotacion(long gestoriaId) {
        Explotacion explotacion = new Explotacion();
        explotacion.setId(EXPLOTACION_ID);
        explotacion.setGestoria(gestoria(gestoriaId));
        return explotacion;
    }

    private static Gestoria gestoria(long id) {
        Gestoria gestoria = new Gestoria("Gestoria " + id);
        gestoria.setId(id);
        return gestoria;
    }
}
