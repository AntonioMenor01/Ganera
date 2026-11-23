package com.ganera.core.tramite;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ovz.DatosPresentacionAlta;
import com.ganera.core.ovz.DatosPresentacionBaja;
import com.ganera.core.ovz.DocumentoOvzInvalidoException;
import com.ganera.core.ovz.DocumentoOvzService;
import com.ganera.core.ovz.Justificante;
import com.ganera.core.ovz.ListasOvz;
import com.ganera.core.ovz.PreferenciaOvz;
import com.ganera.core.ovz.PreferenciaOvzRepository;
import com.ganera.core.ovz.PresentacionAutomaticaProperties;
import com.ganera.core.ovz.TipoDocumentoOvz;
import com.ganera.core.ovz.ValidadorPdf;
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
    private final TramiteCrotalRepository tramiteCrotalRepository;
    private final DocumentoOvzService documentoOvzService;
    private final PreferenciaOvzRepository preferenciaOvzRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;
    /**
     * Interruptor de la presentacion automatica (T3 maestro + T6 por gestoria). Se encola al aprobar SOLO si el
     * interruptor maestro esta activo Y la gestoria esta en la lista ({@code habilitadaParaGestoria}); por defecto
     * esta apagado y la lista vacia, asi que no se encola nada (via manual, HECHO_EN_OVZ), aunque el ganadero tenga
     * credenciales. Solo afecta al encolado al aprobar ({@link #encolarSiProcede}); el reencolado manual de un
     * trabajo ya existente ({@link #reencolar}) es una accion explicita del gestor y no depende de este interruptor.
     */
    private final PresentacionAutomaticaProperties presentacionAutomatica;

    @PersistenceContext
    private EntityManager entityManager;

    public TrabajoOvzService(TrabajoOvzRepository trabajoOvzRepository,
                             TramiteRepository tramiteRepository,
                             TramiteCrotalService tramiteCrotalService,
                             TramiteCrotalRepository tramiteCrotalRepository,
                             DocumentoOvzService documentoOvzService,
                             PreferenciaOvzRepository preferenciaOvzRepository,
                             AuditoriaService auditoriaService,
                             Clock clock,
                             PresentacionAutomaticaProperties presentacionAutomatica) {
        this.trabajoOvzRepository = trabajoOvzRepository;
        this.tramiteRepository = tramiteRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.tramiteCrotalRepository = tramiteCrotalRepository;
        this.documentoOvzService = documentoOvzService;
        this.preferenciaOvzRepository = preferenciaOvzRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
        this.presentacionAutomatica = presentacionAutomatica;
    }

    /**
     * Encola un trabajo al aprobar un {@code BAJA_MUERTE}, en la transacción de la aprobación ({@code MANDATORY}: si
     * no hay transacción, falla en vez de perderse). Solo si el ganadero tiene credenciales OVZ y no usa certificado
     * (D4/D8); si no, no hace nada. Idempotente: con el trabajo ya creado (UNIQUE(tramite_id)) no inserta otro.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void encolarSiProcede(Tramite tramite, Long gestoriaId, Long usuarioId) {
        if (!presentacionAutomatica.habilitadaParaGestoria(gestoriaId)) {
            // Interruptor apagado o gestoria no habilitada (T3 maestro + T6 por gestoria): nada se encola al
            // aprobar; el tramite se tramita por la via manual.
            return;
        }
        TipoTrabajoOvz tipoTrabajo = tipoTrabajoAutopresentable(tramite);
        if (tipoTrabajo == null) {
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
        trabajo.setGanadero(tramite.getExplotacion().getGanadero());
        trabajo.setTipo(tipoTrabajo);
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setCreatedAt(ahora);
        trabajo.setActualizadoEn(ahora);
        trabajoOvzRepository.save(trabajo);
        auditoriaService.registrar(AccionAuditoria.OVZ_TRABAJO_ENCOLADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.TRABAJO_OVZ, trabajo.getId(), null,
                DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.PENDIENTE)
                        .y(Clave.TIPO_TRAMITE, tramite.getTipoTramite()));
    }

    /**
     * El {@link TipoTrabajoOvz} con el que se autopresentaría este trámite, o {@code null} si no procede (vía
     * manual). Exige explotación y credenciales usuario+contraseña sin certificado (D4/D8). Por tipo:
     * <ul>
     *   <li>{@code BAJA_MUERTE}: siempre (plan 2026-11-17).</li>
     *   <li>{@code ALTA_NACIMIENTO}: solo con raza válida y distinta de LIDIA (D6; la raza es obligatoria al
     *       aprobar, T1). El resto de obligatorios se exigen al preparar los datos ({@link #datosPresentacionAlta}).
     *       Trashumante iría por vía manual (D6), pero aún no se modela.</li>
     * </ul>
     * No toca la cola; lo usan {@link #encolarSiProcede} y {@link #sePresentaraAutomaticamente}.
     */
    private TipoTrabajoOvz tipoTrabajoAutopresentable(Tramite tramite) {
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion == null || !tieneCredencialesParaAutomatico(explotacion.getGanadero())) {
            return null;
        }
        TipoTramite tipo = tramite.getTipoTramite();
        if (tipo == TipoTramite.BAJA_MUERTE) {
            return TipoTrabajoOvz.BAJA_MUERTE;
        }
        if (tipo == TipoTramite.ALTA_NACIMIENTO && altaAutopresentable(tramite)) {
            return TipoTrabajoOvz.ALTA_NACIMIENTO;
        }
        return null;
    }

    /** El alta se autopresenta solo con raza válida y distinta de LIDIA (D6). */
    private static boolean altaAutopresentable(Tramite tramite) {
        String raza = tramite.getRaza();
        return ListasOvz.esRazaValida(raza) && !ListasOvz.CODIGO_RAZA_LIDIA.equals(raza);
    }

    /** Automático solo con credenciales usuario+contraseña y sin certificado (D4/D8). */
    static boolean tieneCredencialesParaAutomatico(Ganadero ganadero) {
        return ganadero != null
                && !ganadero.isUsaCertificado()
                && ganadero.getOvzUsuario() != null
                && ganadero.getOvzPasswordCifrada() != null;
    }

    /**
     * Si al aprobar este trámite se encolaría una presentación automática (T5): misma condición que
     * {@link #encolarSiProcede} (interruptor habilitado para la gestoría y {@link #tipoTrabajoAutopresentable} no
     * nulo: BAJA_MUERTE, o ALTA_NACIMIENTO con raza válida no LIDIA, con explotación y credenciales
     * usuario+contraseña sin certificado, D4/D8/D6), pero SIN tocar la cola. Lo usa el detalle para el botón
     * «Aprobar y presentar en OVZ» y para saber que la vía será la automática. Lee el ganadero por la explotación
     * (dentro de la petición, OSIV); no abre transacción.
     */
    public boolean sePresentaraAutomaticamente(Tramite tramite) {
        Long gestoriaId = tramite.getGestoria() == null ? null : tramite.getGestoria().getId();
        return presentacionAutomatica.habilitadaParaGestoria(gestoriaId)
                && tipoTrabajoAutopresentable(tramite) != null;
    }

    /**
     * El estado del trabajo de presentación de este trámite para el detalle (T5), o vacío si no tiene (vía manual u
     * otro tipo). Solo lectura, con el {@code gestoriaId} explícito; un trabajo de otra gestoría nunca se ve.
     * {@link PresentacionOvzResponse} solo lee campos escalares del trabajo (no navega a entidades LAZY).
     */
    public Optional<PresentacionOvzResponse> presentacionDe(Long tramiteId, Long gestoriaId) {
        return trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaId)
                .map(PresentacionOvzResponse::from);
    }

    /**
     * «Reclamar trabajo» del worker (T4), D3: toma UN trabajo PENDIENTE a nivel global de forma atómica (bloqueo de
     * fila {@code FOR UPDATE SKIP LOCKED} en Postgres; en H2 degrada a {@code FOR UPDATE}, suficiente porque el
     * worker es único y serializado) y pasa el trabajo y su trámite a EN_PROCESO. Vacío si no hay ninguno.
     *
     * <p>Cross-tenant por diseño (como los planificadores): no hay petición ni {@code gestoriaFilter}. No deja fila
     * de auditoría: los eventos auditados de la presentación (presentada/error) los deja el worker al terminar (T4);
     * esta reclama es un paso interno (salvo el re-check de APROBADO, que sí audita OVZ_BAJA_ERROR). No se usa desde
     * ningún endpoint autenticado.
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
        if (trabajo.getTramite().getEstado() != EstadoTramite.APROBADO) {
            // Re-check (T4): entre encolar y reclamar el trámite pudo dejar de estar aprobado (rechazado, marcado a
            // mano como hecho en OVZ...). NUNCA se presenta algo que ya no está aprobado: el trabajo queda en ERROR
            // SEGURO y el trámite NO se toca.
            trabajo.setEstado(EstadoTrabajoOvz.ERROR);
            trabajo.setClaseError(TipoErrorOvz.SEGURO);
            trabajo.setMotivoError(MOTIVO_NO_APROBADO);
            trabajo.setActualizadoEn(ahora);
            auditoriaService.registrar(AccionAuditoria.OVZ_BAJA_ERROR, trabajo.getGestoria().getId(), Actor.sistema(),
                    EntidadAuditada.TRABAJO_OVZ, trabajo.getId(),
                    DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.PENDIENTE),
                    DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.ERROR));
            return Optional.empty();
        }
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

    // ------------------------------------------------------------------ worker de OVZ (T4)

    static final String MOTIVO_NO_APROBADO =
            "El trámite ya no estaba aprobado al ir a presentarlo en OVZ.";
    static final int MAX_MOTIVO = 500;

    private static final java.time.format.DateTimeFormatter FORMATO_FECHA_OVZ =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Datos para presentar la baja, cargados en una transacción corta y con el {@code gestoriaId} explícito (T4).
     * Exige exactamente UN crotal completo, REGA y fecha de muerte; si falta algo lanza
     * {@link DatosPresentacionInvalidosException} (el worker la trata como error SEGURO, sin abrir OVZ).
     */
    @Transactional(readOnly = true)
    public DatosPresentacionBaja datosPresentacion(Long trabajoId, Long gestoriaId) {
        TrabajoOvz trabajo = trabajoOvzRepository.findByIdAndGestoriaId(trabajoId, gestoriaId)
                .orElseThrow(() -> new DatosPresentacionInvalidosException("No se encontró el trabajo de OVZ."));
        Tramite tramite = trabajo.getTramite();
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion == null || explotacion.getCodigoRega() == null || explotacion.getCodigoRega().isBlank()) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene explotación (REGA) para presentarlo en OVZ.");
        }
        if (tramite.getFechaHecho() == null) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene fecha de muerte para presentarlo en OVZ.");
        }
        List<TramiteCrotal> crotales = tramiteCrotalRepository
                .findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaId);
        if (crotales.size() != 1) {
            throw new DatosPresentacionInvalidosException(
                    "La presentación automática de la baja exige exactamente un crotal en el trámite.");
        }
        String crotal = crotales.get(0).getCrotal();
        if (crotal == null || crotal.isBlank()) {
            throw new DatosPresentacionInvalidosException("El crotal del trámite no está completo.");
        }
        Ganadero ganadero = trabajo.getGanadero();
        return new DatosPresentacionBaja(tramite.getId(), ganadero.getId(), crotal,
                explotacion.getCodigoRega(), tramite.getFechaHecho().format(FORMATO_FECHA_OVZ),
                tramite.getNumeroMer(), ganadero.getOvzUsuario(), ganadero.isUsaCertificado());
    }

    /**
     * Datos para presentar el alta por nacimiento, en una transacción corta con el {@code gestoriaId} explícito
     * (plan 2026-11-23, T2). Obligatorios (si faltan → {@link DatosPresentacionInvalidosException}, que el worker
     * trata como error SEGURO sin abrir OVZ, D2/D5): REGA, exactamente UN crotal completo (el ternero), crotal de
     * la madre completo, fecha de nacimiento, sexo reconocible, raza válida y fecha de identificación. El tipo de
     * identificador es el único del catálogo (Crotal Electrónico, 04). La emisión del DI sale de las preferencias
     * (texto o código); si no está, por defecto imprimir desde «Animales sin DI» (sin tasas); la oficina solo
     * cuenta si la emisión es en la OVZ (D5). La raza LIDIA no llega aquí: va por vía manual (D6, filtrada al encolar).
     */
    @Transactional(readOnly = true)
    public DatosPresentacionAlta datosPresentacionAlta(Long trabajoId, Long gestoriaId) {
        TrabajoOvz trabajo = trabajoOvzRepository.findByIdAndGestoriaId(trabajoId, gestoriaId)
                .orElseThrow(() -> new DatosPresentacionInvalidosException("No se encontró el trabajo de OVZ."));
        Tramite tramite = trabajo.getTramite();
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion == null || explotacion.getCodigoRega() == null || explotacion.getCodigoRega().isBlank()) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene explotación (REGA) para presentarlo en OVZ.");
        }
        List<TramiteCrotal> crotales = tramiteCrotalRepository
                .findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaId);
        if (crotales.size() != 1) {
            throw new DatosPresentacionInvalidosException(
                    "La presentación automática del alta exige exactamente un crotal (el ternero) en el trámite.");
        }
        String crotalTernero = crotales.get(0).getCrotal();
        if (crotalTernero == null || crotalTernero.isBlank()) {
            throw new DatosPresentacionInvalidosException("El crotal del ternero no está completo.");
        }
        String crotalMadre = tramite.getCrotalMadre();
        if (crotalMadre == null || crotalMadre.isBlank()) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene el crotal de la madre para el alta.");
        }
        if (tramite.getFechaNacimiento() == null) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene fecha de nacimiento para el alta.");
        }
        if (tramite.getFechaIdentificacion() == null) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene fecha de identificación para el alta.");
        }
        String sexo = ListasOvz.codigoSexo(tramite.getSexo());
        if (sexo == null) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene un sexo reconocible para el alta.");
        }
        String raza = tramite.getRaza();
        if (!ListasOvz.esRazaValida(raza)) {
            throw new DatosPresentacionInvalidosException("El trámite no tiene una raza válida para el alta.");
        }
        PreferenciaOvz preferencia = preferenciaOvzRepository.findByGestoriaId(gestoriaId).orElse(null);
        String emision = ListasOvz.codigoEmisionDi(preferencia == null ? null : preferencia.getEmisionDi());
        if (emision == null) {
            emision = ListasOvz.CODIGO_EMISION_DI_IMPRIME;
        }
        String oficina = ListasOvz.CODIGO_EMISION_DI_OVZ.equals(emision) && preferencia != null
                ? preferencia.getOficinaDi() : null;
        Ganadero ganadero = trabajo.getGanadero();
        return new DatosPresentacionAlta(tramite.getId(), ganadero.getId(), crotalTernero, crotalMadre,
                explotacion.getCodigoRega(), tramite.getFechaNacimiento().format(FORMATO_FECHA_OVZ), sexo, raza,
                ListasOvz.CODIGO_TIPO_IDENTIFICADOR_DEFECTO,
                tramite.getFechaIdentificacion().format(FORMATO_FECHA_OVZ), emision, oficina,
                ganadero.getOvzUsuario(), ganadero.isUsaCertificado());
    }

    /**
     * Fin del ENSAYO (T4): el worker llegó hasta antes de «Aceptar» sin presentar nada. El trabajo queda en
     * {@code ENSAYO_OK} (terminal) y el trámite vuelve a {@code APROBADO}.
     */
    @Transactional
    public void finalizarEnsayo(Long trabajoId, Long gestoriaId) {
        TrabajoOvz trabajo = cargarTrabajo(trabajoId, gestoriaId);
        Tramite tramite = cargarTramiteConBloqueo(trabajo, gestoriaId);
        trabajo.setEstado(EstadoTrabajoOvz.ENSAYO_OK);
        trabajo.setActualizadoEn(ahora());
        tramite.setEstado(EstadoTramite.APROBADO);
        auditar(AccionAuditoria.OVZ_BAJA_ENSAYO, gestoriaId, trabajo, EstadoTrabajoOvz.ENSAYO_OK);
    }

    /**
     * La baja SE PRESENTÓ (T4): trabajo {@code EJECUTADO}, trámite {@code EJECUTADO_OVZ}. Si hay justificante lo
     * guarda; si falta (o no es un PDF válido) el estado queda igual (el documento queda pendiente, D7): perder el
     * hecho «presentada» por un justificante defectuoso sería peor que no tener el PDF.
     */
    @Transactional
    public void finalizarEjecutado(Long trabajoId, Long gestoriaId, Optional<Justificante> justificante) {
        TrabajoOvz trabajo = cargarTrabajo(trabajoId, gestoriaId);
        Tramite tramite = cargarTramiteConBloqueo(trabajo, gestoriaId);
        trabajo.setEstado(EstadoTrabajoOvz.EJECUTADO);
        trabajo.setClaseError(null);
        trabajo.setMotivoError(null);
        trabajo.setIntentos(trabajo.getIntentos() + 1);
        trabajo.setActualizadoEn(ahora());
        tramite.setEstado(EstadoTramite.EJECUTADO_OVZ);
        tramite.setMotivoError(null);
        auditar(AccionAuditoria.OVZ_BAJA_PRESENTADA, gestoriaId, trabajo, EstadoTrabajoOvz.EJECUTADO);
        if (justificante.isPresent() && pdfValido(justificante.get().contenido())) {
            documentoOvzService.guardar(tramite, trabajo.getGanadero(), tramite.getExplotacion(),
                    TipoDocumentoOvz.BAJA_ANIMAL, "Baja Animal.pdf", justificante.get().contenido(), Actor.sistema());
        }
    }

    /**
     * Error de la presentación (T4): {@code SEGURO} (antes de «Aceptar») o {@code INCIERTO} (después, sin resolver en
     * Documentos). El motivo es el texto para el gestor: va al trabajo y al trámite, NUNCA a la auditoría.
     */
    @Transactional
    public void finalizarError(Long trabajoId, Long gestoriaId, TipoErrorOvz clase, String motivo) {
        TrabajoOvz trabajo = cargarTrabajo(trabajoId, gestoriaId);
        Tramite tramite = cargarTramiteConBloqueo(trabajo, gestoriaId);
        String texto = recortar(motivo);
        trabajo.setEstado(EstadoTrabajoOvz.ERROR);
        trabajo.setClaseError(clase);
        trabajo.setMotivoError(texto);
        trabajo.setIntentos(trabajo.getIntentos() + 1);
        trabajo.setActualizadoEn(ahora());
        tramite.setEstado(EstadoTramite.ERROR_OVZ);
        tramite.setMotivoError(texto);
        auditar(AccionAuditoria.OVZ_BAJA_ERROR, gestoriaId, trabajo, EstadoTrabajoOvz.ERROR);
    }

    private TrabajoOvz cargarTrabajo(Long trabajoId, Long gestoriaId) {
        return trabajoOvzRepository.findByIdAndGestoriaId(trabajoId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    /** Bloqueo de fila + refresh, como reencolar (footgun M3: el trámite podría estar cacheado en el EntityManager). */
    private Tramite cargarTramiteConBloqueo(TrabajoOvz trabajo, Long gestoriaId) {
        Tramite tramite = tramiteRepository.findConBloqueoByIdAndGestoriaId(trabajo.getTramite().getId(), gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        entityManager.refresh(tramite);
        return tramite;
    }

    private void auditar(AccionAuditoria accion, Long gestoriaId, TrabajoOvz trabajo, EstadoTrabajoOvz despues) {
        auditoriaService.registrar(accion, gestoriaId, Actor.sistema(), EntidadAuditada.TRABAJO_OVZ, trabajo.getId(),
                DatosAuditoria.con(Clave.ESTADO, EstadoTrabajoOvz.EN_PROCESO),
                DatosAuditoria.con(Clave.ESTADO, despues));
    }

    private static boolean pdfValido(byte[] contenido) {
        try {
            ValidadorPdf.exigirPdfValido(contenido);
            return true;
        } catch (DocumentoOvzInvalidoException e) {
            return false;
        }
    }

    private static String recortar(String motivo) {
        String m = motivo == null || motivo.isBlank() ? "Error al presentar la baja en OVZ." : motivo.trim();
        return m.length() > MAX_MOTIVO ? m.substring(0, MAX_MOTIVO) : m;
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
