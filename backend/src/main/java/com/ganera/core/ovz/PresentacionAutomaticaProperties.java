package com.ganera.core.ovz;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Interruptor de la presentación automática de la baja en OVZ (escalón 2, plan 2026-11-17), con dos niveles
 * (T6):
 *
 * <ul>
 *   <li>{@code activa} — interruptor MAESTRO. APAGADO por defecto: con él apagado no se encola nada al aprobar,
 *       sea cual sea la lista.</li>
 *   <li>{@code gestorias} — lista de ids de gestoría habilitadas, VACÍA por defecto. Con el interruptor maestro
 *       encendido, solo las gestorías de esta lista presentan automáticamente; una lista vacía no habilita a
 *       ninguna.</li>
 * </ul>
 *
 * <p>Fail-closed por diseño: se encola <b>solo si</b> {@code activa} <b>y</b> la gestoría está en {@code gestorias}
 * ({@link #habilitadaParaGestoria}). Así el despliegue a producción no cambia nada hasta encender el maestro y
 * añadir cada gestoría a la lista, una a una. El reencolado manual de un trabajo ya existente no depende de este
 * interruptor (es una acción explícita del gestor).
 */
@ConfigurationProperties(prefix = "ganera.ovz.presentacion-automatica")
public class PresentacionAutomaticaProperties {

    /** Interruptor maestro. APAGADO por defecto. */
    private boolean activa = false;

    /** Ids de gestoría habilitadas. VACÍA por defecto (ninguna). */
    private Set<Long> gestorias = new LinkedHashSet<>();

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public Set<Long> getGestorias() {
        return gestorias;
    }

    public void setGestorias(Set<Long> gestorias) {
        this.gestorias = gestorias == null ? new LinkedHashSet<>() : gestorias;
    }

    /**
     * ¿La presentación automática está encendida para esta gestoría? Solo si el interruptor maestro está activo
     * Y la gestoría está en la lista. Lista vacía = ninguna gestoría habilitada (aunque el maestro esté encendido).
     */
    public boolean habilitadaParaGestoria(Long gestoriaId) {
        return activa && gestoriaId != null && gestorias.contains(gestoriaId);
    }
}
