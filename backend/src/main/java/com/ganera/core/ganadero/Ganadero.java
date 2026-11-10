package com.ganera.core.ganadero;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ganera.core.shared.crypto.CredencialOvzCifrada;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import com.ganera.core.shared.texto.NormalizadorBusqueda;
import jakarta.persistence.Column;
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

    /**
     * Ciphertext EN REPOSO de la contrasena OVZ (M2): ya NO lleva {@code @Convert}, asi que materializar el
     * Ganadero no descifra nada; el descifrado es bajo demanda ({@code DescifradoCredencialOvz}). El getter
     * devuelve el ciphertext tal cual.
     */
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    @Column(name = "ovz_password_cifrada")
    private String ovzPasswordCifrada;

    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.nombreBusqueda = NormalizadorBusqueda.normalizar(nombre);
    }

    /**
     * Guarda el ciphertext en reposo. Solo acepta un {@link CredencialOvzCifrada} (lo unico que produce
     * {@code CifradorAesGcm.cifrar}): pasar un {@code String} plano no compila (M2, garantia de tipo). Null borra
     * la credencial.
     */
    public void setOvzPasswordCifrada(CredencialOvzCifrada credencial) {
        this.ovzPasswordCifrada = credencial == null ? null : credencial.valor();
    }
}
