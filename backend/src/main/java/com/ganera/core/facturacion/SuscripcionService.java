package com.ganera.core.facturacion;

import com.ganera.core.gestoria.GestoriaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class SuscripcionService {

    private static final Set<EstadoSuscripcion> ESTADOS_QUE_BLOQUEAN_APROBACION =
            EnumSet.of(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, EstadoSuscripcion.SUSPENDIDA);

    private final SuscripcionRepository suscripcionRepository;
    private final GestoriaRepository gestoriaRepository;

    public SuscripcionService(SuscripcionRepository suscripcionRepository,
                              GestoriaRepository gestoriaRepository) {
        this.suscripcionRepository = suscripcionRepository;
        this.gestoriaRepository = gestoriaRepository;
    }

    public boolean puedeAprobarTramites(Long gestoriaId) {
        return suscripcionRepository.findByGestoriaId(gestoriaId)
                .map(suscripcion -> !ESTADOS_QUE_BLOQUEAN_APROBACION.contains(suscripcion.getEstado()))
                .orElse(false);
    }

    /**
     * Devuelve la Suscripcion de la Gestoria, creandola en TRIAL si aun no existe
     * (Gestoria real no-piloto que llega al checkout sin fila previa). Idempotente
     * frente a doble click / carrera: si dos hilos intentan crearla a la vez, el
     * perdedor choca con UNIQUE(gestoria_id) (DataIntegrityViolationException) y
     * relee la fila que gano.
     */
    public Suscripcion obtenerOCrearSuscripcion(Long gestoriaId) {
        return suscripcionRepository.findByGestoriaId(gestoriaId)
                .orElseGet(() -> crearSuscripcionEnTrial(gestoriaId));
    }

    private Suscripcion crearSuscripcionEnTrial(Long gestoriaId) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        try {
            return suscripcionRepository.saveAndFlush(suscripcion);
        } catch (DataIntegrityViolationException e) {
            // Carrera sobre UNIQUE(gestoria_id): otro hilo la creo primero; releer la ganadora.
            // Este catch tambien atrapa la FK violation de un gestoriaId inexistente: en ese
            // caso la relectura no encuentra nada y se relanza la excepcion original (fail-explicit).
            return suscripcionRepository.findByGestoriaId(gestoriaId)
                    .orElseThrow(() -> e);
        }
    }
}
