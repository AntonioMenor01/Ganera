package com.ganera.core.tramite;

import com.ganera.core.tramite.ExtraccionValidador.ExtraccionValidada;
import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Validacion en el servidor de lo que devuelve la IA (R3 del Prompt B1): puro, sin Spring. */
class ExtraccionValidadorTest {

    @Test
    void normalizaCadaCrotalYConservaElOrden() {
        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.BAJA_MUERTE,
                List.of("es-0100 0000.1234", "1234", " 5678 ")));

        assertThat(validada.tipo()).isEqualTo(TipoTramite.BAJA_MUERTE);
        assertThat(validada.crotales()).containsExactly("ES010000001234", "1234", "5678");
        assertThat(validada.descartados()).isZero();
    }

    @Test
    void tipoNuloSeConserva() {
        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(null, List.of("1234")));

        assertThat(validada.tipo()).isNull();
        assertThat(validada.crotales()).containsExactly("1234");
    }

    @Test
    void losInvalidosSeDescartanYSeCuentanSinLanzar() {
        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.ALTA_NACIMIENTO,
                Arrays.asList("123", "", "   ", null, "ES*1234", "ñ1234", "A".repeat(31), "56789")));

        assertThat(validada.crotales()).containsExactly("56789");
        assertThat(validada.descartados()).isEqualTo(7);
    }

    @Test
    void losDuplicadosDelMismoValorNormalizadoSeColapsanYNoCuentan() {
        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.ALTA_NACIMIENTO,
                List.of("1234", "12-34", "ES1", "es1", "1234", "123")));

        assertThat(validada.crotales()).containsExactly("1234", "ES1");
        assertThat(validada.descartados()).isEqualTo(1);
    }

    @Test
    void comoMuchoCincuentaValidosYLosDemasSeCuentan() {
        List<String> crotales = new ArrayList<>();
        for (int i = 0; i < 53; i++) {
            crotales.add(String.valueOf(100000 + i));
        }
        crotales.add("100000"); // repetido de uno aceptado: no cuenta
        crotales.add("100051"); // repetido de uno que paso del maximo: ya contado una vez
        crotales.add("12");     // invalido: cuenta

        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.DECLARACION_CENSO, crotales));

        assertThat(validada.crotales()).hasSize(50);
        assertThat(validada.crotales().get(0)).isEqualTo("100000");
        assertThat(validada.crotales().get(49)).isEqualTo("100049");
        assertThat(validada.descartados()).isEqualTo(4);
    }

    @Test
    void exactamenteCincuentaNoDescartaNada() {
        List<String> crotales = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            crotales.add(String.valueOf(200000 + i));
        }

        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.DECLARACION_CENSO, crotales));

        assertThat(validada.crotales()).hasSize(50);
        assertThat(validada.descartados()).isZero();
    }

    @Test
    void listaNulaOVaciaDaCeroCrotales() {
        assertThat(ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.ALTA_NACIMIENTO, null)))
                .isEqualTo(new ExtraccionValidada(TipoTramite.ALTA_NACIMIENTO, List.of(), 0));
        assertThat(ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.ALTA_NACIMIENTO, List.of())))
                .isEqualTo(new ExtraccionValidada(TipoTramite.ALTA_NACIMIENTO, List.of(), 0));
    }

    @Test
    void laListaDevueltaEsInmutable() {
        ExtraccionValidada validada = ExtraccionValidador.validar(new TramiteExtraido(TipoTramite.ALTA_NACIMIENTO, List.of("1234")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> validada.crotales().add("5678"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
