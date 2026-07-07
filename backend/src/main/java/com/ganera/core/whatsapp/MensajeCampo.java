package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.tramite.Tramite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mensaje crudo entrante de Twilio. NO extiende GestoriaScopedEntity: al
 * recibir el webhook aún no se conoce la Gestoria — gestoria/contacto/tramite
 * se rellenan una vez resuelto el Contacto por teléfono.
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

    @Lob
    @Column(nullable = false)
    private String cuerpo;

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
