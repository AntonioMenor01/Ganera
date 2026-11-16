package com.ganera.core.whatsapp;

import com.ganera.core.gestoria.Gestoria;
import jakarta.persistence.Column;
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

import java.time.Instant;

/**
 * Número de WhatsApp propio de una gestoría (modalidad DEDICADO, fase 1; plan
 * 2026-11-16-whatsapp-numero-por-gestoria). Al que escriben sus clientes: la gestoría de un mensaje se
 * resuelve por el número de DESTINO ({@code To}), no por el teléfono del remitente.
 *
 * <p>NO extiende {@link com.ganera.core.shared.tenant.GestoriaScopedEntity} a propósito: el webhook la
 * consulta sin tenant (no hay {@code Authentication} al recibir el mensaje) con
 * {@link NumeroWhatsappRepository#findByNumero}. Aun así {@code gestoria} es obligatoria: la tabla solo
 * guarda números DEDICADO (el COMPARTIDO de hoy se identifica por configuración, no es una fila), así
 * que siempre tiene dueña y la auditoría de su alta siempre tiene gestoría.
 *
 * <p>{@code numero} va normalizado a E.164 (TelefonoNormalizador) y es UNIQUE global.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "numero_whatsapp")
public class NumeroWhatsapp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestoria_id", nullable = false)
    private Gestoria gestoria;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
