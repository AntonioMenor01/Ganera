package com.ganera.core.ganadero;

import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.explotacion.ConteoExplotacionesPorGanadero;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lectura de Ganaderos. Todas las consultas llevan el gestoriaId del JWT como parametro real (nunca
 * findById ni el gestoriaFilter ambiente como unica defensa), y los datos relacionados se cargan
 * en lote: listado = pagina + un conteo agrupado; detalle = Ganadero + Explotaciones + Contactos.
 */
@Service
public class GanaderoConsultaService {

    private final GanaderoRepository ganaderoRepository;
    private final ExplotacionRepository explotacionRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;

    public GanaderoConsultaService(GanaderoRepository ganaderoRepository,
                                   ExplotacionRepository explotacionRepository,
                                   ContactoExplotacionRepository contactoExplotacionRepository) {
        this.ganaderoRepository = ganaderoRepository;
        this.explotacionRepository = explotacionRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
    }

    @Transactional(readOnly = true)
    public Page<GanaderoResumenResponse> listar(Long gestoriaId, Pageable pageable) {
        Page<Ganadero> pagina = ganaderoRepository.findByGestoriaId(gestoriaId, pageable);
        List<Long> ids = pagina.getContent().stream().map(Ganadero::getId).toList();
        Map<Long, Long> conteos = ids.isEmpty()
                ? Map.of()
                : explotacionRepository.contarPorGanadero(gestoriaId, ids).stream()
                        .collect(Collectors.toMap(
                                ConteoExplotacionesPorGanadero::ganaderoId,
                                ConteoExplotacionesPorGanadero::numeroExplotaciones));
        return pagina.map(g -> new GanaderoResumenResponse(
                g.getId(), g.getNombre(), g.getNif(), conteos.getOrDefault(g.getId(), 0L)));
    }

    /** Vacio si el Ganadero no existe o es de otra Gestoria (el controlador responde 404). */
    @Transactional(readOnly = true)
    public Optional<GanaderoDetalleResponse> detalle(Long gestoriaId, Long ganaderoId) {
        return ganaderoRepository.findByIdAndGestoriaId(ganaderoId, gestoriaId)
                .map(ganadero -> construirDetalle(gestoriaId, ganadero));
    }

    private GanaderoDetalleResponse construirDetalle(Long gestoriaId, Ganadero ganadero) {
        List<Explotacion> explotaciones =
                explotacionRepository.findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc(ganadero.getId(), gestoriaId);
        List<Long> idsExplotaciones = explotaciones.stream().map(Explotacion::getId).toList();
        Map<Long, List<GanaderoDetalleResponse.ContactoDeExplotacion>> contactosPorExplotacion = idsExplotaciones.isEmpty()
                ? Map.of()
                : contactoExplotacionRepository
                        .findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(
                                idsExplotaciones, gestoriaId, gestoriaId).stream()
                        .sorted(Comparator.comparing((ContactoExplotacion ce) -> ce.getContacto().getNombre())
                                .thenComparing(ce -> ce.getContacto().getId()))
                        .collect(Collectors.groupingBy(
                                ce -> ce.getExplotacion().getId(),
                                Collectors.mapping(ce -> new GanaderoDetalleResponse.ContactoDeExplotacion(
                                        ce.getContacto().getId(),
                                        ce.getContacto().getNombre(),
                                        ce.getContacto().getTelefono(),
                                        ce.getRol()), Collectors.toList())));
        List<GanaderoDetalleResponse.ExplotacionDeGanadero> detalleExplotaciones = explotaciones.stream()
                .map(e -> new GanaderoDetalleResponse.ExplotacionDeGanadero(
                        e.getId(), e.getCodigoRega(), e.getNombre(),
                        contactosPorExplotacion.getOrDefault(e.getId(), List.of())))
                .toList();
        return new GanaderoDetalleResponse(ganadero.getId(), ganadero.getNombre(), ganadero.getNif(), detalleExplotaciones);
    }
}
