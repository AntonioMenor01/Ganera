package com.ganera.core.tramite;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.tramite.CrotalNormalizador.TipoCrotal;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Revision de un Tramite por la gestoria (Prompt A1, Task 6): PATCH, aprobar y rechazar.
 *
 * Cada metodo publico es UNA transaccion que:
 * - carga el Tramite con bloqueo de fila (PESSIMISTIC_WRITE) y gestoriaId explicito -- nunca
 *   findById a secas; si no existe o es de otra Gestoria -> RecursoNoEncontradoException (404);
 * - solo actua sobre un Tramite en PENDIENTE_REVISION (decision 12) -> si no, 409;
 * - PATCH y aprobar exigen la version que mostraba la pantalla (decision 27): distinta de la
 *   actual -> 409 MOTIVO_VERSION_DESFASADA sin cambios. Se comprueba DESPUES del estado (un
 *   Tramite que ya no esta pendiente da su motivo concreto, que es cierto con cualquier version);
 * - toda escritura aceptada incrementa la version exactamente en 1 (incrementarVersion), tambien
 *   cuando solo cambia tramite_crotal y en la re-resolucion confirmada de aprobar.
 *
 * Los errores (RecursoNoEncontradoException, CrotalInvalidoException, TramiteConflictoException)
 * SALEN de la transaccion, que se revierte entera, y los traduce el controlador (decision 26).
 * Unica excepcion: ResolucionCrotalesCambiadaException en aprobar confirma la re-resolucion.
 * Nunca se capturan dentro: una excepcion de TramiteCrotalService ya marca la transaccion como
 * rollback-only y un commit posterior fallaria con UnexpectedRollbackException.
 *
 * Nada de esto llama a OvzAutomationService: aprobar solo cambia el estado en BD (Prompt 3c).
 */
@Service
public class TramiteRevisionService {

    static final String MOTIVO_EDITAR_SOLO_PENDIENTE = "Solo se puede editar un trámite pendiente de revisión.";
    static final String MOTIVO_APROBAR_SOLO_PENDIENTE = "Solo se puede aprobar un trámite pendiente de revisión.";
    static final String MOTIVO_RECHAZAR_SOLO_PENDIENTE = "Solo se puede rechazar un trámite pendiente de revisión.";
    static final String MOTIVO_FALTA_EXPLOTACION = "Falta asignar la explotación.";
    static final String MOTIVO_FALTA_TIPO = "Falta el tipo de trámite.";
    static final String MOTIVO_VERSION_DESFASADA =
            "El trámite ha cambiado desde que lo abriste. Vuelve a cargarlo y revísalo antes de continuar.";

    private final TramiteRepository tramiteRepository;
    private final ExplotacionRepository explotacionRepository;
    private final TramiteCrotalService tramiteCrotalService;
    private final TramiteCrotalRepository tramiteCrotalRepository;
    private final MensajeCampoRepository mensajeCampoRepository;

    /** Proxy compartido: dentro de la transaccion es el EntityManager de la peticion (OSIV). */
    @PersistenceContext
    private EntityManager entityManager;

    public TramiteRevisionService(
            TramiteRepository tramiteRepository,
            ExplotacionRepository explotacionRepository,
            TramiteCrotalService tramiteCrotalService,
            TramiteCrotalRepository tramiteCrotalRepository,
            MensajeCampoRepository mensajeCampoRepository) {
        this.tramiteRepository = tramiteRepository;
        this.explotacionRepository = explotacionRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.tramiteCrotalRepository = tramiteCrotalRepository;
        this.mensajeCampoRepository = mensajeCampoRepository;
    }

