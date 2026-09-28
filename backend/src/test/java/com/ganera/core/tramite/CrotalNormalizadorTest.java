package com.ganera.core.tramite;

import com.ganera.core.tramite.CrotalNormalizador.CrotalNormalizado;
import com.ganera.core.tramite.CrotalNormalizador.TipoCrotal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrotalNormalizadorTest {

    @Test
    void quitaEspaciosYPasaAMayusculas() {
        assertThat(CrotalNormalizador.normalizar("ES 1234 5678 9012"))
                .isEqualTo(new CrotalNormalizado("ES123456789012", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("es123456789012"))
                .isEqualTo(new CrotalNormalizado("ES123456789012", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("  eS12\t3456\n789012 "))
                .isEqualTo(new CrotalNormalizado("ES123456789012", TipoCrotal.COMPLETO));
    }

    @Test
    void quitaEspaciosNoSeparablesQueLleganAlPegarDesdeExcel() {
        assertThat(CrotalNormalizador.normalizar("ES 1234 5678 9012"))
                .isEqualTo(new CrotalNormalizado("ES123456789012", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar(" 1234 "))
                .isEqualTo(new CrotalNormalizado("1234", TipoCrotal.INCOMPLETO));
    }

    @Test
    void soloDigitosEntre4Y12EsIncompleto() {
        assertThat(CrotalNormalizador.normalizar("1234"))
                .isEqualTo(new CrotalNormalizado("1234", TipoCrotal.INCOMPLETO));
        assertThat(CrotalNormalizador.normalizar("123456"))
                .isEqualTo(new CrotalNormalizado("123456", TipoCrotal.INCOMPLETO));
        assertThat(CrotalNormalizador.normalizar("123456789012"))
                .isEqualTo(new CrotalNormalizado("123456789012", TipoCrotal.INCOMPLETO));
        assertThat(CrotalNormalizador.normalizar("0001"))
                .isEqualTo(new CrotalNormalizado("0001", TipoCrotal.INCOMPLETO));
    }

    @Test
    void treceOMasDigitosEsCompleto() {
        assertThat(CrotalNormalizador.normalizar("1234567890123"))
                .isEqualTo(new CrotalNormalizado("1234567890123", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("123456789012345678901234567890"))
                .isEqualTo(new CrotalNormalizado("123456789012345678901234567890", TipoCrotal.COMPLETO));
    }

    @Test
    void empezarPorDosLetrasEsCompletoAunqueTengaPocosDigitos() {
        assertThat(CrotalNormalizador.normalizar("ES12"))
                .isEqualTo(new CrotalNormalizado("ES12", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("fr1"))
                .isEqualTo(new CrotalNormalizado("FR1", TipoCrotal.COMPLETO));
    }

    @Test
    void cualquierOtraFormaValidaEsCompletaParaIgualdadExacta() {
        assertThat(CrotalNormalizador.normalizar("A1234"))
                .isEqualTo(new CrotalNormalizado("A1234", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("12A34"))
                .isEqualTo(new CrotalNormalizado("12A34", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("1234x"))
                .isEqualTo(new CrotalNormalizado("1234X", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("A"))
                .isEqualTo(new CrotalNormalizado("A", TipoCrotal.COMPLETO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12", "1", " 1 2 3 "})
    void tresDigitosOMenosSeRechazaConMotivoExplicito(String crotal) {
        assertThatThrownBy(() -> CrotalNormalizador.normalizar(crotal))
                .isInstanceOf(CrotalInvalidoException.class)
                .hasMessage("Crotal demasiado corto: indica al menos los últimos 4 dígitos");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " ", "-", "--", ".", "/", " - / . ", "ÑA1234", "12_34", "ES+1234", "ES1234%", "ß1234",
            "１２３４", "1234567890123456789012345678901"})
    void formatoInvalidoSeRechaza(String crotal) {
        assertThatThrownBy(() -> CrotalNormalizador.normalizar(crotal))
                .isInstanceOf(CrotalInvalidoException.class)
                .satisfies(e -> assertThat(e.getMessage()).isNotBlank());
    }

    /** Decision 24: guiones, puntos y barras se quitan igual que los espacios (valores pegados). */
    @Test
    void quitaGuionesPuntosYBarras() {
        assertThat(CrotalNormalizador.normalizar("ES-0100-0000-1234"))
                .isEqualTo(new CrotalNormalizado("ES010000001234", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("ES.0100.0000.1234"))
                .isEqualTo(new CrotalNormalizado("ES010000001234", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("ES/0100/0000/1234"))
                .isEqualTo(new CrotalNormalizado("ES010000001234", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("es - 0100.0000/1234"))
                .isEqualTo(new CrotalNormalizado("ES010000001234", TipoCrotal.COMPLETO));
        assertThat(CrotalNormalizador.normalizar("ES-12"))
                .isEqualTo(new CrotalNormalizado("ES12", TipoCrotal.COMPLETO));
    }

    @Test
    void separadoresEnUnSufijoSiguenSiendoIncompleto() {
        assertThat(CrotalNormalizador.normalizar("12-34"))
                .isEqualTo(new CrotalNormalizado("1234", TipoCrotal.INCOMPLETO));
        assertThat(CrotalNormalizador.normalizar("0000.1234"))
                .isEqualTo(new CrotalNormalizado("00001234", TipoCrotal.INCOMPLETO));
    }

    @Test
    void conSeparadoresTresDigitosSigueSiendoDemasiadoCorto() {
        assertThatThrownBy(() -> CrotalNormalizador.normalizar("1-2-3"))
                .isInstanceOf(CrotalInvalidoException.class)
                .hasMessage("Crotal demasiado corto: indica al menos los últimos 4 dígitos");
    }

    @Test
    void treintaCaracteresEsElMaximoValido() {
        String treinta = "ES" + "1".repeat(28);
        assertThat(CrotalNormalizador.normalizar(treinta).valor()).isEqualTo(treinta);
    }
}
