package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Mini-prompt tras A2 (punto 8, decisiones D8.1-D8.3): completo se calcula al serializar a partir
 * de lo ESCRITO (crotalIndicado), nunca del crotal resuelto, y un valor antiguo que no pase la
 * normalizacion no tumba la respuesta.
 */
class TramiteCrotalResponseTest {

    @Test
    void unCrotalEspanolConDoceDigitosEsCompleto() {
        assertThat(TramiteCrotalResponse.from(fila("ES010000001234", "ES010000001234", ResolucionCrotal.NO_ENCONTRADO))
                .completo()).isTrue();
    }

    @Test
    void entreCuatroYDoceDigitosSueltosNoEsCompleto() {
        for (String indicado : new String[] {"1234", "010000001234"}) {
            assertThat(TramiteCrotalResponse.from(fila(indicado, indicado, ResolucionCrotal.NO_ENCONTRADO)).completo())
                    .as(indicado).isFalse();
        }
    }

    /** D8.2: un sufijo resuelto EN_INVENTARIO sigue siendo completo=false (describe lo escrito). */
    @Test
    void unSufijoResueltoEnInventarioSigueSinSerCompleto() {
        TramiteCrotalResponse respuesta = TramiteCrotalResponse.from(
                fila("1234", "ES010000001234", ResolucionCrotal.EN_INVENTARIO));

        assertThat(respuesta.crotal()).isEqualTo("ES010000001234");
        assertThat(respuesta.enInventario()).isTrue();
        assertThat(respuesta.completo()).isFalse();
    }

    /** D8.3: un crotal_indicado antiguo invalido no lanza; completo=false. */
    @Test
    void unCrotalIndicadoInvalidoDaCompletoFalseSinLanzar() {
        for (String invalido : new String[] {"123", "ES*1234", ""}) {
            assertThatCode(() -> TramiteCrotalResponse.from(fila(invalido, invalido, ResolucionCrotal.NO_ENCONTRADO)))
                    .as(invalido).doesNotThrowAnyException();
            assertThat(TramiteCrotalResponse.from(fila(invalido, invalido, ResolucionCrotal.NO_ENCONTRADO)).completo())
                    .as(invalido).isFalse();
        }
    }

    private static TramiteCrotal fila(String indicado, String crotal, ResolucionCrotal resolucion) {
        TramiteCrotal fila = new TramiteCrotal();
        fila.setCrotalIndicado(indicado);
        fila.setCrotal(crotal);
        fila.setResolucion(resolucion);
        return fila;
    }
}
