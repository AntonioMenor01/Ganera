package com.ganera.core.gestoria;

import com.ganera.core.explotacion.Explotacion;
import jakarta.persistence.Entity;
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

/**
 * Vinculo Usuario-Explotacion para la futura cartera opcional (modoCartera).
 * Entidad de scaffolding: sin repositorio propio todavia, igual que
 * ContactoExplotacion -- no construir logica de filtrado encima sin que el
 * paso correspondiente lo pida explicitamente.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario_explotacion")
public class UsuarioExplotacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;
}
