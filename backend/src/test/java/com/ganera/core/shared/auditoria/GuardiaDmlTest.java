package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logica pura de la guardia DML (plan 2026-10-21, T6). La integracion real (que una escritura dentro de un
 * endpoint auditado ponga rojo) la ejercitan los tests E2E con {@link GuardiaDmlExtension}; aqui solo la regla.
 */
class GuardiaDmlTest {

    @Test
    void unaEscrituraDeNegocioSinFilaDeAuditoriaEsInvalida() {
        assertThat(GuardiaDml.verificar(Set.of("tramite"), false, Set.of())).isNotNull();
    }

    @Test
    void unaEscrituraDeNegocioConFilaDeAuditoriaEsValida() {
        assertThat(GuardiaDml.verificar(Set.of("tramite", "auditoria"), true, Set.of())).isNull();
    }

    @Test
    void soloUnaTablaExentaEsValidaAunqueNoHayaAuditoria() {
        assertThat(GuardiaDml.verificar(Set.of("alta_pendiente"), false, Set.of())).isNull();
    }

    @Test
    void unaTablaQueNoEsDeNegocioSeIgnora() {
        assertThat(GuardiaDml.verificar(Set.of("flyway_schema_history"), false, Set.of())).isNull();
    }

    @Test
    void unaExencionPorAnotacionDejaPasarEsaTabla() {
        assertThat(GuardiaDml.verificar(Set.of("mensaje_campo"), false, Set.of("mensaje_campo"))).isNull();
    }

    @Test
    void altaPendienteEstaObservadaYExentaALaVezParaQueLaExencionEsteVigilada() {
        // alta_pendiente es tabla de negocio observada Y exenta: quitarla de TABLAS_EXENTAS (mutacion) hace
        // que el escenario del checkout de la landing (que la escribe sin auditar) pase a invalido.
        assertThat(GuardiaDml.TABLAS_NEGOCIO).contains("alta_pendiente");
        assertThat(GuardiaDml.TABLAS_EXENTAS).contains("alta_pendiente");
        assertThat(GuardiaDml.verificar(Set.of("alta_pendiente"), false, Set.of())).isNull();
    }
}
