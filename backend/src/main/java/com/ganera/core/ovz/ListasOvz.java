package com.ganera.core.ovz;

import java.util.List;

/**
 * Listas de valores de OVZ, tomadas del mapa real de OVZnet/CAÑADA
 * ({@code docs/referencias/mapa-ovz.md}, leído el 2026-11-16 con una cuenta de ganadero, sin presentar
 * nada). Cada opción guarda el {@code texto} visible y el {@code valor} interno de OVZ.
 *
 * <p>Las razas se mapean <b>por valor</b>, no por texto: hay dos «DESCONOCIDA» (901 y 955) y dos casi
 * iguales, «PARDE DE MONTAÑA» [1088] y «PARDA DE MONTAÑA» [1322].</p>
 *
 * <p>La única lista que sigue {@code provisional=true} es la categoría del censo: el formulario de censo
 * no se abrió en el mapeo. El resto son definitivas.</p>
 */
public final class ListasOvz {

    private ListasOvz() {
    }

    /** Una opción de un desplegable de OVZ: lo que se ve y el valor interno con el que OVZ la identifica. */
    public record OpcionOvz(String texto, String valor) {
    }

    private static OpcionOvz o(String texto, String valor) {
        return new OpcionOvz(texto, valor);
    }

    /** Sexo (alta): `P_DDLSexo`. */
    public static final List<OpcionOvz> SEXO = List.of(
            o("Hembra", "H"), o("Macho", "M"));

    /** Tipo de identificador (alta): `P_DDLTipoIdentificador` (sin el placeholder [00]). */
    public static final List<OpcionOvz> TIPOS_IDENTIFICADOR = List.of(
            o("Crotal Electrónico", "04"));

    /** Aptitud del movimiento: `P_DDLAptitud`. */
    public static final List<OpcionOvz> APTITUDES_MOVIMIENTO = List.of(
            o("SACRIFICIO", "0"), o("VIDA", "1"), o("REPRODUCC", "2"), o("CEBO", "3"));

    /** Medio de transporte (movimiento): `P_DDLMedioTransporte`. */
    public static final List<OpcionOvz> MEDIOS_TRANSPORTE = List.of(
            o("Camión", "1"), o("Barco", "2"), o("Tren", "3"), o("Avión", "4"),
            o("Conducción a pie", "5"), o("Otros", "99"));

    /** Tipo de responsable del movimiento: `P_DDLResponsable`. */
    public static final List<OpcionOvz> TIPOS_RESPONSABLE = List.of(
            o("Desconocido", "0"), o("Titular de la Explotación", "1"),
            o("Tratante-Comerciante-Operador", "2"), o("Transportista", "3"), o("Otros", "99"));

    /** Emisión del DI (alta): `P_rblImpDI`. */
    public static final List<OpcionOvz> EMISIONES_DI = List.of(
            o("Prefiero que mis DI's sean emitidos en la OVZ", "OVZ"),
            o("Imprimiré los DI's desde la opción de animales sin DI", "OVZNET"));

    /** Envío o recogida del DI (alta): `P_DDLOVZ`. Incluye CORREO y las oficinas con su código. */
    public static final List<OpcionOvz> OFICINAS_DI = List.of(
            o("CORREO", "0"), o("AZUAGA", "601"), o("BADAJOZ", "602"), o("CACERES", "1001"),
            o("CASTUERA", "603"), o("CORIA", "1002"), o("DON BENITO", "604"),
            o("HERRERA DEL DUQUE", "605"), o("JEREZ DE LOS CABALLEROS", "606"), o("MERIDA", "607"),
            o("NAVALMORAL DE LA MATA", "1004"), o("PLASENCIA", "1005"), o("TRUJILLO", "1006"),
            o("VALENCIA DE ALCANTARA", "1007"), o("ZAFRA", "608"), o("ZORITA", "1003"));

    /** Comunidad Autónoma de destino (movimiento): `P_DDLCA`. */
    public static final List<OpcionOvz> COMUNIDADES_AUTONOMAS = List.of(
            o("C.A. de Andalucía", "1"), o("C.A. de Aragón", "2"), o("C.A. de Asturias", "3"),
            o("C.A. de Baleares", "4"), o("C.A. de Canarias", "5"), o("C.A. de Cantabria", "6"),
            o("C.A. de Castilla la Mancha", "7"), o("C.A. de Castilla y León", "8"),
            o("C.A. de Cataluña", "9"), o("C.A. de Extremadura", "10"), o("C.A. de Galicia", "11"),
            o("C.A. de Madrid", "12"), o("C.A. de Murcia", "13"), o("C.A. de Navarra", "14"),
            o("C.A. del País Vasco", "15"), o("C.A. de la Rioja", "16"), o("C.A. Valenciana", "17"),
            o("Ceuta", "18"), o("Melilla", "19"));

