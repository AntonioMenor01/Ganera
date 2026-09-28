package com.ganera.core.explotacion;

import com.ganera.core.contacto.ContactoService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que texto ve el usuario en un error de fila de la hoja Contactos: solo los errores de fila
 * esperados (FilaImportacionException) muestran su mensaje; el UNIQUE de telefono da el mensaje
 * generico de telefono; cualquier otra excepcion da un texto generico y nunca su mensaje interno.
 */
class ExplotacionImportMotivoContactoTest {

    @Test
    void errorDeFilaEsperadoMuestraSuMotivo() {
        assertThat(ExplotacionImportService.motivoContactoDe(new FilaImportacionException("La explotacion 'X' no existe")))
                .isEqualTo("La explotacion 'X' no existe");
    }

    @Test
    void violacionDeUnicidadDaElMensajeGenericoDeTelefono() {
        assertThat(ExplotacionImportService.motivoContactoDe(
                new DataIntegrityViolationException("duplicate key contacto_telefono_key gestoria_id=7")))
                .isEqualTo(ContactoService.MOTIVO_TELEFONO_EN_USO);
    }

    @Test
    void excepcionInesperadaDaTextoGenericoSinSuMensajeInterno() {
        String motivo = ExplotacionImportService.motivoContactoDe(
                new IllegalStateException("detalle interno: gestoria 7, SQL select * from contacto"));
        assertThat(motivo).isEqualTo(ExplotacionImportService.MOTIVO_CONTACTO_GENERICO);
        assertThat(motivo).doesNotContain("detalle interno").doesNotContain("SQL");

        assertThat(ExplotacionImportService.motivoContactoDe(new RuntimeException("otro detalle interno")))
                .isEqualTo(ExplotacionImportService.MOTIVO_CONTACTO_GENERICO);
    }
}
