package com.ganera.core.whatsapp;

/**
 * Cuerpo del alta interna de un número DEDICADO (plan 2026-11-16, T4): el número (se normaliza a E.164) y la
 * gestoría dueña. Todos los números de esta tabla son DEDICADO, así que no hay campo de tipo.
 */
public record NumeroWhatsappRequest(String numero, Long gestoriaId) {
}