    /** Tipo de explotación de destino (movimiento): `P_DDLTipoExplotacion`. */
    public static final List<OpcionOvz> TIPOS_EXPLOTACION = List.of(
            o("GANADERA", "1"), o("FERIA O MERCADO PERMANENTE", "4"), o("MATADERO", "5"),
            o("PLAZA DE TOROS PERMANENTE", "6"), o("NUCLEO ZOOLOGICO", "7"),
            o("MATANZAS DOMICILIARIAS", "8"), o("CONCENTRACION EQUINA", "10"), o("PASTOS", "11"),
            o("TRATANTES (CON EXPLOTACIÓN)", "12"), o("FERIA O MERCADO NO PERMANENTE", "13"),
            o("PLAZA DE TOROS NO PERMANENTE", "14"),
            o("CENTRO DE TESTAJE, SELECCIÓN Y REPRODUCCIÓN ANIMAL", "15"),
            o("CENTRO DE AGRUPAMIENTO REPROD. PORCINOS DESVIEJE", "16"),
            o("CENTRO DE TIPIFICACION DE OVINO O BOVINO", "17"),
            o("OTROS CENTROS DE CONCENTRACION ANIMAL", "18"), o("CENTRO DE INSPECCIÓN", "19"),
            o("CENTRO DE CUARENTENA", "20"), o("PUNTO DE PARADA", "21"),
            o("OTRAS EXPLOTACIONES DE OCIO", "22"), o("CENTRO DE ENSEÑANZA", "23"),
            o("CENTROS DE ANIMALES DE EXPERIMENTACIÓN", "24"),
            o("EXPLOTACIÓN PARA LA PRÁCTICA ECUESTRE", "25"),
            o("CENTRO DE MUESTREO PARA LA LENGUA AZUL", "26"),
            o("ESTABLECIMIENTOS DE TRANSFORMACIÓN AUTORIZADOS", "27"),
            o("CENTROS DE DEPURACIÓN/EXPEDICIÓN O SIMILARES", "28"),
            o("ESPACIOS NATURALES ACOTADOS", "29"),
            o("DEPÓSITOS SEMENTALES EQUINOS Y CENTROS REPROD", "30"),
            o("CONCENTRACIONES ÉQUIDOS CARÁCTER LÚDICO/CULTURAL", "31"),
            o("EXPLOTACIONES NO COMERCIALES", "32"),
            o("CENTROS DE ACOGIDA/RECOGIDA DE ÉQUIDOS ABANDONADOS", "33"),
            o("EXPLOTACIONES DE CABALLOS SILVESTRES", "34"),
            o("CENTRO DE SACRIFICIO DE URGENCIA", "35"), o("ESPACIOS CINEGÉTICOS", "37"),
            o("CENTRO DE CONCENTRACIÓN DE LIDIA", "38"), o("EXPLOTACIÓN DE CABESTROS", "39"));

