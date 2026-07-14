package com.ganera.core.ganadero;

import com.ganera.core.shared.crypto.EncryptedStringConverter;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cliente de una Gestoria. Nunca autentica; su único canal es WhatsApp vía Contacto. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ganadero")
public class Ganadero extends GestoriaScopedEntity {

    @Column(nullable = false)
    private String nombre;

    /** NIF/CIF, clave de negocio real del Ganadero -- usada como clave de upsert por el importador Excel. */
    @Column(name = "nif", unique = true)
    private String nif;

    @Column(name = "ovz_usuario")
    private String ovzUsuario;

    @Column(name = "ovz_password_cifrada")
    @Convert(converter = EncryptedStringConverter.class)
    private String ovzPasswordCifrada;
}
