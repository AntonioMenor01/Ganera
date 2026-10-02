package com.ganera.core.tramite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crotal de un Tramite en GET /tramites, GET /tramites/{id} y las respuestas de PATCH, aprobar y
 * rechazar. enInventario = resolucion EN_INVENTARIO.
 *
 * <p>{@code completo} (mini-prompt tras A2, punto 8, decisiones D8.1-D8.3) describe lo que se
 * ESCRIBIO ({@code crotalIndicado}), NO el crotal resuelto: un {@code 1234} que se resuelve
 * EN_INVENTARIO a {@code ES010000001234} sigue siendo {@code completo: false}. Se calcula al
 * serializar con {@link CrotalNormalizador} (COMPLETO frente a INCOMPLETO), sin columna en BD. Si un
 * {@code crotal_indicado} antiguo no pasara la normalizacion (no deberia: se guarda ya normalizado),
 * no se lanza nada -- {@code completo: false} y un aviso en el log, para no tumbar el listado.
 */
public record TramiteCrotalResponse(
        String crotalIndicado,
        String crotal,
        boolean completo,
        Long animalId,
        boolean enInventario,
        String resolucion) {

    private static final Logger log = LoggerFactory.getLogger(TramiteCrotalResponse.class);

    public static TramiteCrotalResponse from(TramiteCrotal fila) {
        return new TramiteCrotalResponse(
                fila.getCrotalIndicado(),
                fila.getCrotal(),
                esCompleto(fila),
                fila.getAnimal() != null ? fila.getAnimal().getId() : null,
                fila.getResolucion() == ResolucionCrotal.EN_INVENTARIO,
                fila.getResolucion().name());
    }

    private static boolean esCompleto(TramiteCrotal fila) {
        try {
            return CrotalNormalizador.normalizar(fila.getCrotalIndicado()).tipo()
                    == CrotalNormalizador.TipoCrotal.COMPLETO;
        } catch (CrotalInvalidoException e) {
            log.warn("crotal_indicado no normalizable en tramite_crotal id={}: se informa completo=false ({})",
                    fila.getId(), e.getMessage());
            return false;
        }
    }
}
