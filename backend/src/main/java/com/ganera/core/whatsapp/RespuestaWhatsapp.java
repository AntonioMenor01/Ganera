package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import com.ganera.core.tramite.Tramite;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Respuesta que un Usuario envia por WhatsApp al Contacto que escribio un Tramite (B2 reducido, D8; tabla
 * de V22). Tenant-scoped: toda consulta lleva el gestoriaId explicito. Enviar no toca el Tramite.
 *
 * <p>{@code createdAt} (heredado) se guarda en la columna {@code creado_en}, con el Instant del Clock.
 * {@code telefonoDestino} es la copia del telefono del Contacto en el momento del envio; la retencion
 * (12 meses) lo pone a null, cambia el cuerpo por el texto de eliminado y marca {@code textoVaciado}.
 * No se guarda nada de la respuesta de Twilio salvo el SID y el codigo de error.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "respuesta_whatsapp")
@AttributeOverride(name = "createdAt", column = @Column(name = "creado_en", nullable = false))
public class RespuestaWhatsapp extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false)
    private Tramite tramite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @Column(name = "telefono_destino", length = 30)
    private String telefonoDestino;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    /** Quien la envio: SIEMPRE el usuario del JWT, nunca un dato del cuerpo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enviado_por", nullable = false)
    private Usuario enviadoPor;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRespuestaWhatsapp estado;

    @Column(name = "twilio_message_sid", length = 64, unique = true)
    private String twilioMessageSid;

    @Column(name = "codigo_error_twilio")
    private Integer codigoErrorTwilio;

    @Column(name = "clave_idempotencia", nullable = false, length = 36)
    private String claveIdempotencia;

    @Column(name = "texto_vaciado", nullable = false)
    private boolean textoVaciado = false;
}
