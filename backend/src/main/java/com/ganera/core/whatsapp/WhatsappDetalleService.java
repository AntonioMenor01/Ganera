package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.Tramite;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Arma el bloque «whatsapp» del detalle de un Tramite (B2 reducido, D14) para GET /tramites/{id} y PATCH.
 * Todas las consultas llevan el gestoriaId explicito: el ultimo mensaje del contacto (ventana de 24 h) y
 * las respuestas, estas en UNA consulta con su usuario (sin N+1). El Contacto es el del Tramite, ya
 * cargado con gestoriaId por quien llama.
 */
@Service
public class WhatsappDetalleService {

    private final MensajeCampoRepository mensajeCampoRepository;
    private final RespuestaWhatsappRepository respuestaWhatsappRepository;
    private final Clock reloj;

    public WhatsappDetalleService(MensajeCampoRepository mensajeCampoRepository,
                                  RespuestaWhatsappRepository respuestaWhatsappRepository,
                                  Clock reloj) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.reloj = reloj;
    }

    /** null si el Tramite no llego por WhatsApp. */
    public WhatsappTramiteResponse bloque(Tramite tramite, Long gestoriaId) {
        if (tramite.getOrigen() != OrigenTramite.WHATSAPP
                || !ReglasRespuestaWhatsapp.contactoDeLaGestoria(tramite.getContacto(), gestoriaId)) {
            // Sin origen WhatsApp no hay conversacion; con un contacto de otra gestoria (datos sembrados a
            // mano, revision N3) no se muestra nada de el.
            return null;
        }
        Instant ahora = reloj.instant();
        Contacto contacto = tramite.getContacto();
        Instant ventanaAbiertaHasta = ReglasRespuestaWhatsapp.ventanaAbiertaHasta(
                mensajeCampoRepository.ultimoMensajeDelContacto(contacto.getId(), gestoriaId));
        String motivo = ReglasRespuestaWhatsapp.motivoNoDisponible(tramite.getOrigen(), tramite.getEstado(),
                contacto.isActivo(), ventanaAbiertaHasta, ahora);
        List<RespuestaWhatsappResponse> respuestas = respuestaWhatsappRepository
                .findByTramiteIdAndGestoriaIdOrderByCreatedAtAscIdAsc(tramite.getId(), gestoriaId).stream()
                .map(r -> RespuestaWhatsappResponse.from(r, ahora))
                .toList();
        return new WhatsappTramiteResponse(
                contacto.getNombre(),
                EnmascaradorTelefono.enmascarar(contacto.getTelefono()),
                contacto.isActivo(),
                ventanaAbiertaHasta,
                tramite.getGestoria().getNombre(),
                motivo,
                respuestas);
    }
}