    /** Catálogo de razas (alta): `P_DDLRazas`. 134 razas; mapear por valor (dos «DESCONOCIDA»). */
    public static final List<OpcionOvz> RAZAS = List.of(
            o("ASTURIANA", "1"), o("RUBIA GALLEGA", "2"), o("SANTA GERTRUDIS", "3"),
            o("BLANCA CACEREÑA", "4"), o("LIDIA", "5"), o("CRUZADA", "6"), o("HEREFORD", "7"),
            o("FLECKVIEH", "8"), o("MORUCHA", "9"), o("LIMOUSINE", "11"), o("CHAROLAISE", "12"),
            o("AVILEÑA", "13"), o("RETINTA", "14"), o("FRISONA", "15"), o("OTRAS", "36"),
            o("BLONDE AQUITANI", "64"), o("BNEGRO", "65"), o("BCOLORADO", "66"), o("MONTPELLIER", "67"),
            o("SALERS", "68"), o("MERTOLENGA", "69"), o("PIRENAICA", "70"), o("ALISTANA-SANABRESA", "73"),
            o("BETIZU", "74"), o("BRUNA DE LOS PIRINEOS", "75"), o("LEVANTINA", "76"),
            o("MALLORQUINA", "77"), o("MONCHINA", "78"), o("MOSTRENCA", "79"), o("SERRANA NEGRA", "80"),
            o("PARDA", "81"), o("JERSEY", "82"), o("ANGUS", "83"), o("ROJA DANESA", "84"),
            o("ASTURIANA DEL VALLE", "85"), o("MRY", "86"), o("NORMANDA", "87"), o("TUDANCA", "88"),
            o("CARDENA ANDALUZA", "89"), o("MURCIANA", "90"), o("CANARIA", "91"), o("PALMERA", "92"),
            o("MENORQUINA", "93"), o("NEGRA ANDALUZA", "94"), o("PAJUNA", "95"), o("BLANCA BELGA", "96"),
            o("PIAMONTESA", "97"), o("CACHENA", "98"), o("CALDELANA", "99"), o("FRIEIRESA", "100"),
            o("LIMIANA", "101"), o("VIANESA", "102"), o("SAYAGUESA", "103"), o("TERREÑA", "104"),
            o("AYRSHIRE", "105"), o("BUFALO", "106"), o("CHIANINA", "107"), o("GUERNSEY", "108"),
            o("MONTBELIARD", "109"), o("BISONTE", "110"), o("BRAHMAN", "111"), o("CEBU", "112"),
            o("HOLSTEIN", "113"), o("SHORTHORN", "114"), o("SIMMENTAL", "115"), o("ANGLER", "116"),
            o("BORDELAISE", "117"), o("GALLOWAY", "118"), o("BRETTONNE PIE NOIRE", "119"),
            o("CASTA (AURE Y ST. GIRONS)", "120"), o("ARMORICAINE", "121"),
            o("AUROCHS RECONSTITUE", "122"), o("BAZADAISE", "123"), o("BEARNAISE", "124"),
            o("BLUE DU NORD", "125"), o("COOPELSO", "126"), o("CORSE", "127"), o("CREOLE", "128"),
            o("FERRANDAISE", "129"), o("FROMENT DU LEON", "130"), o("GELBVIEH", "131"),
            o("HERENS", "132"), o("INRA", "133"), o("LOURDAISE", "134"), o("MARAICHINE", "135"),
            o("MARCHIGIANA", "136"), o("MIRANDAISE", "137"), o("N. DAMA", "138"), o("NANTAISE", "139"),
            o("PIE ROUGE DES PLAINES", "140"), o("CAMARGUE", "141"), o("ROUGE FLAMANDE", "142"),
            o("SAOSNOISE", "143"), o("TARENTAISE", "144"), o("VILLARD DE LANS", "145"),
            o("VOSGIENNE", "146"), o("ABONDANCE", "147"), o("ROTBUNTE", "148"), o("SOUTH DEVON", "149"),
            o("IRISH MAOL / DROIMEANN", "151"), o("MAINE ANJOU", "152"), o("PARTENAISE", "153"),
            o("DEXTER", "154"), o("AUBRAC", "155"), o("HIGHLAND", "156"), o("KERRY", "157"),
            o("MURRAY GREY", "158"), o("ROMAGNOLA", "159"), o("PASIEGA", "160"),
            o("SERRANA DE TERUEL", "161"), o("DESCONOCIDA", "901"), o("DESCONOCIDA", "955"),
            o("ALBERA", "956"), o("MURCIANA-LEVANTINA", "957"), o("PINZGAUER", "958"),
            o("BALTATA ROMANESCA", "959"), o("ROJA LETONA", "960"), o("ALENTEJANA", "1015"),
            o("PARDE DE MONTAÑA", "1088"), o("WAGYU", "1089"), o("WATUSI", "1090"), o("KOBE", "1091"),
            o("SWEDISH RED AND WHITE", "1092"), o("AROUQUESA", "1093"), o("PRETA", "1114"),
            o("Valdostana", "1119"), o("Vaca de la reina", "1125"), o("Gascona del Pirineo", "1204"),
            o("Barrosa", "1212"), o("Dansk Holstein", "1217"), o("Prim Holstein", "1256"),
            o("Mantequera leonesa", "1319"), o("Braunvieh", "1321"), o("PARDA DE MONTAÑA", "1322"));

    public record ListaOvz(String id, String etiqueta, boolean provisional, List<OpcionOvz> valores) {
    }

    public record ListasOvzResponse(List<ListaOvz> listas) {
    }

    public static ListasOvzResponse todas() {
        return new ListasOvzResponse(List.of(
                new ListaOvz("sexo", "Sexo", false, SEXO),
                new ListaOvz("raza", "Raza", false, RAZAS),
                new ListaOvz("tipo_identificador", "Tipo Identificador", false, TIPOS_IDENTIFICADOR),
                new ListaOvz("emision_di", "Emisión del DI", false, EMISIONES_DI),
                new ListaOvz("oficina_di", "Envío o recogida del DI", false, OFICINAS_DI),
                new ListaOvz("aptitud_movimiento", "Aptitud del Movimiento", false, APTITUDES_MOVIMIENTO),
                new ListaOvz("medio_transporte", "Medio de Transporte", false, MEDIOS_TRANSPORTE),
                new ListaOvz("tipo_responsable", "Tipo Responsable del Movimiento", false, TIPOS_RESPONSABLE),
                new ListaOvz("comunidad_autonoma", "Comunidad Autónoma", false, COMUNIDADES_AUTONOMAS),
                new ListaOvz("tipo_explotacion", "Tipo Explotación", false, TIPOS_EXPLOTACION),
                new ListaOvz("categoria_censo", "Categoría del censo", true, List.of())));
    }
}