    /**
     * PATCH /tramites/{id}. Un argumento null significa "no cambiar". La Explotacion se asigna
     * ANTES de tocar los crotales, para que se resuelvan contra la nueva. Si dos crotales acaban
     * en el mismo Animal -> 409 y no se guarda nada (decision 23).
     */
    @Transactional
    public TramiteDetalleResponse actualizar(Long gestoriaId, Long tramiteId, Long versionVista,
                                             Long explotacionId, TipoTramite tipoTramite, List<String> crotales) {
        Tramite tramite = cargarConBloqueo(gestoriaId, tramiteId);
        exigirPendienteRevision(tramite, MOTIVO_EDITAR_SOLO_PENDIENTE);
        exigirVersion(tramite, versionVista);
        long versionLeida = tramite.getVersion();

        boolean explotacionCambiada = false;
        if (explotacionId != null) {
            Explotacion explotacion = explotacionRepository.findByIdAndGestoriaId(explotacionId, gestoriaId)
                    .orElseThrow(RecursoNoEncontradoException::new);
            Long anterior = tramite.getExplotacion() != null ? tramite.getExplotacion().getId() : null;
            explotacionCambiada = !Objects.equals(anterior, explotacion.getId());
            tramite.setExplotacion(explotacion);
        }
        if (tipoTramite != null) {
            tramite.setTipoTramite(tipoTramite);
        }
        if (crotales != null) {
            tramiteCrotalService.reemplazarCrotales(tramite, crotales, gestoriaId);
        } else if (explotacionCambiada) {
            tramiteCrotalService.recalcularEnlaces(tramite, gestoriaId);
        }

        List<TramiteCrotal> filas = filasDe(tramite, gestoriaId);
        List<String> duplicados = motivosMismoAnimal(filas);
        if (!duplicados.isEmpty()) {
            throw new TramiteConflictoException(String.join(" ", duplicados));
        }
        tramiteRepository.save(tramite);
        incrementarVersion(tramite, versionLeida);

        String mensajeOriginal = mensajeCampoRepository.findFirstByTramiteIdOrderByCreatedAtDesc(tramite.getId())
                .map(MensajeCampo::getCuerpo)
                .orElse(null);
        return TramiteDetalleResponse.from(tramite, mensajeOriginal, respuestas(filas));
    }

