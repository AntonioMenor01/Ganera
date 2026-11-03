package com.ganera.core.shared.auditoria;

import java.util.Objects;

/**
 * Quien hace la accion: un Usuario (siempre con su id, nunca {@code usuario(null)}) o un proceso sin usuario.
 * La gestoria de la fila NO va aqui: la pasa quien llama, sacada de la entidad tocada.
 */
public final class Actor {

    private static final Actor SISTEMA = new Actor(ActorTipo.SISTEMA, null);
    private static final Actor WEBHOOK_TWILIO = new Actor(ActorTipo.WEBHOOK_TWILIO, null);
    private static final Actor WEBHOOK_STRIPE = new Actor(ActorTipo.WEBHOOK_STRIPE, null);
    private static final Actor PLANIFICADOR = new Actor(ActorTipo.PLANIFICADOR, null);

    private final ActorTipo tipo;
    private final Long usuarioId;

    private Actor(ActorTipo tipo, Long usuarioId) {
        this.tipo = tipo;
        this.usuarioId = usuarioId;
    }

    public static Actor usuario(Long id) {
        return new Actor(ActorTipo.USUARIO, Objects.requireNonNull(id, "Actor.usuario exige el id del usuario"));
    }

    public static Actor sistema() {
        return SISTEMA;
    }

    public static Actor webhookTwilio() {
        return WEBHOOK_TWILIO;
    }

    public static Actor webhookStripe() {
        return WEBHOOK_STRIPE;
    }

    public static Actor planificador() {
        return PLANIFICADOR;
    }

    public ActorTipo tipo() {
        return tipo;
    }

    /** El id del usuario; null para los actores sin usuario. */
    public Long usuarioId() {
        return usuarioId;
    }
}
