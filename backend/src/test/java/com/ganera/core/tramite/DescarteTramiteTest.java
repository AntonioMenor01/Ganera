package com.ganera.core.tramite;

import com.ganera.core.gestoria.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla pura de «descartable» (plan 2026-10-12, D3) y lo que exponen las dos respuestas (motivoRechazo,
 * descartable, historialDescarte). Sin Spring ni BD.
 */
class DescarteTramiteTest {

    private static final TramiteCrotalResponse UN_CROTAL =
            new TramiteCrotalResponse("1234", "1234", false, null, false, "NO_ENCONTRADO");

    private static Tramite sinTramite() {
        Tramite tramite = new Tramite();
        tramite.setId(9L);
        tramite.setVersion(2L);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TRAMITE);
        return tramite;
    }

    @Test
    void unPendienteSinTramiteSinTipoNiCrotalesEsDescartable() {
        assertThat(DescarteTramite.esDescartable(sinTramite(), 0)).isTrue();
    }

    @Test
    void conTipoYaNoEsDescartable() {
        for (TipoTramite tipo : TipoTramite.values()) {
            Tramite tramite = sinTramite();
            tramite.setTipoTramite(tipo);
            assertThat(DescarteTramite.esDescartable(tramite, 0)).as(tipo.name()).isFalse();
        }
    }

    @Test
    void conCrotalesYaNoEsDescartable() {
        assertThat(DescarteTramite.esDescartable(sinTramite(), 1)).isFalse();
        assertThat(DescarteTramite.esDescartable(sinTramite(), 3)).isFalse();
    }

    @Test
    void conOtroEstadoDeExtraccionNoEsDescartable() {
        for (EstadoExtraccion estado : Arrays.asList(EstadoExtraccion.PENDIENTE, EstadoExtraccion.COMPLETADA,
                EstadoExtraccion.FALLIDA, EstadoExtraccion.SIN_TEXTO, null)) {
            Tramite tramite = sinTramite();
            tramite.setEstadoExtraccion(estado);
            assertThat(DescarteTramite.esDescartable(tramite, 0)).as(String.valueOf(estado)).isFalse();
        }
    }

    @Test
    void fueraDePendienteDeRevisionNoEsDescartable() {
        for (EstadoTramite estado : EnumSet.complementOf(EnumSet.of(EstadoTramite.PENDIENTE_REVISION))) {
            Tramite tramite = sinTramite();
            tramite.setEstado(estado);
            assertThat(DescarteTramite.esDescartable(tramite, 0)).as(estado.name()).isFalse();
        }
    }

    @Test
    void soloUnRechazadoConMotivoSinTramiteEsUnDescarte() {
        Tramite descartado = sinTramite();
        descartado.setEstado(EstadoTramite.RECHAZADO);
        descartado.setMotivoRechazo(MotivoRechazo.SIN_TRAMITE);
        Tramite rechazoNormal = sinTramite();
        rechazoNormal.setEstado(EstadoTramite.RECHAZADO);

        assertThat(DescarteTramite.esDescarte(descartado)).isTrue();
        assertThat(DescarteTramite.esDescarte(rechazoNormal)).isFalse();
        assertThat(DescarteTramite.esDescarte(sinTramite())).isFalse();
    }

    @Test
    void lasRespuestasExponenMotivoRechazoYDescartableConLosCrotalesQueYaLlevan() {
        Tramite tramite = sinTramite();

        // Sin crotales (en el listado, un tramite sin filas llega con null): descartable.
        assertThat(TramiteResponse.from(tramite, null).descartable()).isTrue();
        assertThat(TramiteResponse.from(tramite, List.of()).descartable()).isTrue();
        assertThat(TramiteResponse.from(tramite, List.of(UN_CROTAL)).descartable()).isFalse();
        assertThat(TramiteDetalleResponse.from(tramite, null, List.of(), null, List.of()).descartable()).isTrue();
        assertThat(TramiteDetalleResponse.from(tramite, null, List.of(UN_CROTAL), null, List.of()).descartable())
                .isFalse();
        assertThat(TramiteResponse.from(tramite, null).motivoRechazo()).isNull();

        tramite.setEstado(EstadoTramite.RECHAZADO);
        tramite.setMotivoRechazo(MotivoRechazo.SIN_TRAMITE);
        TramiteResponse fila = TramiteResponse.from(tramite, null);
        TramiteDetalleResponse detalle = TramiteDetalleResponse.from(tramite, null, null, null, null);
        assertThat(fila.motivoRechazo()).isEqualTo("SIN_TRAMITE");
        assertThat(fila.descartable()).isFalse();
        assertThat(detalle.motivoRechazo()).isEqualTo("SIN_TRAMITE");
        assertThat(detalle.descartable()).isFalse();
        // Sin historial cargado, lista vacia (nunca null en el JSON).
        assertThat(detalle.historialDescarte()).isEmpty();
    }

    @Test
    void elHistorialLlevaTipoNombreDelUsuarioYCuando() {
        Usuario ana = new Usuario();
        ana.setNombre("Ana Pérez");
        ana.setEmail("ana@test.com");
        TramiteEvento evento = new TramiteEvento();
        evento.setTipo(TipoEventoTramite.DESCARTE_DESHECHO);
        evento.setUsuario(ana);
        evento.setCreatedAt(Instant.parse("2026-10-12T08:00:00.123456Z"));

        EventoDescarteResponse respuesta = EventoDescarteResponse.from(evento);

        assertThat(respuesta).isEqualTo(new EventoDescarteResponse(
                "DESCARTE_DESHECHO", "Ana Pérez", Instant.parse("2026-10-12T08:00:00.123456Z")));
    }

    @Test
    void losValoresDeLosEnumsSonLosDelContrato() {
        assertThat(MotivoRechazo.values()).extracting(Enum::name).containsExactly("SIN_TRAMITE");
        assertThat(TipoEventoTramite.values()).extracting(Enum::name)
                .containsExactly("DESCARTADO", "DESCARTE_DESHECHO");
    }
}
