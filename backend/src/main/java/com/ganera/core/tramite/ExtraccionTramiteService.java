package com.ganera.core.tramite;

import com.ganera.core.tramite.ExtraccionValidador.ExtraccionValidada;
import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Extraccion por IA en segundo plano de los Tramites que llegan por WhatsApp (B1, T2; R2, R3 y D4
 * del plan). La cola es la propia tabla tramite: {@code estado_extraccion IN (PENDIENTE, FALLIDA)}
 * con {@code proximo_intento_extraccion <= ahora}.
 *
 * <p>Por cada Tramite:
 * <ol>
 *   <li>Comprobacion previa barata, sin bloqueo: si ya no procede (ver {@link #procede}), se deja
 *       de reintentar y no se llama a la IA. Si el mensaje no tiene texto -> SIN_TEXTO, sin IA.</li>
 *   <li>Llamada a la IA FUERA de cualquier transaccion y sin bloqueo de fila: nunca se tiene un
 *       bloqueo (ni una conexion del pool) mientras dura una llamada de red (nota M2 para el 3c).</li>
 *   <li>El resultado se aplica en una transaccion corta con {@code findConBloqueoByIdAndGestoriaId}
 *       + refresh (patron de TramiteRevisionService), comprobando OTRA VEZ bajo el bloqueo que
 *       procede: si alguien edito, aprobo o rechazo el Tramite mientras la IA respondia, el
 *       resultado se descarta. Nunca se pisa una correccion humana.</li>
 * </ol>
 *
 * <p>Version (decision 27 de A1): aplicar un resultado o el primer fallo son cambios visibles y
 * dejan la version exactamente en la leida + 1 (un solo UPDATE del Tramite, comprobado). La
 * contabilidad de reintentos sobre un Tramite ya en FALLIDA y el "dejar de reintentar" NO tocan la
 * version (UPDATE JPQL, ver TramiteRepository): asi no invalidan versionTrasFallo ni dan 409 falsos
 * a un empleado con el modal abierto.
 *
 * <p>Job cross-tenant sin peticion (como SuscripcionSyncScheduler): no hay gestoriaFilter. Solo la
 * consulta de la cola ve todas las Gestorias; cada operacion sobre un Tramite lleva su gestoriaId
 * explicito (Tramite, mensaje, crotales y Animales).
 *
 * <p>Limite conocido: no se "reclaman" filas. Hoy hay una sola instancia y {@code fixedDelay} no
 * solapa ejecuciones; con varias instancias dos podrian llamar a la IA para el mismo Tramite (el
 * resultado se aplicaria una sola vez, por la comprobacion bajo bloqueo, pero la llamada se pagaria
 * dos veces). Antes de escalar: reclamar con {@code SELECT ... FOR UPDATE SKIP LOCKED} o un lease.
 *
 * <p>Logs: ids, estadoExtraccion, intentos y el TIPO de la excepcion. Nunca el texto del mensaje,
 * el telefono ni el mensaje de la excepcion (puede llevar lo que contesto el modelo).
 */
@Slf4j
@Service
public class ExtraccionTramiteService {

    private static final List<EstadoExtraccion> ESTADOS_EN_COLA = List.of(EstadoExtraccion.PENDIENTE, EstadoExtraccion.FALLIDA);

    private final TramiteRepository tramiteRepository;
    private final TramiteCrotalService tramiteCrotalService;
    private final TramiteCrotalRepository tramiteCrotalRepository;
    private final MensajeCampoRepository mensajeCampoRepository;
    private final TramiteExtractionService extraccion;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;
    private final List<Duration> reintentos;
    private final int lote;

    /** Proxy compartido: dentro de cada TransactionTemplate es el EntityManager de esa transaccion. */
    @PersistenceContext
    private EntityManager entityManager;

    public ExtraccionTramiteService(TramiteRepository tramiteRepository,
                                    TramiteCrotalService tramiteCrotalService,
                                    TramiteCrotalRepository tramiteCrotalRepository,
                                    MensajeCampoRepository mensajeCampoRepository,
                                    TramiteExtractionService extraccion,
                                    Clock reloj,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${ganera.extraccion.reintentos:PT1M,PT5M,PT30M}") String reintentos,
                                    @Value("${ganera.extraccion.lote:10}") int lote) {
        this.tramiteRepository = tramiteRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.tramiteCrotalRepository = tramiteCrotalRepository;
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.extraccion = extraccion;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
        this.reintentos = parsearReintentos(reintentos);
        if (lote < 1) {
            throw new IllegalArgumentException("ganera.extraccion.lote debe ser al menos 1");
        }
        this.lote = lote;
    }

    /** Plazos tras el 1.er, 2.º, ... fallo ("PT1M,PT5M,PT30M"); tras el ultimo fallo, no hay mas. */
    static List<Duration> parsearReintentos(String valor) {
        List<Duration> plazos = new ArrayList<>();
        if (valor != null) {
            for (String parte : valor.split(",")) {
                if (!parte.isBlank()) {
                    Duration plazo = Duration.parse(parte.trim());
                    if (plazo.isNegative()) {
                        throw new IllegalArgumentException("ganera.extraccion.reintentos no admite plazos negativos");
                    }
                    plazos.add(plazo);
                }
            }
        }
        return List.copyOf(plazos);
    }

    /** Cola: Tramites con extraccion PENDIENTE o FALLIDA cuyo proximo intento es {@code <= ahora}.
     * Package-private: devuelve ids de todas las Gestorias y solo lo usan este servicio y sus tests. */
    List<TramitePendienteExtraccion> pendientes(Instant ahora, int limite) {
        return tramiteRepository.findPendientesDeExtraccion(ESTADOS_EN_COLA, ahora, PageRequest.of(0, limite));
    }

    /**
     * Una pasada del planificador: un lote de la cola, con el "ahora" del Clock. Un fallo con un
     * Tramite (fuera de la propia IA, que ya cuenta como fallo) se registra y no aborta el lote; se
     * intenta contar como un fallo de extraccion para que el Tramite salga de la cola con plazo (y a
     * revision si estaba PENDIENTE) en vez de bloquear la cabeza de la cola en cada pasada.
     *
     * <p>Coste aceptado (revision m3): en BD no se distingue un fallo de la IA de uno que no lo es.
     * Un error transitorio de BD (p. ej. en la lectura previa) consume uno de los intentos y muestra
     * el aviso de "no se ha podido extraer" aunque no se haya llamado a la IA. Es recuperable: el
     * siguiente reintento que acierte aplica el resultado y quita el aviso, y nunca se aprueba ni se
     * pisa nada. El log de error si lo distingue (tipo de la excepcion).
     *
     * @return cuantos Tramites se han tomado de la cola.
     */
    public int procesarPendientes() {
        List<TramitePendienteExtraccion> cola = pendientes(reloj.instant(), lote);
        for (TramitePendienteExtraccion pendiente : cola) {
            try {
                procesar(pendiente);
            } catch (RuntimeException e) {
                log.error("Error procesando la extraccion: tramiteId={}, gestoriaId={}, excepcion={}",
                        pendiente.tramiteId(), pendiente.gestoriaId(), e.getClass().getName());
                try {
                    registrarFallo(pendiente, e);
                } catch (RuntimeException otro) {
                    log.error("No se ha podido registrar el fallo de extraccion: tramiteId={}, gestoriaId={}, excepcion={}",
                            pendiente.tramiteId(), pendiente.gestoriaId(), otro.getClass().getName());
                }
            }
        }
        return cola.size();
    }

    private void procesar(TramitePendienteExtraccion pendiente) {
        Preparacion preparacion = lectura.execute(estado -> preparar(pendiente));
        if (preparacion == null || !preparacion.procede()) {
            transaccion.executeWithoutResult(estado -> dejarDeReintentar(pendiente, "ya no procede"));
            return;
        }
        if (preparacion.texto() == null || preparacion.texto().isBlank()) {
            transaccion.executeWithoutResult(estado -> aplicarSinTexto(pendiente));
            return;
        }

        // FUERA de cualquier transaccion: ni bloqueo de fila ni conexion retenida durante la red.
        ExtraccionValidada validada;
        try {
            TramiteExtraido extraido = extraccion.extraer(preparacion.texto());
            if (extraido == null) {
                throw new ExtraccionFallidaException("La IA no ha devuelto ninguna extraccion");
            }
            validada = ExtraccionValidador.validar(extraido);
        } catch (RuntimeException e) {
            registrarFallo(pendiente, e);
            return;
        }
        transaccion.executeWithoutResult(estado -> aplicarExito(pendiente, validada));
    }

    /** Lectura sin bloqueo: si procede y el texto del mensaje (con el gestoriaId del Tramite). */
    private Preparacion preparar(TramitePendienteExtraccion pendiente) {
        Optional<Tramite> tramite = tramiteRepository.findByIdAndGestoriaId(pendiente.tramiteId(), pendiente.gestoriaId());
        if (tramite.isEmpty() || !procede(tramite.get())) {
            return new Preparacion(false, null);
        }
        String texto = mensajeCampoRepository
                .findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc(pendiente.tramiteId(), pendiente.gestoriaId())
                .map(MensajeCampo::getCuerpo)
                .orElse(null);
        return new Preparacion(true, texto);
    }

    private record Preparacion(boolean procede, String texto) {
    }

    /**
     * Solo se aplica algo a un Tramite que nadie ha tocado: el de la primera extraccion
     * (PENDIENTE_EXTRACCION + PENDIENTE) o el que dejo el primer fallo sin cambios (PENDIENTE_REVISION
     * + FALLIDA con la misma version que dejo ese fallo).
     */
    static boolean procede(Tramite tramite) {
        if (tramite.getEstado() == EstadoTramite.PENDIENTE_EXTRACCION
                && tramite.getEstadoExtraccion() == EstadoExtraccion.PENDIENTE) {
            return true;
        }
        return tramite.getEstado() == EstadoTramite.PENDIENTE_REVISION
                && tramite.getEstadoExtraccion() == EstadoExtraccion.FALLIDA
                && tramite.getVersion() != null
                && Objects.equals(tramite.getVersion(), tramite.getVersionTrasFallo());
    }

    // ------------------------------------------------------------- escrituras, cada una en su transaccion

    private void aplicarExito(TramitePendienteExtraccion pendiente, ExtraccionValidada validada) {
        Optional<Tramite> cargado = cargarConBloqueo(pendiente);
        if (cargado.isEmpty()) {
            return;
        }
        Tramite tramite = cargado.get();
        if (!procede(tramite)) {
            dejarDeReintentar(pendiente, "resultado descartado: el tramite ha cambiado mientras respondia la IA");
            return;
        }
        long versionLeida = tramite.getVersion();

        // Crotales ANTES de tocar el Tramite: TramiteCrotalService hace flush, y con el Tramite ya
        // modificado ese flush sacaria un UPDATE y el del commit otro (dos incrementos de version).
        List<TramiteCrotal> filas = tramiteCrotalService.reemplazarCrotales(tramite, validada.crotales(), pendiente.gestoriaId());
        int quitados = quitarLosQueResuelvenAlMismoAnimal(filas);

        tramite.setTipoTramite(validada.tipo());
        tramite.setCrotalesDescartados(validada.descartados());
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setEstadoExtraccion(EstadoExtraccion.COMPLETADA);
        tramite.setProximoIntentoExtraccion(null);
        exigirUnSoloIncremento(tramite, versionLeida);
        log.info("Extraccion aplicada: tramiteId={}, gestoriaId={}, estadoExtraccion={}, intentos={}, crotales={}, "
                        + "descartados={}, mismoAnimal={}",
                tramite.getId(), pendiente.gestoriaId(), EstadoExtraccion.COMPLETADA, tramite.getIntentosExtraccion(),
                filas.size() - quitados, validada.descartados(), quitados);
    }

    /**
     * Decision 23 de A1: si dos crotales resuelven al mismo Animal, se queda el primero (en el orden
     * del mensaje). Los quitados no cuentan como descartados (no son identificadores invalidos).
     */
    private int quitarLosQueResuelvenAlMismoAnimal(List<TramiteCrotal> filas) {
        Set<Long> animales = new HashSet<>();
        List<TramiteCrotal> repetidas = new ArrayList<>();
        for (TramiteCrotal fila : filas) {
            if (fila.getAnimal() != null && !animales.add(fila.getAnimal().getId())) {
                repetidas.add(fila);
            }
        }
        if (!repetidas.isEmpty()) {
            tramiteCrotalRepository.deleteAll(repetidas);
            tramiteCrotalRepository.flush();
        }
        return repetidas.size();
    }

    private void aplicarSinTexto(TramitePendienteExtraccion pendiente) {
        Optional<Tramite> cargado = cargarConBloqueo(pendiente);
        if (cargado.isEmpty()) {
            return;
        }
        Tramite tramite = cargado.get();
        if (!procede(tramite)) {
            dejarDeReintentar(pendiente, "ya no procede");
            return;
        }
        long versionLeida = tramite.getVersion();
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TEXTO);
        tramite.setProximoIntentoExtraccion(null);
        exigirUnSoloIncremento(tramite, versionLeida);
        log.info("Mensaje sin texto, no se llama a la IA: tramiteId={}, gestoriaId={}, estadoExtraccion={}",
                tramite.getId(), pendiente.gestoriaId(), EstadoExtraccion.SIN_TEXTO);
    }

    /**
     * Un fallo cuenta un intento. Desde PENDIENTE es un cambio visible (a revision con FALLIDA, sin
     * tipo ni crotales; el texto en bruto ya esta en el mensaje) y deja versionTrasFallo = la version
     * resultante. Sobre un Tramite ya en FALLIDA, solo intentos y proximo, sin tocar la version.
     */
    private void registrarFallo(TramitePendienteExtraccion pendiente, RuntimeException fallo) {
        transaccion.executeWithoutResult(estado -> {
            Optional<Tramite> cargado = cargarConBloqueo(pendiente);
            if (cargado.isEmpty()) {
                return;
            }
            Tramite tramite = cargado.get();
            int intentos = tramite.getIntentosExtraccion() + 1;
            // Tras los UPDATE JPQL de abajo ya no se toca la entidad (clearAutomatically la desprende,
            // ver TramiteRepository): solo se leen su id y los valores calculados aqui.
            if (!procede(tramite)) {
                tramiteRepository.registrarIntentoFallidoSinCambiarVersion(tramite.getId(), pendiente.gestoriaId(), null);
                log.info("Fallo de extraccion sobre un tramite que ya no procede, no se reintenta: tramiteId={}, "
                                + "gestoriaId={}, intentos={}, excepcion={}",
                        tramite.getId(), pendiente.gestoriaId(), intentos, fallo.getClass().getSimpleName());
                return;
            }
            Instant proximo = siguienteIntento(intentos);
            if (tramite.getEstadoExtraccion() == EstadoExtraccion.PENDIENTE) {
                long versionLeida = tramite.getVersion();
                tramite.setIntentosExtraccion(intentos);
                tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
                tramite.setEstadoExtraccion(EstadoExtraccion.FALLIDA);
                tramite.setProximoIntentoExtraccion(proximo);
                // La version que dejara ESTE UPDATE (uno solo, comprobado abajo): fijarla despues del
                // flush ensuciaria el Tramite otra vez y saldria un segundo incremento en el commit.
                tramite.setVersionTrasFallo(versionLeida + 1);
                exigirUnSoloIncremento(tramite, versionLeida);
            } else {
                tramiteRepository.registrarIntentoFallidoSinCambiarVersion(tramite.getId(), pendiente.gestoriaId(), proximo);
            }
            log.warn("Fallo de extraccion: tramiteId={}, gestoriaId={}, estadoExtraccion={}, intentos={}, "
                            + "proximoIntento={}, excepcion={}",
                    tramite.getId(), pendiente.gestoriaId(), EstadoExtraccion.FALLIDA, intentos, proximo,
                    fallo.getClass().getSimpleName());
        });
    }

    /** Tras el fallo numero {@code intentos}: ahora + su plazo, o null si era el ultimo. */
    private Instant siguienteIntento(int intentos) {
        return intentos <= reintentos.size() ? reloj.instant().plus(reintentos.get(intentos - 1)) : null;
    }

    /** Sin tocar la version (UPDATE JPQL): nada visible cambia. */
    private void dejarDeReintentar(TramitePendienteExtraccion pendiente, String motivo) {
        tramiteRepository.dejarDeReintentarExtraccion(pendiente.tramiteId(), pendiente.gestoriaId());
        log.info("Extraccion detenida ({}): tramiteId={}, gestoriaId={}", motivo, pendiente.tramiteId(), pendiente.gestoriaId());
    }

    /** Bloqueo de fila + refresh, con gestoriaId explicito (patron de TramiteRevisionService). */
    private Optional<Tramite> cargarConBloqueo(TramitePendienteExtraccion pendiente) {
        Optional<Tramite> tramite = tramiteRepository.findConBloqueoByIdAndGestoriaId(pendiente.tramiteId(), pendiente.gestoriaId());
        tramite.ifPresent(entityManager::refresh);
        return tramite;
    }

    /**
     * Vuelca el Tramite (un UPDATE, que Hibernate acompaña de version + 1) y comprueba que la version
     * ha quedado exactamente en la leida + 1; si no, excepcion y rollback de toda la escritura.
     */
    private void exigirUnSoloIncremento(Tramite tramite, long versionLeida) {
        tramiteRepository.save(tramite);
        entityManager.flush();
        if (tramite.getVersion() != versionLeida + 1) {
            throw new IllegalStateException("La version del tramite no ha quedado en la leida + 1");
        }
    }
}
