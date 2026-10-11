package com.ganera.core.gestoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    /** Usuario por id con el gestoriaId explicito (nunca findById a secas sobre un
     * GestoriaScopedEntity). Lo usa «hecho en OVZ» para cargar al usuario autenticado. */
    Optional<Usuario> findByIdAndGestoriaId(Long id, Long gestoriaId);
}
