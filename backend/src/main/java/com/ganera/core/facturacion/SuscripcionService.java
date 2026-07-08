package com.ganera.core.facturacion;

import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class SuscripcionService {

    private static final Set<EstadoSuscripcion> ESTADOS_QUE_BLOQUEAN_APROBACION =
            EnumSet.of(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, EstadoSuscripcion.SUSPENDIDA);

    private final SuscripcionRepository suscripcionRepository;

    public SuscripcionService(SuscripcionRepository suscripcionRepository) {
        this.suscripcionRepository = suscripcionRepository;
    }

    public boolean puedeAprobarTramites(Long gestoriaId) {
        return suscripcionRepository.findByGestoriaId(gestoriaId)
                .map(suscripcion -> !ESTADOS_QUE_BLOQUEAN_APROBACION.contains(suscripcion.getEstado()))
                .orElse(false);
    }
}