    /**
     * POST /tramites/{id}/aprobar, DESPUES del 403 de suscripcion (lo comprueba el controlador).
     *
     * Vuelve a resolver los crotales contra el inventario actual (una resolucion guardada puede
     * estar obsoleta). Si la re-resolucion CAMBIA algun crotal (otro animal, ambiguo, ya no
     * esta...) -> ResolucionCrotalesCambiadaException (409): nunca se aprueba algo distinto de lo
     * que el revisor vio. Esa excepcion NO revierte la transaccion (noRollbackFor): la nueva
     * resolucion se guarda y el detalle la muestra; el estado no cambia (solo se fija al final).
     *
     * Si nada cambio, se exigen explotacion, tipo y crotales aprobables (decision 25); si algo
     * falla, 409 con TODOS los motivos y rollback completo.
     */
    @Transactional(noRollbackFor = ResolucionCrotalesCambiadaException.class)
    public TramiteResponse aprobar(Long gestoriaId, Long tramiteId, Long versionVista) {
        Tramite tramite = cargarConBloqueo(gestoriaId, tramiteId);
        exigirPendienteRevision(tramite, MOTIVO_APROBAR_SOLO_PENDIENTE);
        exigirVersion(tramite, versionVista);
        long versionLeida = tramite.getVersion();

        Map<Long, ResolucionVista> vistas = new LinkedHashMap<>();
        for (TramiteCrotal fila : filasDe(tramite, gestoriaId)) {
            vistas.put(fila.getId(), ResolucionVista.de(fila));
        }
        tramiteCrotalService.recalcularEnlaces(tramite, gestoriaId);
        List<TramiteCrotal> filas = filasDe(tramite, gestoriaId);

        List<String> cambios = new ArrayList<>();
        for (TramiteCrotal fila : filas) {
            ResolucionVista antes = vistas.get(fila.getId());
            ResolucionVista ahora = ResolucionVista.de(fila);
            if (!ahora.equals(antes)) {
                cambios.add(fila.getCrotalIndicado() + " antes " + (antes != null ? antes.describir() : "sin resolver")
                        + ", ahora " + ahora.describir());
            }
        }
        if (!cambios.isEmpty()) {
            // Se fuerza el flush para que un fallo de escritura salga aqui (y se traduzca) y no
            // en el commit; la transaccion se confirma igualmente al salir (noRollbackFor).
            // La re-resolucion solo toca tramite_crotal: el incremento de version se FUERZA
            // (decision 27), asi la pantalla que vio la resolucion anterior ya no puede aprobar.
            tramiteCrotalRepository.flush();
            incrementarVersion(tramite, versionLeida);
            throw new ResolucionCrotalesCambiadaException(
                    "La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                            + String.join("; ", cambios) + ". Revisa el trámite antes de aprobarlo.");
        }

        List<String> motivos = new ArrayList<>();
        if (tramite.getExplotacion() == null) {
            motivos.add(MOTIVO_FALTA_EXPLOTACION);
        }
        if (tramite.getTipoTramite() == null) {
            motivos.add(MOTIVO_FALTA_TIPO);
        }
        for (TramiteCrotal fila : filas) {
            motivoCrotalNoAprobable(fila).ifPresent(motivos::add);
        }
        motivos.addAll(motivosMismoAnimal(filas));
        if (!motivos.isEmpty()) {
            throw new TramiteConflictoException(String.join(" ", motivos));
        }

        tramite.setEstado(EstadoTramite.APROBADO);
        tramiteRepository.save(tramite);
        incrementarVersion(tramite, versionLeida);
        return TramiteResponse.from(tramite, respuestas(filas));
    }

    /** POST /tramites/{id}/rechazar: solo desde PENDIENTE_REVISION; sin requisitos de datos. No
     * exige version (decision 27) pero, como toda escritura, la incrementa. */
    @Transactional
    public TramiteResponse rechazar(Long gestoriaId, Long tramiteId) {
        Tramite tramite = cargarConBloqueo(gestoriaId, tramiteId);
        exigirPendienteRevision(tramite, MOTIVO_RECHAZAR_SOLO_PENDIENTE);
        long versionLeida = tramite.getVersion();
        tramite.setEstado(EstadoTramite.RECHAZADO);
        tramiteRepository.save(tramite);
        incrementarVersion(tramite, versionLeida);
        return TramiteResponse.from(tramite, respuestas(filasDe(tramite, gestoriaId)));
    }

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) y despues REFRESCA la entidad (revision M3): si algo
     * ya hubiera cargado este Tramite en el mismo EntityManager (OSIV) antes de la llamada,
     * Hibernate devolveria esa instancia cacheada, posiblemente obsoleta, aunque la query bloquee.
     * El refresh es un SELECT normal por clave primaria; la fila ya esta bloqueada por nosotros,
     * asi que lee el ultimo estado confirmado. Un solo FOR UPDATE por endpoint.
     */
    private Tramite cargarConBloqueo(Long gestoriaId, Long tramiteId) {
        if (gestoriaId == null || tramiteId == null) {
            throw new RecursoNoEncontradoException();
        }
        Tramite tramite = tramiteRepository.findConBloqueoByIdAndGestoriaId(tramiteId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        entityManager.refresh(tramite);
        return tramite;
    }

    /** Lo que el revisor vio de un crotal: con esto se detecta si la re-resolucion lo cambio. */
    private record ResolucionVista(String crotal, Long animalId, ResolucionCrotal resolucion) {

        static ResolucionVista de(TramiteCrotal fila) {
            return new ResolucionVista(fila.getCrotal(),
                    fila.getAnimal() != null ? fila.getAnimal().getId() : null, fila.getResolucion());
        }

        String describir() {
            return switch (resolucion) {
                case EN_INVENTARIO -> crotal;
                case AMBIGUO -> "ambiguo";
                case NO_ENCONTRADO -> "no está en el inventario";
                case SIN_EXPLOTACION -> "sin explotación";
            };
        }
    }

    /**
     * Decision 27: la version que mostraba la pantalla debe ser la actual, leida bajo el bloqueo de
     * fila y tras el refresh (cargarConBloqueo). null (el controlador ya responde 400 antes) se
     * trata igual que una version distinta: nunca se escribe sin saber que se vio.
     */
    private static void exigirVersion(Tramite tramite, Long versionVista) {
        if (versionVista == null || !versionVista.equals(tramite.getVersion())) {
            throw new TramiteConflictoException(MOTIVO_VERSION_DESFASADA);
        }
    }

    /**
     * Toda escritura aceptada deja la version exactamente en versionLeida + 1. Primero se vuelca
     * lo pendiente: si el propio Tramite estaba modificado (estado, explotacion, tipo), Hibernate
     * ya incrementa la version en ese UPDATE. Si no (solo cambio tramite_crotal, o un PATCH sin
     * cambios), se fuerza con un UPDATE explicito de la version (id + gestoriaId + version leida;
     * la fila ya esta bloqueada por nosotros, asi que siempre actualiza exactamente 1) y se
     * refresca la entidad para que la respuesta lleve la version nueva.
     * No se usa entityManager.lock(PESSIMISTIC_FORCE_INCREMENT): Hibernate lo omite en silencio si
     * la entrada del contexto de persistencia ya tiene un bloqueo de nivel igual (comprobado en
     * @DataJpaTest: la version no cambiaba). Todo dentro de la transaccion: se revierte con ella
     * en cualquier 409 salvo el de noRollbackFor.
     */
    private void incrementarVersion(Tramite tramite, long versionLeida) {
        entityManager.flush();
        if (tramite.getVersion() != versionLeida) {
            return;
        }
        int filas = entityManager.createQuery("update Tramite t set t.version = t.version + 1 "
                        + "where t.id = :id and t.gestoria.id = :gestoriaId and t.version = :version")
                .setParameter("id", tramite.getId())
                .setParameter("gestoriaId", tramite.getGestoria().getId())
                .setParameter("version", versionLeida)
                .executeUpdate();
        if (filas != 1) {
            // No deberia ocurrir con la fila bloqueada; si ocurre, conflicto -> 409 y rollback.
            throw new ObjectOptimisticLockingFailureException(Tramite.class, tramite.getId());
        }
        entityManager.refresh(tramite);
    }

    private static void exigirPendienteRevision(Tramite tramite, String motivo) {
        if (tramite.getEstado() != EstadoTramite.PENDIENTE_REVISION) {
            throw new TramiteConflictoException(motivo);
        }
    }

    private List<TramiteCrotal> filasDe(Tramite tramite, Long gestoriaId) {
        return tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaId);
    }

