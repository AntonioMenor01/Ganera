package com.ganera.core.tramite;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula alertas de plazos legales (manual OVZ §5) y de cola atascada. Sin tabla propia: se
 * derivan de los trámites de la gestoría. gestoriaId siempre del JWT.
 */
@Service
public class AlertaService {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final int DIAS_REVISION_ATASCADA = 2;
    static final int DIAS_APROBADO_SIN_OVZ = 3;
    static final int DIAS_BAJA = 7;
    static final int DIAS_CONFIRMACION = 7;
    static final int DIAS_AVISO_13 = 10;
    /** Tope de trámites escaneados por petición (piloto). */
    static final int MAX_TRAMITES = 500;

    private final TramiteRepository tramiteRepository;
    private final Clock clock;

    public AlertaService(TramiteRepository tramiteRepository, Clock clock) {
        this.tramiteRepository = tramiteRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> listar(Long gestoriaId) {
        LocalDate hoy = LocalDate.now(clock.withZone(MADRID));
        List<Tramite> tramites = tramiteRepository
                .findByGestoriaId(gestoriaId, Pageable.ofSize(MAX_TRAMITES))
                .getContent();
        List<AlertaResponse> alertas = new ArrayList<>();
        for (Tramite t : tramites) {
            añadirCola(alertas, t, hoy);
            añadirPlazosLegales(alertas, t, hoy);
        }
        alertas.sort(Comparator
                .comparing(AlertaResponse::venceEl, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AlertaResponse::id));
        return List.copyOf(alertas);
    }

    private void añadirCola(List<AlertaResponse> alertas, Tramite t, LocalDate hoy) {
        if (t.getCreatedAt() == null) {
            return;
        }
        LocalDate creado = t.getCreatedAt().atZone(MADRID).toLocalDate();
        if (t.getEstado() == EstadoTramite.PENDIENTE_REVISION
                && !creado.plusDays(DIAS_REVISION_ATASCADA).isAfter(hoy)) {
            alertas.add(new AlertaResponse(
                    "revision-" + t.getId(),
                    "REVISION_ATASCADA",
                    "Trámite sin revisar",
                    "Lleva " + DIAS_REVISION_ATASCADA + " días o más en pendiente de revisión.",
                    creado.plusDays(DIAS_REVISION_ATASCADA),
                    t.getId(),
                    "warning"));
        }
        if (t.getEstado() == EstadoTramite.APROBADO
                && !creado.plusDays(DIAS_APROBADO_SIN_OVZ).isAfter(hoy)) {
            alertas.add(new AlertaResponse(
                    "aprobado-" + t.getId(),
                    "APROBADO_SIN_OVZ",
                    "Aprobado · falta OVZ",
                    "Aprobado hace " + DIAS_APROBADO_SIN_OVZ
                            + " días o más y aún no está marcado como hecho en OVZ.",
                    creado.plusDays(DIAS_APROBADO_SIN_OVZ),
                    t.getId(),
                    "warning"));
        }
    }

    private void añadirPlazosLegales(List<AlertaResponse> alertas, Tramite t, LocalDate hoy) {
        if (t.getFechaHecho() == null || t.getTipoTramite() == null) {
            return;
        }
        if (t.getEstado() == EstadoTramite.HECHO_EN_OVZ || t.getEstado() == EstadoTramite.RECHAZADO) {
            return;
        }
        LocalDate hecho = t.getFechaHecho();
        if (t.getTipoTramite() == TipoTramite.BAJA_MUERTE) {
            LocalDate vence = hecho.plusDays(DIAS_BAJA);
            if (!vence.isBefore(hoy.minusDays(1)) && !vence.isAfter(hoy.plusDays(3))) {
                alertas.add(new AlertaResponse(
                        "baja-" + t.getId(),
                        "BAJA_PLAZO_7D",
                        "Plazo de baja por muerte",
                        "OVZ: 7 días desde la muerte. Vence el " + vence + ".",
                        vence,
                        t.getId(),
                        severidad(vence, hoy)));
            }
        }
        if (t.getTipoTramite() == TipoTramite.CONFIRMACION_MOVIMIENTO) {
            LocalDate vence = hecho.plusDays(DIAS_CONFIRMACION);
            if (!vence.isBefore(hoy.minusDays(1)) && !vence.isAfter(hoy.plusDays(3))) {
                alertas.add(new AlertaResponse(
                        "conf-" + t.getId(),
                        "CONFIRMACION_PLAZO_7D",
                        "Plazo de confirmación de entrada",
                        "OVZ: 7 días desde la entrada. Vence el " + vence + ".",
                        vence,
                        t.getId(),
                        severidad(vence, hoy)));
            }
            LocalDate aviso13 = hecho.plusDays(DIAS_AVISO_13);
            if (!aviso13.isAfter(hoy)) {
                alertas.add(new AlertaResponse(
                        "bloqueo13-" + t.getId(),
                        "ENTRADAS_13D_BLOQUEO",
                        "Entrada sin confirmar (aviso 13 días)",
                        "Pasados 13 días sin confirmar, OVZ puede bloquear nuevas guías.",
                        hecho.plusDays(13),
                        t.getId(),
                        "critical"));
            }
        }
    }

    private static String severidad(LocalDate vence, LocalDate hoy) {
        if (vence.isBefore(hoy) || vence.isEqual(hoy)) {
            return "critical";
        }
        return "warning";
    }
}
