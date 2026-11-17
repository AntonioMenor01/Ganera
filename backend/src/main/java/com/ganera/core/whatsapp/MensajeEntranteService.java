package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.TelefonoNormalizador;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
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
    private final NumeroWhatsappRepository numeroWhatsappRepository;
    private final AuditoriaService auditoriaService;
    private final Clock reloj;
    /** El numero COMPARTIDO (ganera.twilio.whatsapp-from) normalizado; vacio si no esta configurado. */
    private final Optional<String> numeroCompartido;

    public MensajeEntranteService(MensajeCampoRepository mensajeCampoRepository,
                                  ContactoRepository contactoRepository,
                                  ContactoExplotacionRepository contactoExplotacionRepository,
                                  TramiteRepository tramiteRepository,
                                  NumeroWhatsappRepository numeroWhatsappRepository,
                                  AuditoriaService auditoriaService,
                                  Clock reloj,
                                  @org.springframework.beans.factory.annotation.Value("${ganera.twilio.whatsapp-from:}")
                                  String whatsappFrom) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.tramiteRepository = tramiteRepository;
        this.numeroWhatsappRepository = numeroWhatsappRepository;
        this.auditoriaService = auditoriaService;
        this.reloj = reloj;
        this.numeroCompartido = TelefonoNormalizador.normalizar(whatsappFrom);
    }

    @Transactional
    public RecepcionMensaje registrar(MensajeEntrante entrante) {
        if (mensajeCampoRepository.existsByMessageSid(entrante.messageSid())) {
            return RecepcionMensaje.deDuplicado();
        }

        Optional<String> telefono = TelefonoNormalizador.normalizar(entrante.from());
        Optional<String> destino = entrante.telefonoDestinoNormalizado();

        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(entrante.messageSid());
        // Si no se puede normalizar se guarda tal cual (truncado a 30): no puede ser el de ningun Contacto.
        mensaje.setTelefonoOrigen(entrante.telefonoParaGuardar());
        // El numero de DESTINO (To) normalizado; null si no viene o no se normaliza. Nunca sale en un DTO.
        mensaje.setTelefonoDestino(destino.orElse(null));
        mensaje.setCuerpo(entrante.cuerpo());
        mensaje.setNumMedia(entrante.numMedia());
        mensaje.setCreatedAt(reloj.instant());
        mensaje.setResultado(ResultadoMensaje.NUMERO_DESCONOCIDO);
        // Primero el mensaje, con flush: un MessageSid repetido revienta aqui, antes de crear nada mas.
        mensajeCampoRepository.saveAndFlush(mensaje);

        // Bifurcacion por el numero de DESTINO (fase 1, plan 2026-11-16):
        //  - To == whatsapp-from configurado -> COMPARTIDO: se resuelve por el telefono del remitente (global), como hoy.
        //  - To en numero_whatsapp -> DEDICADO: la gestoria es la duena del numero; el contacto se busca DENTRO de ella
        //    (findByGestoriaIdAndTelefono). Asi un contacto de otra gestoria que escribe aqui queda como desconocido.
        //  - ni uno ni otro -> desconocido, como hoy, con un log.warn (sin el numero) para detectar numeros mal dados de alta.
        Optional<Contacto> contacto;
        NumeroWhatsapp numeroDedicado = null;
        if (destino.isPresent() && numeroCompartido.isPresent() && destino.get().equals(numeroCompartido.get())) {
            contacto = telefono.flatMap(contactoRepository::findByTelefono);
        } else {
            Optional<NumeroWhatsapp> numero = destino.flatMap(numeroWhatsappRepository::findByNumero);
            if (numero.isPresent()) {
                numeroDedicado = numero.get();
                Long gestoriaDelNumero = numeroDedicado.getGestoria().getId();
                contacto = telefono.flatMap(t -> contactoRepository.findByGestoriaIdAndTelefono(gestoriaDelNumero, t));
            } else {
                log.warn("Mensaje de WhatsApp a un numero de destino no registrado ni compartido: mensajeCampoId={}",
                        mensaje.getId());
                contacto = Optional.empty();
            }
        }

        if (contacto.isEmpty()) {
            log.info("Mensaje de WhatsApp de un numero desconocido ({}): mensajeCampoId={}, resultado={}",
                    EnmascaradorTelefono.enmascarar(mensaje.getTelefonoOrigen()), mensaje.getId(),
                    ResultadoMensaje.NUMERO_DESCONOCIDO);
            return new RecepcionMensaje(false, ResultadoMensaje.NUMERO_DESCONOCIDO, mensaje.getId(), null);
        }

        Contacto remitente = contacto.get();
        mensaje.setGestoria(remitente.getGestoria());
        mensaje.setContacto(remitente);

        Long gestoriaId = remitente.getGestoria().getId();
        if (!remitente.isActivo()) {
            mensaje.setResultado(ResultadoMensaje.CONTACTO_INACTIVO);
            // Contacto conocido pero inactivo: solo MENSAJE_RECIBIDO (no se crea Tramite). Actor WEBHOOK_TWILIO,
            // gestoria = la del contacto. Numero desconocido y ERROR_RECEPCION (sin gestoria) quedan FUERA.
            auditarMensajeRecibido(gestoriaId, mensaje);
            log.info("Mensaje de WhatsApp de un contacto inactivo: mensajeCampoId={}, contactoId={}, resultado={}",
                    mensaje.getId(), remitente.getId(), ResultadoMensaje.CONTACTO_INACTIVO);
            return new RecepcionMensaje(false, ResultadoMensaje.CONTACTO_INACTIVO, mensaje.getId(), null);
        }

        Tramite tramite = tramiteRepository.save(nuevoTramite(remitente, entrante, numeroDedicado));
        mensaje.setTramite(tramite);
        mensaje.setResultado(ResultadoMensaje.TRAMITE_CREADO);
        auditarMensajeRecibido(gestoriaId, mensaje);
        auditoriaService.registrar(AccionAuditoria.TRAMITE_CREADO, gestoriaId, Actor.webhookTwilio(),
                EntidadAuditada.TRAMITE, tramite.getId(), null,
                DatosAuditoria.con(Clave.ESTADO, tramite.getEstado())
                        .y(Clave.ESTADO_EXTRACCION, tramite.getEstadoExtraccion())
                        .y(Clave.EXPLOTACION_ID,
                                tramite.getExplotacion() != null ? tramite.getExplotacion().getId() : null)
                        // Id del numero DEDICADO por el que entro (null en COMPARTIDO); nunca el telefono (D9).
                        .y(Clave.NUMERO_WHATSAPP_ID, numeroDedicado != null ? numeroDedicado.getId() : null));
        log.info("Mensaje de WhatsApp recibido: mensajeCampoId={}, tramiteId={}, gestoriaId={}, estadoExtraccion={}, resultado={}",
                mensaje.getId(), tramite.getId(), gestoriaId, tramite.getEstadoExtraccion(),
                ResultadoMensaje.TRAMITE_CREADO);
        return new RecepcionMensaje(false, ResultadoMensaje.TRAMITE_CREADO, mensaje.getId(), tramite.getId());
    }

    /** Fila MENSAJE_RECIBIDO (resultado y numMedia como codigo/entero; nunca el texto ni el telefono). */
    private void auditarMensajeRecibido(Long gestoriaId, MensajeCampo mensaje) {
        auditoriaService.registrar(AccionAuditoria.MENSAJE_RECIBIDO, gestoriaId, Actor.webhookTwilio(),
                EntidadAuditada.MENSAJE_CAMPO, mensaje.getId(), null,
                DatosAuditoria.con(Clave.RESULTADO, mensaje.getResultado())
                        .y(Clave.NUM_MEDIA, mensaje.getNumMedia()));
    }

    private Tramite nuevoTramite(Contacto remitente, MensajeEntrante entrante, NumeroWhatsapp numeroDedicado) {
        Tramite tramite = new Tramite();
        // Siempre la Gestoria del Contacto: es la unica que puede verlo.
        tramite.setGestoria(remitente.getGestoria());
        tramite.setContacto(remitente);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        // Numero DEDICADO por el que entro (null en COMPARTIDO/legacy). Invariante: si no es null es de la
        // misma gestoria que el tramite, porque el remitente se resolvio con findByGestoriaIdAndTelefono de esa gestoria.
        tramite.setNumeroWhatsapp(numeroDedicado);
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