    private static List<TramiteCrotalResponse> respuestas(List<TramiteCrotal> filas) {
        return filas.stream().map(TramiteCrotalResponse::from).toList();
    }

    /**
     * Decision 28 (regla PROVISIONAL, pendiente de confirmar longitudes de otros paises): formato
     * de crotal completo para aprobar un NO_ENCONTRADO. "ES" + exactamente 12 digitos; cualquier
     * otro codigo de pais (2 letras) + 8 a 12 digitos. Solo afecta a la aprobacion: la
     * clasificacion COMPLETO/INCOMPLETO de la resolucion (decision 20) no cambia.
     */
    private static final Pattern CROTAL_COMPLETO_ESPANOL = Pattern.compile("ES[0-9]{12}");
    private static final Pattern CROTAL_COMPLETO_OTRO_PAIS = Pattern.compile("[A-Z]{2}[0-9]{8,12}");

    static boolean tieneFormatoCompletoAprobable(String crotalNormalizado) {
        if (crotalNormalizado.startsWith("ES")) {
            return CROTAL_COMPLETO_ESPANOL.matcher(crotalNormalizado).matches();
        }
        return CROTAL_COMPLETO_OTRO_PAIS.matcher(crotalNormalizado).matches();
    }

    /**
     * Decision 25: AMBIGUO y SIN_EXPLOTACION nunca se aprueban; NO_ENCONTRADO solo si el crotal
     * indicado es completo (entrada de un animal que aun no esta en inventario) -- OVZ necesita
     * el crotal entero -- y, desde la decision 28, con un formato completo plausible.
     * EN_INVENTARIO: desde la revision 7a I1 (opcion a) tambien exige el formato de la decision 28,
     * sobre el crotal RESUELTO (el del Animal): un inventario importado sin "ES" resolveria bien
     * los ultimos digitos pero enviaria a OVZ un crotal incompleto. Misma comprobacion
     * (tieneFormatoCompletoAprobable) para los dos casos; la resolucion no cambia.
     */
    private static Optional<String> motivoCrotalNoAprobable(TramiteCrotal fila) {
        String crotal = fila.getCrotalIndicado();
        return switch (fila.getResolucion()) {
            case AMBIGUO -> Optional.of("El crotal " + crotal + " es ambiguo (varios animales coinciden).");
            case SIN_EXPLOTACION -> Optional.of(
                    "El crotal " + crotal + " no se puede comprobar porque el trámite no tiene explotación.");
            case NO_ENCONTRADO -> motivoNoEncontrado(crotal);
            case EN_INVENTARIO -> tieneFormatoCompletoAprobable(fila.getCrotal())
                    ? Optional.empty()
                    : Optional.of("El crotal " + crotal + " está en el inventario como " + fila.getCrotal()
                            + ", que no es un crotal completo válido."
                            + " Vuelve a importar el inventario con el crotal completo.");
        };
    }

