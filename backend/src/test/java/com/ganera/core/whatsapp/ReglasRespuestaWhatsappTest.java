package com.ganera.core.whatsapp;

import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.MotivoRechazo;
import com.ganera.core.tramite.OrigenTramite;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas puras de la respuesta por WhatsApp (B2 reducido, T1): ventana, disponibilidad, motivos y
 * estado efectivo. Sin Spring ni Twilio. */
class ReglasRespuestaWhatsappTest {

    private static final Instant ULTIMO = Instant.parse("2031-03-10T09:00:00Z");
    private static final Duration UN_SEGUNDO = Duration.ofSeconds(1);
    private static final Duration VEINTICUATRO_HORAS = Duration.ofHours(24);

    // ------------------------------------------------------------- ventana de 24 h

    @Test
    void laVentanaSeCierraVeinticuatroHorasDespuesDelUltimoMensaje() {
        assertThat(ReglasRespuestaWhatsapp.ventanaAbiertaHasta(ULTIMO)).isEqualTo(ULTIMO.plus(VEINTICUATRO_HORAS));
        assertThat(ReglasRespuestaWhatsapp.ventanaAbiertaHasta(null)).isNull();
    }

    @Test
    void laVentanaEstaAbiertaHastaUnSegundoAntesYCerradaEnElLimiteEstricto() {
        Instant hasta = ReglasRespuestaWhatsapp.ventanaAbiertaHasta(ULTIMO);

        assertThat(ReglasRespuestaWhatsapp.ventanaAbierta(hasta, hasta.minus(UN_SEGUNDO))).isTrue();
        assertThat(ReglasRespuestaWhatsapp.ventanaAbierta(hasta, hasta)).isFalse();
        assertThat(ReglasRespuestaWhatsapp.ventanaAbierta(hasta, hasta.plus(UN_SEGUNDO))).isFalse();
        // Sin ningun mensaje del contacto no hay ventana.
        assertThat(ReglasRespuestaWhatsapp.ventanaAbierta(null, ULTIMO)).isFalse();
    }

    // ------------------------------------------------------------- disponibilidad

