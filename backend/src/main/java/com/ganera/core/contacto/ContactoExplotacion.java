package com.ganera.core.contacto;

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
 * Vínculo Contacto-Explotacion. Un titular puede tener varias filas (una
 * gestoría de varias explotaciones); un trabajador, exactamente una.
 * Entidad de scaffolding: sin repositorio propio todavía, igual que
 * UsuarioExplotacion — no construir lógica de filtrado encima sin que el
 * paso correspondiente lo pida explícitamente.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto_explotacion")
public class ContactoExplotacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;
}
