package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * origen, estadoExtraccion y crotalesDescartados (B1, T3) en las dos respuestas de trámite. Salen de
 * columnas del propio Tramite; el motivo técnico del fallo de la IA no está en la entidad y nunca
 * llega a la respuesta.
 */
class TramiteResponseOrigenTest {

    private static Tramite tramite(OrigenTramite origen, EstadoExtraccion estado, int descartados) {
        Tramite tramite = new Tramite();
        tramite.setId(7L);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setVersion(3L);
        tramite.setOrigen(origen);
        tramite.setEstadoExtraccion(estado);
        tramite.setCrotalesDescartados(descartados);
        return tramite;
    }

    @Test
    void elListadoLlevaOrigenEstadoDeExtraccionYDescartadosDeWhatsApp() {
        for (EstadoExtraccion estado : EstadoExtraccion.values()) {
            TramiteResponse respuesta = TramiteResponse.from(tramite(OrigenTramite.WHATSAPP, estado, 2), List.of());

            assertThat(respuesta.origen()).isEqualTo("WHATSAPP");
            assertThat(respuesta.estadoExtraccion()).isEqualTo(estado.name());
            assertThat(respuesta.crotalesDescartados()).isEqualTo(2);
        }
    }

    @Test
    void elDetalleLlevaOrigenEstadoDeExtraccionYDescartadosDeWhatsApp() {
        for (EstadoExtraccion estado : EstadoExtraccion.values()) {
            TramiteDetalleResponse respuesta = TramiteDetalleResponse.from(
                    tramite(OrigenTramite.WHATSAPP, estado, 5), "texto", List.of());

            assertThat(respuesta.origen()).isEqualTo("WHATSAPP");
            assertThat(respuesta.estadoExtraccion()).isEqualTo(estado.name());
            assertThat(respuesta.crotalesDescartados()).isEqualTo(5);
        }
    }

    @Test
    void unTramiteSinOrigenDaNullYCeroEnLasDosRespuestas() {
        Tramite anterior = tramite(null, null, 0);

        TramiteResponse fila = TramiteResponse.from(anterior, null);
        TramiteDetalleResponse detalle = TramiteDetalleResponse.from(anterior, null, null);

        assertThat(fila.origen()).isNull();
        assertThat(fila.estadoExtraccion()).isNull();
        assertThat(fila.crotalesDescartados()).isZero();
        assertThat(detalle.origen()).isNull();
        assertThat(detalle.estadoExtraccion()).isNull();
        assertThat(detalle.crotalesDescartados()).isZero();
    }

    @Test
    void losValoresDeLosEnumsSonLosDelContratoJson() {
        assertThat(OrigenTramite.values()).extracting(Enum::name).containsExactly("WHATSAPP");
        assertThat(EstadoExtraccion.values()).extracting(Enum::name)
                .containsExactly("PENDIENTE", "COMPLETADA", "FALLIDA", "SIN_TEXTO");
    }
}
