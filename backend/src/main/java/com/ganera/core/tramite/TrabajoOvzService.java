package com.ganera.core.tramite;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Cola de presentación automática de la baja por muerte en OVZ (escalón 2, T2; plan 2026-11-17, §3/§4/D11).
 *
 * <ul>
 *   <li>{@link #encolarSiProcede}: al <b>aprobar</b> un {@code BAJA_MUERTE} (desde {@link TramiteRevisionService},
 *       en la MISMA transacción, {@code MANDATORY}) encola un trabajo PENDIENTE <b>solo si</b> el ganadero tiene
 *       credenciales OVZ y no usa certificado (D4/D8); si no, no cambia nada (vía manual, {@code HECHO_EN_OVZ}).</li>
 *   <li>{@link #reclamarSiguiente}: lo usará el worker (T4). Toma atómicamente UN trabajo PENDIENTE a nivel global
 *       (D3) con bloqueo de fila y pasa el trabajo y el trámite a EN_PROCESO.</li>
 *   <li>{@link #reencolar}: desde un {@code ERROR} (D11). SEGURO se reencola directamente; INCIERTO solo con
 *       confirmación explícita del gestor (auditada); {@code EJECUTADO} nunca. Reutiliza la misma fila (clave de
 *       idempotencia por trámite), así que no duplica la presentación.</li>
 * </ul>
 */
@Service
public class TrabajoOvzService {

    static final String MOTIVO_SIN_TRABAJO =
            "Este trámite no tiene una presentación automática en OVZ que reencolar.";
    static final String MOTIVO_YA_EJECUTADO =
            "La baja ya se presentó en OVZ; no se puede volver a encolar.";
    static final String MOTIVO_NO_ES_ERROR =
            "Solo se puede reencolar una presentación que haya terminado en error.";
    static final String MOTIVO_INCIERTO_REQUIERE_CONFIRMACION =
            "La presentación falló tras pulsar «Aceptar» y no se pudo confirmar en OVZ. Antes de reencolar, comprueba "
                    + "en OVZ que la baja NO está presentada y confírmalo de forma explícita (riesgo de duplicarla).";

    private final TrabajoOvzRepository trabajoOvzRepository;
    private final TramiteRepository tramiteRepository;
    private final TramiteCrotalService tramiteCrotalService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public TrabajoOvzService(TrabajoOvzRepository trabajoOvzRepository,
                             TramiteRepository tramiteRepository,
                             TramiteCrotalService tramiteCrotalService,
                             AuditoriaService auditoriaService,
                             Clock clock) {
        this.trabajoOvzRepository = trabajoOvzRepository;
        this.tramiteRepository = tramiteRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /**
     * Encola un trabajo al aprobar un {@code BAJA_MUERTE}, en la transacción de la aprobación ({@code MANDATORY}: si
     * no hay transacción, falla en vez de perderse). Solo si el ganadero tiene credenciales OVZ y no usa certificado
     * (D4/D8); si no, no hace nada. Idempotente: con el trabajo ya creado (UNIQUE(tramite_id)) no inserta otro.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void encolarSiProcede(Tramite tramite, Long gestoriaId, Long usuarioId) {
        if (tramite.getTipoTramite() != TipoTramite.BAJA_MUERTE) {
            return;
        }
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion == null) {
            return;
        }
        Ganadero ganadero = explotacion.getGanadero();
        if (!tieneCredencialesParaAutomatico(ganadero)) {
            return;
        }
        if (trabajoOvzRepository.existsByTramiteId(tramite.getId())) {
            // Defensa en profundidad: el UNIQUE(tramite_id) es la garantía real; esto evita el 500 del constraint
            // en el caso raro de una reaprobación que llegase aquí con el trabajo ya existente.
            return;
        }
        Instant ahora = ahora();
        TrabajoOvz trabajo = new TrabajoOvz();
        trabajo.setGestoria(tramite.getGestoria());
        trabajo.setTramite(tramite);
        trabajo.setGanadero(ganadero);
        trabajo.setTipo(TipoTrabajoOvz.BAJA_MUERTE);
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setCreatedAt(ahora);
        trabajo.setActualizadoEn(ahora);
        trabajoOvzRepository.save(trabajo);
        auditoriaService.registrar(AccionAuditoria.OVZ_TRABAJO_ENCOLADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.TRABAJO_OVZ, trabajo.getId(), null,
                DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.PENDIENTE)
                        .y(Clave.TIPO_TRAMITE, TipoTramite.BAJA_MUERTE));
    }

    /** Automático solo con credenciales usuario+contraseña y sin certificado (D4/D8). */
    static boolean tieneCredencialesParaAutomatico(Ganadero ganadero) {
        return ganadero != null
                && !ganadero.isUsaCertificado()
                && ganadero.getOvzUsuario() != null
                && ganadero.getOvzPasswordCifrada() != null;
    }

    /**
     * «Reclamar trabajo» del worker (T4), D3: toma UN trabajo PENDIENTE a nivel global de forma atómica (bloqueo de
     * fila {@code FOR UPDATE SKIP LOCKED} en Postgres; en H2 degrada a {@code FOR UPDATE}, suficiente porque el
     * worker es único y serializado) y pasa el trabajo y su trámite a EN_PROCESO. Vacío si no hay ninguno.
     *
     * <p>Cross-tenant por diseño (como los planificadores): no hay petición ni {@code gestoriaFilter}. No deja fila
     * de auditoría: los eventos auditados de la presentación (presentada/error) los deja el worker al terminar (T4);
     * esta reclama es un paso interno. No se usa desde ningún endpoint autenticado.
     */
    @Transactional
    public Optional<TrabajoOvz> reclamarSiguiente() {
        List<TrabajoOvz> candidatos = trabajoOvzRepository.siguientesParaReclamar(
                EstadoTrabajoOvz.PENDIENTE, PageRequest.of(0, 1));
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        TrabajoOvz trabajo = candidatos.get(0);
        Instant ahora = ahora();
        trabajo.setEstado(EstadoTrabajoOvz.EN_PROCESO);
        trabajo.setActualizadoEn(ahora);
        // El trámite del trabajo pasa a EN_PROCESO (entidad gestionada; se vuelca al commit).
        trabajo.getTramite().setEstado(EstadoTramite.EN_PROCESO);
        return Optional.of(trabajo);
    }

    /**
     * Reencola un trabajo en {@code ERROR} (D11). Orden: 404 (trámite ajeno/inexistente, lo audita el controlador) →
     * 409 sin trabajo → 409 ya ejecutado → 409 no está en error → 409 versión desfasada → 409 incierto sin
     * confirmación → el trabajo vuelve a PENDIENTE (misma fila; clave de idempotencia por trámite) y el trámite a
     * APROBADO, {@code version + 1}. Audita OVZ_TRABAJO_REENCOLADO (SEGURO) u OVZ_INCIERTO_CONFIRMADO (INCIERTO).
     */
    @Transactional
    public TramiteResponse reencolar(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista,
                                     boolean confirmadoNoPresentado) {
        if (gestoriaId == null || tramiteId == null) {
            throw new RecursoNoEncontradoException();
        }
        // Bloqueo de fila + refresh, como TramiteRevisionService: si el trámite ya estaba cargado en este
        // EntityManager, Hibernate devolvería la instancia cacheada aunque la query bloquee (footgun M3).
        Tramite tramite = tramiteRepository.findConBloqueoByIdAndGestoriaId(tramiteId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        entityManager.refresh(tramite);
        TrabajoOvz trabajo = trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaId)
                .orElseThrow(() -> new TramiteConflictoException(MOTIVO_SIN_TRABAJO));
        if (trabajo.getEstado() == EstadoTrabajoOvz.EJECUTADO) {
            throw new TramiteConflictoException(MOTIVO_YA_EJECUTADO);
        }
        if (trabajo.getEstado() != EstadoTrabajoOvz.ERROR) {
            throw new TramiteConflictoException(MOTIVO_NO_ES_ERROR);
        }
        exigirVersion(tramite, versionVista);
        boolean incierto = trabajo.getClaseError() == TipoErrorOvz.INCIERTO;
        if (incierto && !confirmadoNoPresentado) {
            throw new TramiteConflictoException(MOTIVO_INCIERTO_REQUIERE_CONFIRMACION);
        }

        Instant ahora = ahora();
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setClaseError(null);
        trabajo.setMotivoError(null);
        trabajo.setActualizadoEn(ahora);
        tramite.setEstado(EstadoTramite.APROBADO);
        tramiteRepository.save(tramite);
        entityManager.flush(); // vuelca el UPDATE del trámite (sube @Version) antes de leer la versión en la respuesta

        AccionAuditoria accion = incierto
                ? AccionAuditoria.OVZ_INCIERTO_CONFIRMADO
                : AccionAuditoria.OVZ_TRABAJO_REENCOLADO;
        auditoriaService.registrar(accion, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.TRABAJO_OVZ, trabajo.getId(),
                DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.ERROR),
                DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.PENDIENTE));

        List<TramiteCrotalResponse> crotales = tramiteCrotalService
                .crotalesPorTramite(List.of(tramite.getId()), gestoriaId)
                .getOrDefault(tramite.getId(), List.of());
        return TramiteResponse.from(tramite, crotales);
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static void exigirVersion(Tramite tramite, Long versionVista) {
        if (versionVista == null || !versionVista.equals(tramite.getVersion())) {
            throw new TramiteConflictoException(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        }
    }
}
