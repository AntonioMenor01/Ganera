package com.ganera.core.gestoria;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Empleado de una Gestoria. Único que autentica en el sistema (ganaderos y contactos nunca lo hacen). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario extends GestoriaScopedEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    /**
     * true obliga a cambiar la contrasena en el primer login (contrasena temporal del alta automatica). El
     * frontend bloquea el resto de la app hasta cambiarla y POST /auth/cambiar-password lo pone a false. Los
     * usuarios creados por el onboarding manual nacen en false (su contrasena ya es la que eligieron).
     */
    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = false;
}
