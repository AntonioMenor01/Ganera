package com.ganera.core.whatsapp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * "Nunca se pierde un mensaje": si la recepcion normal ({@link MensajeEntranteService}) falla por
 * algo que no es un duplicado, su transaccion se deshace entera (mensaje incluido). Este bean guarda
 * entonces el mensaje en una transaccion propia (REQUIRES_NEW, en un bean aparte para que pase por
 * el proxy de Spring: la autoinvocacion no lo haria) con resultado ERROR_RECEPCION, sin gestoria,
 * contacto ni Tramite, para revisarlo a mano.
 *
 * <p>Usa {@code save} (no {@code saveAndFlush}): el flush lo hace el commit de esta transaccion.
 * Cualquier fallo aqui se propaga al controlador, que responde 500.
 */
@Service
public class MensajeRescateService {

    private final MensajeCampoRepository mensajeCampoRepository;
    private final Clock reloj;

    public MensajeRescateService(MensajeCampoRepository mensajeCampoRepository, Clock reloj) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long guardarConError(MensajeEntrante entrante) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(entrante.messageSid());
        mensaje.setTelefonoOrigen(entrante.telefonoParaGuardar());
        mensaje.setCuerpo(entrante.cuerpo());
        mensaje.setNumMedia(entrante.numMedia());
        mensaje.setCreatedAt(reloj.instant());
        mensaje.setResultado(ResultadoMensaje.ERROR_RECEPCION);
        return mensajeCampoRepository.save(mensaje).getId();
    }
}
