package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las cuatro combinaciones del gate de la presentación automática por gestoría (T6): se encola solo si el
 * interruptor maestro está activo Y la gestoría está en la lista. Lógica pura, sin Spring.
 */
class PresentacionAutomaticaPropertiesTest {

    @Test
    void porDefectoEstaApagadaYLaListaVacia() {
        PresentacionAutomaticaProperties props = new PresentacionAutomaticaProperties();
        assertThat(props.isActiva()).isFalse();
        assertThat(props.getGestorias()).isEmpty();
        assertThat(props.habilitadaParaGestoria(4L)).isFalse();
    }

    @Test
    void activaYEnLaListaHabilita() {
        assertThat(props(true, Set.of(4L)).habilitadaParaGestoria(4L)).isTrue();
    }

    @Test
    void activaPeroFueraDeLaListaNoHabilita() {
        assertThat(props(true, Set.of(4L)).habilitadaParaGestoria(7L)).isFalse();
    }

    @Test
    void apagadaAunqueEsteEnLaListaNoHabilita() {
        assertThat(props(false, Set.of(4L)).habilitadaParaGestoria(4L)).isFalse();
    }

    @Test
    void activaConListaVaciaNoHabilitaANingunaGestoria() {
        assertThat(props(true, Set.of()).habilitadaParaGestoria(4L)).isFalse();
    }

    @Test
    void gestoriaIdNuloNuncaHabilita() {
        assertThat(props(true, Set.of(4L)).habilitadaParaGestoria(null)).isFalse();
    }

    @Test
    void setGestoriasNuloSeTrataComoListaVacia() {
        PresentacionAutomaticaProperties props = new PresentacionAutomaticaProperties();
        props.setActiva(true);
        props.setGestorias(null);
        assertThat(props.getGestorias()).isEmpty();
        assertThat(props.habilitadaParaGestoria(4L)).isFalse();
    }

    private static PresentacionAutomaticaProperties props(boolean activa, Set<Long> gestorias) {
        PresentacionAutomaticaProperties props = new PresentacionAutomaticaProperties();
        props.setActiva(activa);
        props.setGestorias(new java.util.LinkedHashSet<>(gestorias));
        return props;
    }
}
