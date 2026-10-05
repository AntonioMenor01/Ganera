package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.tramite.Tramite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mensaje crudo entrante de Twilio. NO extiende GestoriaScopedEntity: al
 * recibir el webhook aún no se conoce la Gestoria — gestoria/contacto/tramite
 * se rellenan una vez resuelto el Contacto por teléfono (gestoria null si el numero es desconocido).
 *
 * Solo se guarda lo necesario (R4 del Prompt B1): MessageSid, telefono de origen normalizado, texto,
 * NumMedia, fecha, resultado y enlaces. Nunca ProfileName, WaId, AccountSid, URLs de adjuntos ni el
 * formulario completo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mensaje_campo")
public class MensajeCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_sid", nullable = false, unique = true)
    private String messageSid;

    @Column(name = "telefono_origen", nullable = false)
    private String telefonoOrigen;

    /** Columna TEXT (V9). Sin @Lob: un @Lob String no valida contra TEXT en H2 y en PostgreSQL se
     * leeria como un large object. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    /** Que paso con el mensaje (V19). Null en los mensajes anteriores a B1. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ResultadoMensaje resultado;

    /** Cuantos adjuntos traia (NumMedia). Solo el numero: nunca se guardan sus URLs. */
    @Column(name = "num_media", nullable = false)
    private int numMedia = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestoria_id")
    private Gestoria gestoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_id")
    private Contacto contacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tramite_id")
    private Tramite tramite;

    private Instant createdAt = Instant.now();
}
