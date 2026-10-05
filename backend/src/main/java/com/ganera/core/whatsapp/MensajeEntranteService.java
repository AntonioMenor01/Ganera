package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.TelefonoNormalizador;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.tramite.EstadoExtraccion;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Recepcion de un mensaje de WhatsApp ya validado (firma de Twilio comprobada por el controlador),
 * en una sola transaccion corta (R2 del Prompt B1): guarda el {@link MensajeCampo}, resuelve el
 * Contacto por telefono y, si esta activo, crea el Tramite en su Gestoria. No llama a la IA: el
 * Tramite queda en PENDIENTE_EXTRACCION para el planificador (T2), o en PENDIENTE_REVISION con
 * SIN_TEXTO si el mensaje no trae texto. Nunca crea un Tramite aprobado ni toca OVZ.net.
 *
 * <p>Idempotencia por MessageSid: si ya esta guardado, {@link RecepcionMensaje#deDuplicado()} sin
 * escribir nada. Si dos entregas pasan a la vez esa comprobacion, la que pierde choca con el
 * UNIQUE(message_sid) en el {@code saveAndFlush}: esa {@code DataIntegrityViolationException} NO se
 * captura aqui (deshace toda la transaccion, Tramite incluido; capturarla dentro dejaria la
 * transaccion marcada para rollback). La captura el controlador, fuera.
 *
 * <p>Sin Authentication: el gestoriaFilter no esta activo. Por eso todo finder sobre entidades de
 * Gestoria lleva el gestoriaId explicito, el del Contacto; el unico sin tenant es
 * {@link ContactoRepository#findByTelefono}, reservado a este caso.
 *
 * <p>Logs: solo ids, resultado y, para un numero desconocido, el telefono enmascarado. Nunca el
 * texto ni el telefono completo.
 */
@Slf4j
@Service
public class MensajeEntranteService {

    private final MensajeCampoRepository mensajeCampoRepository;
    private final ContactoRepository contactoRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;
    private final TramiteRepository tramiteRepository;
    private final Clock reloj;

    public MensajeEntranteService(MensajeCampoRepository mensajeCampoRepository,
                                  ContactoRepository contactoRepository,
                                  ContactoExplotacionRepository contactoExplotacionRepository,
                                  TramiteRepository tramiteRepository,
                                  Clock reloj) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.tramiteRepository = tramiteRepository;
        this.reloj = reloj;
    }

    @Transactional
    public RecepcionMensaje registrar(MensajeEntrante entrante) {
        if (mensajeCampoRepository.existsByMessageSid(entrante.messageSid())) {
            return RecepcionMensaje.deDuplicado();
        }

        Optional<String> telefono = TelefonoNormalizador.normalizar(entrante.from());

        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(entrante.messageSid());
        // Si no se puede normalizar se guarda tal cual (truncado a 30): no puede ser el de ningun Contacto.
        mensaje.setTelefonoOrigen(entrante.telefonoParaGuardar());
        mensaje.setCuerpo(entrante.cuerpo());
        mensaje.setNumMedia(entrante.numMedia());
        mensaje.setCreatedAt(reloj.instant());
        mensaje.setResultado(ResultadoMensaje.NUMERO_DESCONOCIDO);
        // Primero el mensaje, con flush: un MessageSid repetido revienta aqui, antes de crear nada mas.
        mensajeCampoRepository.saveAndFlush(mensaje);

        Optional<Contacto> contacto = telefono.flatMap(contactoRepository::findByTelefono);
        if (contacto.isEmpty()) {
            log.info("Mensaje de WhatsApp de un numero desconocido ({}): mensajeCampoId={}, resultado={}",
                    EnmascaradorTelefono.enmascarar(mensaje.getTelefonoOrigen()), mensaje.getId(),
                    ResultadoMensaje.NUMERO_DESCONOCIDO);
            return new RecepcionMensaje(false, ResultadoMensaje.NUMERO_DESCONOCIDO, mensaje.getId(), null);
        }

        Contacto remitente = contacto.get();
        mensaje.setGestoria(remitente.getGestoria());
        mensaje.setContacto(remitente);

        if (!remitente.isActivo()) {
            mensaje.setResultado(ResultadoMensaje.CONTACTO_INACTIVO);
            log.info("Mensaje de WhatsApp de un contacto inactivo: mensajeCampoId={}, contactoId={}, resultado={}",
                    mensaje.getId(), remitente.getId(), ResultadoMensaje.CONTACTO_INACTIVO);
            return new RecepcionMensaje(false, ResultadoMensaje.CONTACTO_INACTIVO, mensaje.getId(), null);
        }

        Tramite tramite = tramiteRepository.save(nuevoTramite(remitente, entrante));
        mensaje.setTramite(tramite);
        mensaje.setResultado(ResultadoMensaje.TRAMITE_CREADO);
        log.info("Mensaje de WhatsApp recibido: mensajeCampoId={}, tramiteId={}, gestoriaId={}, estadoExtraccion={}, resultado={}",
                mensaje.getId(), tramite.getId(), remitente.getGestoria().getId(), tramite.getEstadoExtraccion(),
                ResultadoMensaje.TRAMITE_CREADO);
        return new RecepcionMensaje(false, ResultadoMensaje.TRAMITE_CREADO, mensaje.getId(), tramite.getId());
    }

    private Tramite nuevoTramite(Contacto remitente, MensajeEntrante entrante) {
        Tramite tramite = new Tramite();
        // Siempre la Gestoria del Contacto: es la unica que puede verlo.
        tramite.setGestoria(remitente.getGestoria());
        tramite.setContacto(remitente);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        tramite.setExplotacion(explotacionUnica(remitente).orElse(null));
        if (entrante.cuerpo().isBlank()) {
            // Solo foto o audio (adjuntos: B2): no hay nada que extraer; directo a revision.
            tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
            tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TEXTO);
        } else {
            tramite.setEstado(EstadoTramite.PENDIENTE_EXTRACCION);
            tramite.setEstadoExtraccion(EstadoExtraccion.PENDIENTE);
            tramite.setProximoIntentoExtraccion(reloj.instant());
        }
        return tramite;
    }

    /**
     * D2: si el Contacto tiene exactamente una Explotacion enlazada en su Gestoria, esa; con cero o
     * varias, ninguna (se asigna a mano en la revision). El enlace y la Explotacion tienen que ser
     * de la Gestoria del Contacto.
     */
    private Optional<Explotacion> explotacionUnica(Contacto remitente) {
        Long gestoriaId = remitente.getGestoria().getId();
        List<ContactoExplotacion> enlaces = contactoExplotacionRepository
                .findByContactoIdAndGestoriaIdAndExplotacionGestoriaId(remitente.getId(), gestoriaId, gestoriaId);
        return enlaces.size() == 1 ? Optional.of(enlaces.get(0).getExplotacion()) : Optional.empty();
    }
}
