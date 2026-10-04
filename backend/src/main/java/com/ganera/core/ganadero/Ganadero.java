package com.ganera.core.ganadero;

import com.ganera.core.shared.crypto.EncryptedStringConverter;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import com.ganera.core.shared.texto.NormalizadorBusqueda;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

    /**
     * NormalizadorBusqueda.normalizar(nombre): el nombre en minusculas, sin tildes ni
     * puntuacion. La mantiene setNombre (sin setter publico; nada de @PrePersist/@PreUpdate, ver
     * el plan de la busqueda sin tildes, D1) y la rellena la migracion V18 para las filas
     * anteriores. La usa la busqueda de GET /explotaciones?q= (el nombre del Ganadero cuenta).
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "nombre_busqueda", nullable = false)
    private String nombreBusqueda;

    /** NIF/CIF, clave de negocio real del Ganadero -- usada como clave de upsert por el importador Excel. */
    @Column(name = "nif", unique = true)
    private String nif;

    @Column(name = "ovz_usuario")
    private String ovzUsuario;

    @Column(name = "ovz_password_cifrada")
    @Convert(converter = EncryptedStringConverter.class)
    private String ovzPasswordCifrada;

    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.nombreBusqueda = NormalizadorBusqueda.normalizar(nombre);
    }
}