    @Test
    void sePuedeResponderEnLosCuatroEstadosPermitidosConContactoActivoYVentanaAbierta() {
        Instant hasta = ULTIMO.plus(VEINTICUATRO_HORAS);
        for (EstadoTramite estado : EnumSet.of(EstadoTramite.PENDIENTE_REVISION, EstadoTramite.APROBADO,
                EstadoTramite.HECHO_EN_OVZ, EstadoTramite.RECHAZADO)) {
            assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, estado, null, true, hasta, ULTIMO))
                    .as(estado.name()).isNull();
        }
    }

    @Test
    void enLosDemasEstadosNoSePuedeResponder() {
        Instant hasta = ULTIMO.plus(VEINTICUATRO_HORAS);
        Set<EstadoTramite> noPermitidos = EnumSet.of(EstadoTramite.PENDIENTE_EXTRACCION, EstadoTramite.EN_PROCESO,
                EstadoTramite.EJECUTADO_OVZ, EstadoTramite.ERROR_OVZ);
        assertThat(noPermitidos).hasSize(EstadoTramite.values().length - 4);
        for (EstadoTramite estado : noPermitidos) {
            assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, estado, null, true, hasta, ULTIMO))
                    .as(estado.name()).isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_ESTADO_NO_PERMITIDO);
        }
    }

    @Test
    void elOrdenEsOrigenEstadoContactoYVentana() {
        Instant cerrada = ULTIMO.minus(UN_SEGUNDO);
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(null, EstadoTramite.PENDIENTE_EXTRACCION, null, false, cerrada, ULTIMO))
                .isEqualTo("Este trámite no llegó por WhatsApp; no hay a quién responder.");
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.PENDIENTE_EXTRACCION, null,
                false, cerrada, ULTIMO))
                .isEqualTo("En este estado no se puede responder por WhatsApp.");
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.APROBADO, null,
                false, cerrada, ULTIMO))
                .isEqualTo("El contacto está dado de baja; no se le puede escribir.");
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.APROBADO, null,
                true, cerrada, ULTIMO))
                .isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_FUERA_DE_VENTANA);
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.APROBADO, null,
                true, null, ULTIMO))
                .isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_FUERA_DE_VENTANA);
    }

    /** SIN_TRAMITE (D9): un descartado no se responde, aunque el contacto este activo y la ventana abierta;
     * va antes que el contacto y la ventana (es una decision del gestor, no un limite de WhatsApp). */
    @Test
    void unDescartadoComoSinTramiteNoSeRespondeConSuPropioMotivo() {
        Instant hasta = ULTIMO.plus(VEINTICUATRO_HORAS);
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.RECHAZADO,
                MotivoRechazo.SIN_TRAMITE, true, hasta, ULTIMO))
                .isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_DESCARTADO)
                .isEqualTo("Este mensaje se descartó como sin trámite; no se responde desde Ganera.");
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.RECHAZADO,
                MotivoRechazo.SIN_TRAMITE, false, ULTIMO.minus(UN_SEGUNDO), ULTIMO))
                .isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_DESCARTADO);
    }

    /** Un rechazo normal (sin motivo) se sigue pudiendo responder, como hasta ahora. */
    @Test
    void unRechazoNormalSeSigueRespondiendo() {
        Instant hasta = ULTIMO.plus(VEINTICUATRO_HORAS);
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.RECHAZADO,
                null, true, hasta, ULTIMO)).isNull();
        assertThat(ReglasRespuestaWhatsapp.motivoNoDisponible(OrigenTramite.WHATSAPP, EstadoTramite.PENDIENTE_REVISION,
                null, true, hasta, ULTIMO)).isNull();
    }

    @Test
    void laVentanaCerradaYElCodigo63016DicenLoMismo() {
        assertThat(ReglasRespuestaWhatsapp.MOTIVO_FUERA_DE_VENTANA)
                .isEqualTo("Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta "
                        + "que vuelva a escribir.")
                .isEqualTo(ReglasRespuestaWhatsapp.motivoPorCodigo(63016));
    }

    // ------------------------------------------------------------- motivo por codigo de Twilio

    @Test
    void cadaCodigoConocidoTieneSuMotivoEnEspanol() {
        assertThat(ReglasRespuestaWhatsapp.motivoPorCodigo(63015))
                .isEqualTo("Este número no se ha unido al sandbox de WhatsApp de pruebas.");
        assertThat(ReglasRespuestaWhatsapp.motivoPorCodigo(21610))
                .isEqualTo("El contacto ha bloqueado los mensajes de este número.");
        assertThat(ReglasRespuestaWhatsapp.motivoPorCodigo(30008))
                .isEqualTo("WhatsApp no ha aceptado el mensaje (código 30008).");
        assertThat(ReglasRespuestaWhatsapp.motivoPorCodigo(null)).isEqualTo("WhatsApp no ha aceptado el mensaje.");
    }

    @Test
    void elMotivoDeLaRespuestaDependeDeSuEstado() {
        assertThat(ReglasRespuestaWhatsapp.motivoError(EstadoRespuestaWhatsapp.FALLIDO, 63016))
                .isEqualTo(ReglasRespuestaWhatsapp.MOTIVO_FUERA_DE_VENTANA);
        assertThat(ReglasRespuestaWhatsapp.motivoError(EstadoRespuestaWhatsapp.FALLIDO, null))
                .isEqualTo("WhatsApp no ha aceptado el mensaje.");
        assertThat(ReglasRespuestaWhatsapp.motivoError(EstadoRespuestaWhatsapp.SIN_CONFIRMAR, null))
                .isEqualTo("No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.");
        for (EstadoRespuestaWhatsapp estado : EnumSet.of(EstadoRespuestaWhatsapp.ENVIANDO, EstadoRespuestaWhatsapp.ENVIADO,
                EstadoRespuestaWhatsapp.ENTREGADO, EstadoRespuestaWhatsapp.LEIDO)) {
            assertThat(ReglasRespuestaWhatsapp.motivoError(estado, 63016)).as(estado.name()).isNull();
        }
    }

    // ------------------------------------------------------------- estado efectivo

    @Test
    void unaRespuestaEnviandoDeHaceMasDeDosMinutosSeExponeSinConfirmar() {
        Instant creada = ULTIMO;
        Duration dosMinutos = Duration.ofMinutes(2);

        assertThat(ReglasRespuestaWhatsapp.estadoEfectivo(EstadoRespuestaWhatsapp.ENVIANDO, creada,
                creada.plus(dosMinutos))).isEqualTo(EstadoRespuestaWhatsapp.ENVIANDO);
        assertThat(ReglasRespuestaWhatsapp.estadoEfectivo(EstadoRespuestaWhatsapp.ENVIANDO, creada,
                creada.plus(dosMinutos).plus(UN_SEGUNDO))).isEqualTo(EstadoRespuestaWhatsapp.SIN_CONFIRMAR);
        // Los demas estados no cambian con el tiempo.
        for (EstadoRespuestaWhatsapp estado : EstadoRespuestaWhatsapp.values()) {
            if (estado != EstadoRespuestaWhatsapp.ENVIANDO) {
                assertThat(ReglasRespuestaWhatsapp.estadoEfectivo(estado, creada, creada.plus(Duration.ofDays(9))))
                        .isEqualTo(estado);
            }
        }
    }

    @Test
    void elContactoTieneQueSerDeLaGestoriaDelJwt() {
        com.ganera.core.gestoria.Gestoria gestoria = new com.ganera.core.gestoria.Gestoria("G");
        gestoria.setId(7L);
        com.ganera.core.contacto.Contacto contacto = new com.ganera.core.contacto.Contacto();
        contacto.setGestoria(gestoria);

        assertThat(ReglasRespuestaWhatsapp.contactoDeLaGestoria(contacto, 7L)).isTrue();
        assertThat(ReglasRespuestaWhatsapp.contactoDeLaGestoria(contacto, 8L)).isFalse();
        assertThat(ReglasRespuestaWhatsapp.contactoDeLaGestoria(new com.ganera.core.contacto.Contacto(), 7L)).isFalse();
        assertThat(ReglasRespuestaWhatsapp.contactoDeLaGestoria(null, 7L)).isFalse();
    }

    @Test
    void losValoresDelEnumSonLosDelContrato() {
        assertThat(EstadoRespuestaWhatsapp.values()).extracting(Enum::name)
                .containsExactly("ENVIANDO", "ENVIADO", "ENTREGADO", "LEIDO", "FALLIDO", "SIN_CONFIRMAR");
    }
}
