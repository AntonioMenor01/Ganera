package com.ganera.core.shared.texto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizadorBusquedaTest {

    @Test
    void quitaTildesYPasaAMinusculas() {
        assertThat(NormalizadorBusqueda.normalizar("MARTÍNEZ")).isEqualTo("martinez");
        assertThat(NormalizadorBusqueda.normalizar("Martínez")).isEqualTo("martinez");
        assertThat(NormalizadorBusqueda.normalizar("Ángel Óscar Úbeda")).isEqualTo("angel oscar ubeda");
    }

    @Test
    void pliegaEnieYCedilla() {
        assertThat(NormalizadorBusqueda.normalizar("Peña")).isEqualTo("pena");
        assertThat(NormalizadorBusqueda.normalizar("PEÑA")).isEqualTo("pena");
        assertThat(NormalizadorBusqueda.normalizar("França")).isEqualTo("franca");
    }

    @Test
    void quitaDieresis() {
        assertThat(NormalizadorBusqueda.normalizar("Güell")).isEqualTo("guell");
    }

    @Test
    void ordinalesPasanALetra() {
        assertThat(NormalizadorBusqueda.normalizar("Nº 3")).isEqualTo("no 3");
        assertThat(NormalizadorBusqueda.normalizar("1ª")).isEqualTo("1a");
    }

    @Test
    void eliminaGuionesSinDejarHueco() {
        assertThat(NormalizadorBusqueda.normalizar("Martín-Pérez")).isEqualTo("martinperez");
    }

    @Test
    void eliminaPuntosYApostrofos() {
        assertThat(NormalizadorBusqueda.normalizar("S.L.")).isEqualTo("sl");
        assertThat(NormalizadorBusqueda.normalizar("O'Donnell")).isEqualTo("odonnell");
        assertThat(NormalizadorBusqueda.normalizar("Granja/Norte")).isEqualTo("granjanorte");
    }

    @Test
    void eliminaComodinesDeLike() {
        assertThat(NormalizadorBusqueda.normalizar("50%_!")).isEqualTo("50");
    }

    @Test
    void colapsaEspaciosDurosTabuladoresYMultiples() {
        assertThat(NormalizadorBusqueda.normalizar("Finca Pérez")).isEqualTo("finca perez");
        assertThat(NormalizadorBusqueda.normalizar("Finca\t\t  Pérez \n Sur")).isEqualTo("finca perez sur");
        assertThat(NormalizadorBusqueda.normalizar("a b c　d")).isEqualTo("a b c d");
    }

    @Test
    void recortaLosExtremos() {
        assertThat(NormalizadorBusqueda.normalizar("  Pérez  ")).isEqualTo("perez");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "%% --", " \t", "¡¿?!"})
    void nuloVacioOSoloPuntuacionDaVacio(String texto) {
        assertThat(NormalizadorBusqueda.normalizar(texto)).isEmpty();
    }

    @Test
    void codigoRega() {
        assertThat(NormalizadorBusqueda.normalizar("ES-12 3456")).isEqualTo("es12 3456");
        assertThat(NormalizadorBusqueda.normalizar("ES123456789012")).isEqualTo("es123456789012");
    }

    @Test
    void palabrasDescartaLasQueQuedanVacias() {
        assertThat(NormalizadorBusqueda.palabras("martin - perez")).containsExactly("martin", "perez");
    }

    @Test
    void palabrasSoloPuntuacionDaListaVacia() {
        assertThat(NormalizadorBusqueda.palabras("%% --")).isEmpty();
    }

    @Test
    void palabrasQuitaRepetidasTrasNormalizar() {
        assertThat(NormalizadorBusqueda.palabras("Pérez PEREZ perez")).containsExactly("perez");
        assertThat(NormalizadorBusqueda.palabras("b a B c A")).containsExactly("b", "a", "c");
    }

    @Test
    void palabrasNormalizaCadaUna() {
        assertThat(NormalizadorBusqueda.palabras("ES12 Pérez")).containsExactly("es12", "perez");
        assertThat(NormalizadorBusqueda.palabras("martin-perez")).containsExactly("martinperez");
    }

    @Test
    void palabrasUsaEspacioDuroYTabuladorComoSeparador() {
        assertThat(NormalizadorBusqueda.palabras("Peña García\tES12")).containsExactly("pena", "garcia", "es12");
        assertThat(NormalizadorBusqueda.palabras("  peña   garcía  ")).containsExactly("pena", "garcia");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " \t\n"})
    void palabrasNuloOEnBlancoDaListaVacia(String q) {
        assertThat(NormalizadorBusqueda.palabras(q)).isEmpty();
    }

    @Test
    void palabrasDevuelveListaInmutable() {
        List<String> palabras = NormalizadorBusqueda.palabras("martin perez");
        assertThatThrownBy(() -> palabras.add("otra")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> NormalizadorBusqueda.palabras(null).add("otra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // Acentos sueltos de espaciado (\p{Sk}): sin tratarlos antes, NFKD los
    // convierte en espacio + marca y partian la palabra ("mart in").
    @Test
    void eliminaAcentosSueltosSinDejarHueco() {
        assertThat(NormalizadorBusqueda.normalizar("Mart´in")).isEqualTo("martin");
        assertThat(NormalizadorBusqueda.normalizar("Gu¨ell")).isEqualTo("guell");
        assertThat(NormalizadorBusqueda.normalizar("Fran¸a")).isEqualTo("frana");
        assertThat(NormalizadorBusqueda.normalizar("a˜b˘c˙d˚e˝f¯g")).isEqualTo("abcdefg");
        assertThat(NormalizadorBusqueda.normalizar("Mart`in Pe^na")).isEqualTo("martin pena");
    }

    @Test
    void palabrasConAcentoSueltoNoSeParten() {
        assertThat(NormalizadorBusqueda.palabras("Mart´in")).containsExactly("martin");
        assertThat(NormalizadorBusqueda.palabras("a¨b")).containsExactly("ab");
    }

    // U+2028, U+0085 y U+1680 no los toca NFKD y solo son \s con
    // UNICODE_CHARACTER_CLASS: sin esa opcion se eliminarian ("xy").
    @Test
    void espaciosUnicodeQueSoloReconoceUnicodeCharacterClass() {
        assertThat(NormalizadorBusqueda.normalizar("x y\u0085z w")).isEqualTo("x y z w");
        assertThat(NormalizadorBusqueda.palabras("pena garcia\u0085es12 sur"))
                .containsExactly("pena", "garcia", "es12", "sur");
    }

    // Defensa: algun caracter que no es \p{Sk} (U+203E, raya alta) sigue
    // convirtiendose en espacio + marca con NFKD; una palabra nunca lleva espacio.
    @Test
    void palabrasVuelveAPartirSiNfkdIntroduceUnEspacio() {
        assertThat(NormalizadorBusqueda.normalizar("a‾b")).isEqualTo("a b");
        assertThat(NormalizadorBusqueda.palabras("a‾b")).containsExactly("a", "b");
    }
    // --- textoExplotacion: columna explotacion.busqueda (entidad y V18) ---

    @Test
    void textoExplotacionUneReGaYNombreNormalizadosConUnEspacio() {
        assertThat(NormalizadorBusqueda.textoExplotacion("ES-12 3456", "Finca La Peña"))
                .isEqualTo("es12 3456 finca la pena");
        assertThat(NormalizadorBusqueda.textoExplotacion("ES120000000001", "Martín-Pérez"))
                .isEqualTo("es120000000001 martinperez");
    }

    @Test
    void textoExplotacionSinEspaciosSobrantesSiUnoEstaVacio() {
        assertThat(NormalizadorBusqueda.textoExplotacion("ES12", null)).isEqualTo("es12");
        assertThat(NormalizadorBusqueda.textoExplotacion(null, "Peña")).isEqualTo("pena");
        assertThat(NormalizadorBusqueda.textoExplotacion("ES12", "---")).isEqualTo("es12");
        assertThat(NormalizadorBusqueda.textoExplotacion("%%", " Peña ")).isEqualTo("pena");
        assertThat(NormalizadorBusqueda.textoExplotacion(null, null)).isEmpty();
        assertThat(NormalizadorBusqueda.textoExplotacion("", "")).isEmpty();
    }
}
