package com.ganera.core.contacto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Quien escribe por WhatsApp. Deliberadamente NO extiende GestoriaScopedEntity:
 * al llegar un mensaje solo se conoce el teléfono, no la Gestoria — por eso
 * telefono es UNIQUE a secas (un único número de Twilio compartido entre
 * todas las gestorías) y la búsqueda por teléfono es la única forma de
 * resolver a qué Gestoria pertenece un mensaje entrante.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto")
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String telefono;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoContacto tipo;

    private Instant createdAt = Instant.now();
}
