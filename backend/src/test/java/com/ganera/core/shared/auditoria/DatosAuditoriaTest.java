package com.ganera.core.shared.auditoria;

import com.ganera.core.tramite.EstadoTramite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que cabe en antes / despues (plan 2026-10-21, D8 y A3): ids, codigos, recuentos, booleanos, instantes y listas
 * de ids. Nunca un String: lo impide el tipo (y la reflexion de abajo lo vigila).
 */
class DatosAuditoriaTest {

    /** Enum de prueba con un nombre de 40 caracteres (el maximo admitido) y otro de 41. */
    enum CodigoDePrueba {
        CODIGO_DE_CUARENTA_CARACTERES_EXACTOS_XX,
        CODIGO_DE_CUARENTA_Y_UN_CARACTERES_EXACTO
    }

    @Test
    void elJsonEsCanonicoConLasClavesOrdenadasEnMinusculas() {
        DatosAuditoria datos = DatosAuditoria.con(Clave.VERSION, 3).y(Clave.ESTADO, EstadoTramite.APROBADO)
                .y(Clave.EXPLOTACION_ID, 42L);
        DatosAuditoria enOtroOrden = DatosAuditoria.con(Clave.EXPLOTACION_ID, 42L)
                .y(Clave.ESTADO, EstadoTramite.APROBADO).y(Clave.VERSION, 3);

        assertThat(datos.json()).isEqualTo("{\"estado\":\"APROBADO\",\"explotacion_id\":42,\"version\":3}");
        assertThat(enOtroOrden.json()).isEqualTo(datos.json());
    }

    @Test
    void cadaTipoPermitidoSeEscribeConSuFormaJson() {
        DatosAuditoria datos = DatosAuditoria.con(Clave.EXPLOTACION_ID, Long.MAX_VALUE)
                .y(Clave.NUM_CROTALES, 7)
                .y(Clave.ACTIVO, false)
                .y(Clave.ESTADO, EstadoTramite.PENDIENTE_REVISION)
                .y(Clave.LIMITE, Instant.parse("2031-02-08T09:00:00.123456Z"))
                .y(Clave.ANIMAL_IDS, List.of(3L, 1L, 2L))
                .y(Clave.TIPO_TRAMITE, (Enum<?>) null);

        assertThat(datos.json()).isEqualTo("{\"activo\":false,\"animal_ids\":[3,1,2],\"estado\":\"PENDIENTE_REVISION\","
                + "\"explotacion_id\":9223372036854775807,\"limite\":\"2031-02-08T09:00:00.123456Z\",\"num_crotales\":7,"
                + "\"tipo_tramite\":null}");
    }

    @Test
    void unInstanteSiempreVaEnUtcConZ() {
        assertThat(DatosAuditoria.con(Clave.LIMITE, Instant.parse("2030-03-10T09:00:00Z")).json())
                .isEqualTo("{\"limite\":\"2030-03-10T09:00:00Z\"}");
    }

