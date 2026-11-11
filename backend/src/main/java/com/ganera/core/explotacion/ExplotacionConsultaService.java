package com.ganera.core.explotacion;

import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.TramiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lectura enriquecida de una Explotación. Todo con gestoriaId explícito del JWT.
 */
@Service
public class ExplotacionConsultaService {

    private final ExplotacionRepository explotacionRepository;
    private final AnimalRepository animalRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;
    private final TramiteRepository tramiteRepository;

    public ExplotacionConsultaService(ExplotacionRepository explotacionRepository,
                                      AnimalRepository animalRepository,
                                      ContactoExplotacionRepository contactoExplotacionRepository,
                                      TramiteRepository tramiteRepository) {
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.tramiteRepository = tramiteRepository;
    }

    /** Vacío si no existe o es de otra gestoría (el controlador responde 404). */
    @Transactional(readOnly = true)
    public Optional<ExplotacionDetalleResponse> detalle(Long gestoriaId, Long explotacionId) {
        return explotacionRepository.findByIdAndGestoriaId(explotacionId, gestoriaId)
                .map(e -> construir(gestoriaId, e));
    }

    private ExplotacionDetalleResponse construir(Long gestoriaId, Explotacion explotacion) {
        long animales = animalRepository.countByExplotacionIdAndGestoriaId(explotacion.getId(), gestoriaId);
        long pendientes = tramiteRepository.countByExplotacionIdAndGestoriaIdAndEstado(
                explotacion.getId(), gestoriaId, EstadoTramite.PENDIENTE_REVISION);
        List<ExplotacionDetalleResponse.ContactoDeExplotacion> contactos =
                contactoExplotacionRepository
                        .findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(
                                List.of(explotacion.getId()), gestoriaId, gestoriaId)
                        .stream()
                        .sorted(Comparator.comparing((ContactoExplotacion ce) -> ce.getContacto().getNombre())
                                .thenComparing(ce -> ce.getContacto().getId()))
                        .map(ce -> new ExplotacionDetalleResponse.ContactoDeExplotacion(
                                ce.getContacto().getId(),
                                ce.getContacto().getNombre(),
                                ce.getContacto().getTelefono(),
                                ce.getRol()))
                        .toList();
        return new ExplotacionDetalleResponse(
                explotacion.getId(),
                explotacion.getCodigoRega(),
                explotacion.getNombre(),
                explotacion.getGanadero().getId(),
                explotacion.getGanadero().getNombre(),
                explotacion.getGanadero().getNif(),
                animales,
                pendientes,
                contactos);
    }
}
