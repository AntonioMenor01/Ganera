package com.ganera.core.whatsapp;

/**
 * Respuesta del alta interna de un número DEDICADO (plan 2026-11-16, T4): solo ids. El número (teléfono) NO se
 * devuelve — el llamador interno ya lo conoce y así no sale ningún teléfono en una respuesta.
 */
public record NumeroWhatsappResponse(Long id, Long gestoriaId) {
}