    @Test
    void unaListaDeMasDe50IdsSeRecortaA50YMarcaTruncado() {
        List<Long> cincuentaYUno = LongStream.rangeClosed(1, 51).boxed().toList();

        String json = DatosAuditoria.con(Clave.ANIMAL_IDS, cincuentaYUno).json();

        String cincuenta = String.join(",", LongStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList());
        assertThat(json).isEqualTo("{\"animal_ids\":[" + cincuenta + "],\"truncado\":true}");
        // Con 50 justos no se marca nada.
        assertThat(DatosAuditoria.con(Clave.ANIMAL_IDS, cincuentaYUno.subList(0, 50)).json()).doesNotContain("truncado");
        // El tope es para el total de elementos del objeto, no por lista.
        String dosListas = DatosAuditoria.con(Clave.ANIMAL_IDS, cincuentaYUno.subList(0, 40))
                .y(Clave.EXPLOTACION_ID, List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L))
                .json();
        assertThat(dosListas).contains("\"explotacion_id\":[100,101,102,103,104,105,106,107,108,109]")
                .contains("\"truncado\":true");
    }

    /** Revision de T1, menor 4: una lista de CODIGOS (p. ej. los campos cambiados de un contacto), solo enums. */
    @Test
    void unaListaDeCodigosSeGuardaConSusNombresEnElOrdenDado() {
        DatosAuditoria datos = DatosAuditoria.conCodigos(Clave.CAMPOS_CAMBIADOS,
                        List.of(EstadoTramite.RECHAZADO, EstadoTramite.APROBADO))
                .yCodigos(Clave.RESULTADO, List.of())
                .yCodigos(Clave.ESTADO, null)
                .y(Clave.VERSION, 2);

        assertThat(datos.json()).isEqualTo(
                "{\"campos_cambiados\":[\"RECHAZADO\",\"APROBADO\"],\"estado\":null,\"resultado\":[],\"version\":2}");
        assertThatThrownBy(() -> DatosAuditoria.conCodigos(Clave.CAMPOS_CAMBIADOS,
                Arrays.asList(EstadoTramite.APROBADO, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatosAuditoria.conCodigos(Clave.CAMPOS_CAMBIADOS,
                List.of(CodigoDePrueba.CODIGO_DE_CUARENTA_Y_UN_CARACTERES_EXACTO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> datos.yCodigos(Clave.CAMPOS_CAMBIADOS, List.of(EstadoTramite.APROBADO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losCodigosCompartenElTopeDe50ConLosIdsYMarcanTruncado() {
        List<EstadoTramite> cuarenta = java.util.Collections.nCopies(40, EstadoTramite.APROBADO);
        String json = DatosAuditoria.con(Clave.ANIMAL_IDS, LongStream.rangeClosed(1, 20).boxed().toList())
                .yCodigos(Clave.CAMPOS_CAMBIADOS, cuarenta).json();

        assertThat(json).contains("\"truncado\":true");
        assertThat(json.split("\"APROBADO\"", -1)).hasSize(31); // solo caben 30 codigos tras los 20 ids
    }

    @Test
    void unaClaveRepetidaOProhibidaEsUnaExcepcion() {
        DatosAuditoria datos = DatosAuditoria.con(Clave.ESTADO, EstadoTramite.APROBADO);

        assertThatThrownBy(() -> datos.y(Clave.ESTADO, EstadoTramite.RECHAZADO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ESTADO");
        assertThatThrownBy(() -> datos.y(Clave.TRUNCADO, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatosAuditoria.con(Clave.ANIMAL_IDS, Arrays.asList(1L, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatosAuditoria.con(Clave.MOTIVO, CodigoDePrueba.CODIGO_DE_CUARENTA_Y_UN_CARACTERES_EXACTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatosAuditoria.con(null, 1L)).isInstanceOf(NullPointerException.class);
        // Inmutable: el intento fallido no ha cambiado nada.
        assertThat(datos.json()).isEqualTo("{\"estado\":\"APROBADO\"}");
        DatosAuditoria con2 = datos.y(Clave.VERSION, 2);
        assertThat(datos.json()).isEqualTo("{\"estado\":\"APROBADO\"}");
        assertThat(con2.json()).isEqualTo("{\"estado\":\"APROBADO\",\"version\":2}");
    }

    @Test
    void masDe20ClavesEsUnaExcepcion() {
        List<Clave> claves = Arrays.stream(Clave.values()).filter(c -> c != Clave.TRUNCADO).toList();
        assertThat(claves.size()).isGreaterThan(DatosAuditoria.MAXIMO_CLAVES);
        DatosAuditoria datos = DatosAuditoria.con(claves.get(0), 1L);
        for (int i = 1; i < DatosAuditoria.MAXIMO_CLAVES; i++) {
            datos = datos.y(claves.get(i), 1L);
        }
        DatosAuditoria lleno = datos;
        assertThatThrownBy(() -> lleno.y(claves.get(DatosAuditoria.MAXIMO_CLAVES), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Peor caso: 20 claves (las de nombre mas largo), 19 con un codigo de 40 caracteres y una con 51 ids de 20 cifras
     * (se recortan a 50 y se marca truncado). Cabe en VARCHAR(4000); y la cota calculada tambien, para cualquier
     * combinacion (claves de hasta 30 caracteres, que vigila este mismo test).
     */
    @Test
    void elPeorCasoCabeEn4000() {
        assertThat(Clave.values()).allSatisfy(c -> assertThat(c.json().length()).as(c.name()).isLessThanOrEqualTo(30));
        List<Clave> masLargas = new ArrayList<>(Arrays.stream(Clave.values()).filter(c -> c != Clave.TRUNCADO).toList());
        masLargas.sort(Comparator.comparingInt((Clave c) -> c.json().length()).reversed());
        List<Long> ids = LongStream.range(0, 51).map(i -> Long.MIN_VALUE + i).boxed().toList();
        List<CodigoDePrueba> codigos = java.util.Collections.nCopies(51,
                CodigoDePrueba.CODIGO_DE_CUARENTA_CARACTERES_EXACTOS_XX);

        DatosAuditoria conIds = DatosAuditoria.con(masLargas.get(0), ids);
        // La lista de codigos de 40 es el elemento mas largo posible: el peor caso de verdad.
        DatosAuditoria conCodigos = DatosAuditoria.conCodigos(masLargas.get(0), codigos);
        for (int i = 1; i < DatosAuditoria.MAXIMO_CLAVES; i++) {
            conIds = conIds.y(masLargas.get(i), CodigoDePrueba.CODIGO_DE_CUARENTA_CARACTERES_EXACTOS_XX);
            conCodigos = conCodigos.y(masLargas.get(i), CodigoDePrueba.CODIGO_DE_CUARENTA_CARACTERES_EXACTOS_XX);
        }

        assertThat(conIds.json()).contains("\"truncado\":true").hasSizeLessThanOrEqualTo(4000);
        assertThat(conCodigos.json()).contains("\"truncado\":true").hasSizeLessThanOrEqualTo(4000);
        // Cota: {} + por clave "clave":valor, (30 + 2 + 1 + 1) + el valor escalar mas largo (codigo de 40 + 2 comillas;
        // un Instant extremo mide 37 + 2), + 50 elementos de lista de hasta 43 (codigo de 40 con comillas y coma; un id
        // ocupa 21) y los corchetes, + "truncado":true.
        int cota = 2 + (DatosAuditoria.MAXIMO_CLAVES + 1) * (30 + 4 + 42) + DatosAuditoria.MAXIMO_ELEMENTOS * 43 + 2;
        assertThat(cota).isLessThanOrEqualTo(4000);
        assertThat(DatosAuditoria.con(Clave.LIMITE, Instant.MAX).json()).hasSizeLessThanOrEqualTo(2 + 34 + 42);
    }

    /**
     * Ningun metodo publico ni constructor acepta String, Object, CharSequence, Map ni un generico sin acotar: solo
     * Clave, Long, Integer, Boolean, Enum, Instant y List&lt;Long&gt;. Ni siquiera un equals/toString propios.
     */
    @Test
    void ningunaPuertaPublicaAceptaTextoNiCualquierCosa() {
        Set<Class<?>> permitidos = Set.of(Clave.class, Long.class, Integer.class, Boolean.class, Enum.class,
                Instant.class, List.class);
        List<Executable> publicos = Stream.concat(
                        Arrays.stream(DatosAuditoria.class.getDeclaredMethods()),
                        Arrays.stream(DatosAuditoria.class.getDeclaredConstructors()))
                .filter(e -> Modifier.isPublic(e.getModifiers()))
                .toList();
        assertThat(publicos).extracting(Executable::getName).contains("con", "y", "conCodigos", "yCodigos");
        assertThat(DatosAuditoria.class.getDeclaredConstructors())
                .allSatisfy(c -> assertThat(Modifier.isPublic(c.getModifiers())).as(c.toString()).isFalse());

        for (Executable publico : publicos) {
            assertThat(publico.getTypeParameters()).as(publico.toString()).isEmpty();
            for (Type tipo : publico.getGenericParameterTypes()) {
                Class<?> crudo = tipo instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) tipo;
                assertThat(permitidos).as(publico + " -> " + tipo).contains(crudo);
                for (Class<?> prohibido : List.of(String.class, Object.class, CharSequence.class, Map.class)) {
                    assertThat(crudo).as(publico.toString()).isNotEqualTo(prohibido);
                }
                if (crudo == List.class) {
                    // List<Long> (ids) o List<? extends Enum<?>> (codigos, solo en conCodigos/yCodigos); nada mas.
                    Type[] argumentos = ((ParameterizedType) tipo).getActualTypeArguments();
                    assertThat(argumentos).as(publico.toString()).hasSize(1);
                    if (publico.getName().endsWith("Codigos")) {
                        assertThat(argumentos[0]).as(publico.toString()).isInstanceOf(WildcardType.class);
                        WildcardType comodin = (WildcardType) argumentos[0];
                        assertThat(comodin.getLowerBounds()).as(publico.toString()).isEmpty();
                        assertThat(comodin.getUpperBounds()).as(publico.toString()).hasSize(1);
                        Type cota = comodin.getUpperBounds()[0];
                        assertThat(cota).as(publico.toString()).isInstanceOf(ParameterizedType.class);
                        assertThat(((ParameterizedType) cota).getRawType()).as(publico.toString()).isEqualTo(Enum.class);
                    } else {
                        assertThat(argumentos).as(publico.toString()).containsExactly(Long.class);
                    }
                }
                if (crudo == Enum.class) {
                    Type argumento = ((ParameterizedType) tipo).getActualTypeArguments()[0];
                    assertThat(argumento).isInstanceOf(WildcardType.class);
                }
            }
        }
        for (Method metodo : DatosAuditoria.class.getDeclaredMethods()) {
            if (Modifier.isPublic(metodo.getModifiers())) {
                assertThat(metodo.getName()).as(metodo.toString()).isIn("con", "y", "conCodigos", "yCodigos");
            }
        }
        for (Constructor<?> constructor : DatosAuditoria.class.getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes()).doesNotContain(String.class, Object.class, Map.class);
        }
    }
}
