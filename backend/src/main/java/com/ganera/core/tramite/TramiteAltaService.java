package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import com.ganera.core.whatsapp.WhatsappDetalleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alta MANUAL de un Trámite desde la UI ({@code POST /tramites}). El gestoriaId y el usuario
 * vienen SIEMPRE del JWT. Queda en {@link EstadoTramite#PENDIENTE_REVISION} con
 * {@link OrigenTramite#MANUAL}; no llama a la IA ni a OVZ. Aprobar sigue siendo un paso aparte.
 *
 * <p>El controlador valida tipo y explotacionId no nulos antes de llamar; aquí solo se resuelven
 * con gestoriaId explícito (404 si son de otra gestoría) y se guardan crotales/campos OVZ.
 */
@Service
public class TramiteAltaService {

    private final TramiteRepository tramiteRepository;
    private final ExplotacionRepository explotacionRepository;
    private final ContactoRepository contactoRepository;
    private final GestoriaRepository gestoriaRepository;
    private final TramiteCrotalService tramiteCrotalService;
    private final WhatsappDetalleService whatsappDetalleService;
    private final AuditoriaService auditoriaService;

    public TramiteAltaService(TramiteRepository tramiteRepository,
                              ExplotacionRepository explotacionRepository,
                              ContactoRepository contactoRepository,
                              GestoriaRepository gestoriaRepository,
                              TramiteCrotalService tramiteCrotalService,
                              WhatsappDetalleService whatsappDetalleService,
                              AuditoriaService auditoriaService) {
        this.tramiteRepository = tramiteRepository;
        this.explotacionRepository = explotacionRepository;
        this.contactoRepository = contactoRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.whatsappDetalleService = whatsappDetalleService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public TramiteDetalleResponse crear(Long gestoriaId, Long usuarioId, TipoTramite tipo,
                                        TramiteAltaRequest request) {
        Explotacion explotacion = explotacionRepository
                .findByIdAndGestoriaId(request.explotacionId(), gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        Contacto contacto = null;
        if (request.contactoId() != null) {
            contacto = contactoRepository.findByIdAndGestoriaId(request.contactoId(), gestoriaId)
                    .orElseThrow(RecursoNoEncontradoException::new);
        }

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        tramite.setExplotacion(explotacion);
        tramite.setContacto(contacto);
        tramite.setTipoTramite(tipo);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setOrigen(OrigenTramite.MANUAL);
        aplicarCamposOvz(tramite, request);
        tramiteRepository.saveAndFlush(tramite);

        List<TramiteCrotal> filas = List.of();
        if (request.crotales() != null && !request.crotales().isEmpty()) {
            filas = tramiteCrotalService.reemplazarCrotales(tramite, request.crotales(), gestoriaId);
            List<String> duplicados = motivosMismoAnimal(filas);
            if (!duplicados.isEmpty()) {
                throw new TramiteConflictoException(String.join(" ", duplicados));
            }
        }

        auditoriaService.registrar(AccionAuditoria.TRAMITE_CREADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.TRAMITE, tramite.getId(), null,
                DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.MANUAL)
                        .y(Clave.TIPO_TRAMITE, tipo)
                        .y(Clave.EXPLOTACION_ID, explotacion.getId())
                        .y(Clave.NUM_CROTALES, filas.size()));

        return TramiteDetalleResponse.from(tramite, null, respuestas(filas),
                whatsappDetalleService.bloque(tramite, gestoriaId),
                List.of());
    }

    private static void aplicarCamposOvz(Tramite tramite, TramiteAltaRequest request) {
        if (request.fechaHecho() != null) {
            tramite.setFechaHecho(request.fechaHecho());
        }
        if (request.fechaNacimiento() != null) {
            tramite.setFechaNacimiento(request.fechaNacimiento());
        }
        if (request.fechaIdentificacion() != null) {
            tramite.setFechaIdentificacion(request.fechaIdentificacion());
        }
        if (request.sexo() != null) {
            tramite.setSexo(textoONull(request.sexo(), 40));
        }
        if (request.numeroMer() != null) {
            tramite.setNumeroMer(textoONull(request.numeroMer(), 60));
        }
        if (request.destinoRega() != null) {
            tramite.setDestinoRega(textoONull(request.destinoRega(), 20));
        }
        if (request.destinoTexto() != null) {
            tramite.setDestinoTexto(textoONull(request.destinoTexto(), 255));
        }
        if (request.consignatario() != null) {
            tramite.setConsignatario(textoONull(request.consignatario(), 255));
        }
        if (request.aptitudMovimiento() != null) {
            tramite.setAptitudMovimiento(textoONull(request.aptitudMovimiento(), 80));
        }
        if (request.crotalMadreIndicado() != null) {
            String crudo = request.crotalMadreIndicado().trim();
            if (crudo.isEmpty()) {
                tramite.setCrotalMadreIndicado(null);
            } else {
                tramite.setCrotalMadreIndicado(CrotalNormalizador.normalizar(crudo).valor());
            }
        }
    }

    private static String textoONull(String valor, int max) {
        String t = valor.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new CrotalInvalidoException("Un campo supera la longitud máxima (" + max + ").");
        }
        return t;
    }

    private static List<String> motivosMismoAnimal(List<TramiteCrotal> filas) {
        Map<Long, List<String>> porAnimal = new LinkedHashMap<>();
        for (TramiteCrotal fila : filas) {
            if (fila.getAnimal() != null) {
                porAnimal.computeIfAbsent(fila.getAnimal().getId(), id -> new ArrayList<>())
                        .add(fila.getCrotalIndicado());
            }
        }
        List<String> motivos = new ArrayList<>();
        for (List<String> indicados : porAnimal.values()) {
            if (indicados.size() > 1) {
                motivos.add("Varios crotales apuntan al mismo animal (" + String.join(", ", indicados) + ").");
            }
        }
        return motivos;
    }

    private static List<TramiteCrotalResponse> respuestas(List<TramiteCrotal> filas) {
        return filas.stream().map(TramiteCrotalResponse::from).toList();
    }
}
