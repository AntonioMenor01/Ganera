package com.ganera.core.shared.auditoria;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Escribe las filas de auditoria (plan 2026-10-21). Dos puertas con la misma firma:
 *
 * <ul>
 *   <li>{@link #registrar}: {@code MANDATORY}, EXIGE la transaccion de la escritura de negocio. La fila se confirma o
 *       se deshace con ella; llamarlo fuera de una transaccion es un error (falla en el test, no se pierde en
 *       silencio).</li>
 *   <li>{@link #registrarAparte}: {@code REQUIRES_NEW}, solo para lecturas sensibles (D4), accesos a otra gestoria
 *       (D3) y logins (A2), que deben quedar aunque la operacion termine en 404/401/rollback.</li>
 * </ul>
 *
 * <p>La gestoria SIEMPRE la pasa quien llama, sacada de la entidad tocada (o del JWT), nunca de un contexto. Con
 * {@code Actor.usuario(id)} se comprueba que el usuario es de esa gestoria (nunca una fila con un usuario de otra);
 * en {@code ACCESO_OTRA_GESTORIA} la gestoria que se pasa es la del actor, asi que la regla se cumple igual.
 *
 * <p>ip y request_id salen de {@link ContextoPeticion} (NULL fuera de una peticion); creado_en, del Clock truncado a
 * microsegundos. Si la escritura falla, la excepcion se propaga: la operacion de negocio se deshace con ella.
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock reloj;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, UsuarioRepository usuarioRepository, Clock reloj) {
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionAuditoria accion, Long gestoriaId, Actor actor, EntidadAuditada entidadTipo,
                          Long entidadId, DatosAuditoria antes, DatosAuditoria despues) {
        escribir(accion, gestoriaId, actor, entidadTipo, entidadId, antes, despues);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAparte(AccionAuditoria accion, Long gestoriaId, Actor actor, EntidadAuditada entidadTipo,
                                Long entidadId, DatosAuditoria antes, DatosAuditoria despues) {
        escribir(accion, gestoriaId, actor, entidadTipo, entidadId, antes, despues);
    }

    private void escribir(AccionAuditoria accion, Long gestoriaId, Actor actor, EntidadAuditada entidadTipo,
                          Long entidadId, DatosAuditoria antes, DatosAuditoria despues) {
        exigir(accion, "accion");
        exigir(gestoriaId, "gestoriaId");
        exigir(actor, "actor");
        exigir(entidadTipo, "entidadTipo");
        if (actor.tipo() == ActorTipo.USUARIO
                && usuarioRepository.findByIdAndGestoriaId(actor.usuarioId(), gestoriaId).isEmpty()) {
            // Nunca una fila con un usuario de otra gestoria (ni con uno que no existe). Sin ids en el mensaje.
            throw new IllegalStateException("El usuario de la auditoria no es de la gestoria de la fila");
        }
        ContextoPeticion.Datos peticion = ContextoPeticion.actual().orElse(null);
        Auditoria fila = new Auditoria(
                entityManager.getReference(Gestoria.class, gestoriaId),
                reloj.instant().truncatedTo(ChronoUnit.MICROS),
                actor.tipo(),
                actor.usuarioId(),
                accion,
                entidadTipo,
                entidadId,
                antes == null ? null : antes.json(),
                despues == null ? null : despues.json(),
                peticion == null ? null : peticion.ip(),
                peticion == null ? null : peticion.requestId());
        auditoriaRepository.save(fila);
    }

    private static void exigir(Object valor, String nombre) {
        if (valor == null) {
            throw new IllegalArgumentException("Falta " + nombre + " en la fila de auditoria");
        }
    }
}
