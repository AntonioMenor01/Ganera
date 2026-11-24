package com.ganera.core.tramite;

import com.ganera.core.ganadero.GanaderoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Agrega los números del dashboard (GET /resumen) por gestoría, en el mínimo de consultas y sin traer filas de
 * trámites para los contadores. gestoriaId llega SIEMPRE del JWT (parámetro real de cada consulta): ningún
 * findById desnudo ni dependencia del filtro ambiente de Hibernate. Las alertas activas reutilizan
 * {@link AlertaService} para no duplicar las reglas de plazos.
 */
@Service
public class ResumenService {

    /** La cola que el gestor revisa. PENDIENTE_EXTRACCION es transitorio de fondo y no cuenta. */
    static final Set<EstadoTramite> ESTADOS_PENDIENTES = Set.of(EstadoTramite.PENDIENTE_REVISION);
    /** Tamaño de la previa de la cola en el dashboard. */
    static final int TAMANO_PREVIA_COLA = 5;

    private final TramiteRepository tramiteRepository;
    private final GanaderoRepository ganaderoRepository;
    private final AlertaService alertaService;

    public ResumenService(TramiteRepository tramiteRepository,
                          GanaderoRepository ganaderoRepository,
                          AlertaService alertaService) {
        this.tramiteRepository = tramiteRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.alertaService = alertaService;
    }

    @Transactional(readOnly = true)
    public ResumenResponse generar(Long gestoriaId) {
        long tramitesPendientes = tramiteRepository.countByGestoriaIdAndEstadoIn(gestoriaId, ESTADOS_PENDIENTES);
        long ganaderos = ganaderoRepository.countByGestoriaId(gestoriaId);
        int alertasActivas = alertaService.listar(gestoriaId).size();
        List<ColaResumenItem> cola = tramiteRepository.previaCola(
                gestoriaId, EstadoTramite.PENDIENTE_REVISION, Pageable.ofSize(TAMANO_PREVIA_COLA));
        return new ResumenResponse(tramitesPendientes, ganaderos, alertasActivas, cola);
    }
}
