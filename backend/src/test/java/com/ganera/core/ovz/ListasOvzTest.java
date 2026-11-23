package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listas OVZ reales del mapa (docs/referencias/mapa-ovz.md). Guarda el valor de OVZ además del texto;
 * las razas se mapean por valor (hay dos «DESCONOCIDA»). Solo la categoría del censo sigue provisional.
 */
class ListasOvzTest {

    private ListasOvz.ListaOvz lista(String id) {
        return ListasOvz.todas().listas().stream()
                .filter(l -> l.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta la lista " + id));
    }

    @Test
    void cadaOpcionLlevaTextoYValor() {
        ListasOvz.ListaOvz sexo = lista("sexo");
        assertThat(sexo.provisional()).isFalse();
        assertThat(sexo.valores()).containsExactly(
                new ListasOvz.OpcionOvz("Hembra", "H"),
                new ListasOvz.OpcionOvz("Macho", "M"));
    }

    @Test
    void lasRazasSeMapeanPorValorConLasDosDesconocidas() {
        ListasOvz.ListaOvz raza = lista("raza");
        assertThat(raza.provisional()).isFalse();
        assertThat(raza.valores()).hasSize(134);
        // Dos «DESCONOCIDA», distintas solo por valor.
        assertThat(raza.valores()).contains(
                new ListasOvz.OpcionOvz("DESCONOCIDA", "901"),
                new ListasOvz.OpcionOvz("DESCONOCIDA", "955"));
        // Dos casi iguales que solo se distinguen por valor.
        assertThat(raza.valores()).contains(
                new ListasOvz.OpcionOvz("PARDE DE MONTAÑA", "1088"),
                new ListasOvz.OpcionOvz("PARDA DE MONTAÑA", "1322"));
        // Todos los valores son únicos (clave de mapeo).
        assertThat(raza.valores().stream().map(ListasOvz.OpcionOvz::valor).distinct().count())
                .isEqualTo(134);
    }

    @Test
    void lasListasDelMapaDejanDeSerProvisionales() {
        List<String> delMapa = List.of("sexo", "raza", "tipo_identificador", "oficina_di",
                "comunidad_autonoma", "tipo_explotacion", "medio_transporte", "tipo_responsable",
                "aptitud_movimiento", "emision_di");
        for (String id : delMapa) {
            ListasOvz.ListaOvz l = lista(id);
            assertThat(l.provisional()).as("%s no es provisional", id).isFalse();
            assertThat(l.valores()).as("%s tiene valores", id).isNotEmpty();
        }
    }

    @Test
    void medioTransporteYResponsableLlevanSuCodigo() {
        assertThat(lista("medio_transporte").valores())
                .startsWith(new ListasOvz.OpcionOvz("Camión", "1"))
                .contains(new ListasOvz.OpcionOvz("Otros", "99"));
        assertThat(lista("tipo_responsable").valores())
                .contains(new ListasOvz.OpcionOvz("Titular de la Explotación", "1"));
    }

    @Test
    void aptitudTraeLosCuatroValores() {
        assertThat(lista("aptitud_movimiento").valores()).containsExactly(
                new ListasOvz.OpcionOvz("SACRIFICIO", "0"),
                new ListasOvz.OpcionOvz("VIDA", "1"),
                new ListasOvz.OpcionOvz("REPRODUCC", "2"),
                new ListasOvz.OpcionOvz("CEBO", "3"));
    }

    @Test
    void esRazaValidaCompruebaElCodigoYLidiaTieneSuConstante() {
        assertThat(ListasOvz.esRazaValida("14")).isTrue();   // RETINTA
        assertThat(ListasOvz.esRazaValida("1322")).isTrue(); // PARDA DE MONTAÑA
        assertThat(ListasOvz.esRazaValida("RETINTA")).isFalse(); // es el texto, no el código
        assertThat(ListasOvz.esRazaValida("9999")).isFalse();
        assertThat(ListasOvz.esRazaValida(null)).isFalse();
        assertThat(ListasOvz.esRazaValida("  ")).isFalse();
        assertThat(ListasOvz.CODIGO_RAZA_LIDIA).isEqualTo("5");
        assertThat(ListasOvz.esRazaValida(ListasOvz.CODIGO_RAZA_LIDIA)).isTrue();
    }

    @Test
    void codigoSexoAceptaCodigoOTextoSinDistinguirMayusculas() {
        assertThat(ListasOvz.codigoSexo("H")).isEqualTo("H");
        assertThat(ListasOvz.codigoSexo("m")).isEqualTo("M");
        assertThat(ListasOvz.codigoSexo("Hembra")).isEqualTo("H");
        assertThat(ListasOvz.codigoSexo(" macho ")).isEqualTo("M");
        assertThat(ListasOvz.codigoSexo("desconocido")).isNull();
        assertThat(ListasOvz.codigoSexo(null)).isNull();
    }

    @Test
    void codigoEmisionDiMapeaTextoOCodigoYConstantes() {
        assertThat(ListasOvz.CODIGO_EMISION_DI_OVZ).isEqualTo("OVZ");
        assertThat(ListasOvz.CODIGO_EMISION_DI_IMPRIME).isEqualTo("OVZNET");
        assertThat(ListasOvz.codigoEmisionDi("OVZ")).isEqualTo("OVZ");
        assertThat(ListasOvz.codigoEmisionDi("ovznet")).isEqualTo("OVZNET");
        assertThat(ListasOvz.codigoEmisionDi("Prefiero que mis DI's sean emitidos en la OVZ")).isEqualTo("OVZ");
        assertThat(ListasOvz.codigoEmisionDi("otra cosa")).isNull();
        assertThat(ListasOvz.codigoEmisionDi(null)).isNull();
        assertThat(ListasOvz.CODIGO_TIPO_IDENTIFICADOR_DEFECTO).isEqualTo("04");
    }

    @Test
    void soloLaCategoriaDeCensoSigueProvisional() {
        ListasOvz.ListaOvz censo = lista("categoria_censo");
        assertThat(censo.provisional()).isTrue();
        assertThat(censo.valores()).isEmpty();
    }
}