    private static Optional<String> motivoNoEncontrado(String crotalIndicado) {
        CrotalNormalizador.CrotalNormalizado normalizado = CrotalNormalizador.normalizar(crotalIndicado);
        if (normalizado.tipo() == TipoCrotal.INCOMPLETO) {
            return Optional.of("El crotal " + crotalIndicado + " no está en el inventario y está incompleto.");
        }
        if (!tieneFormatoCompletoAprobable(normalizado.valor())) {
            return Optional.of("El crotal " + crotalIndicado
                    + " no está en el inventario y no tiene un formato completo válido.");
        }
        return Optional.empty();
    }

    /** Decision 23: un motivo por cada Animal al que se resuelven dos o mas crotales indicados. */
    private static List<String> motivosMismoAnimal(List<TramiteCrotal> filas) {
        Map<Long, List<TramiteCrotal>> porAnimal = new LinkedHashMap<>();
        for (TramiteCrotal fila : filas) {
            if (fila.getAnimal() != null) {
                porAnimal.computeIfAbsent(fila.getAnimal().getId(), id -> new ArrayList<>()).add(fila);
            }
        }
        List<String> motivos = new ArrayList<>();
        for (List<TramiteCrotal> grupo : porAnimal.values()) {
            if (grupo.size() > 1) {
                List<String> indicados = grupo.stream().map(TramiteCrotal::getCrotalIndicado).toList();
                motivos.add("Los crotales " + enumerar(indicados)
                        + " corresponden al mismo animal (" + grupo.get(0).getCrotal() + ").");
            }
        }
        return motivos;
    }

    /** "a y b", "a, b y c". */
    private static String enumerar(List<String> valores) {
        if (valores.size() == 1) {
            return valores.get(0);
        }
        return String.join(", ", valores.subList(0, valores.size() - 1)) + " y " + valores.get(valores.size() - 1);
    }
}
