package com.ganera.core.tramite;

import com.ganera.core.tramite.ExtraccionValidador.ExtraccionValidada;
import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluacion del prompt de SIN_TRAMITE contra Haiku REAL (A3 del plan SIN_TRAMITE). Desactivada por
 * defecto: solo corre con {@code GANERA_EVAL_IA=true} y {@code ANTHROPIC_API_KEY} no vacia en el
 * entorno, asi que {@code ./mvnw clean test} normal (y cualquier CI) la omite sin llamar a la API.
 *
 * <p>Solo mensajes inventados. La clave se lee del entorno y se pasa al servicio; nunca se imprime ni
 * aparece en un mensaje de asercion. Cada mensaje se manda 3 veces, en orden.
 *
 * <p>Informe por consola y en {@code target/eval-sin-tramite.md} (no versionado): mensaje x pasada ->
 * tipo crudo de la IA (SIN_TRAMITE, NO_IDENTIFICADO, el tipo, o FALLO), crotales crudos y lo que
 * quedaria tras la red de seguridad D2 de {@link ExtraccionValidador}.
 *
 * <p>Requisito: ninguno de los mensajes que piden algo devuelve SIN_TRAMITE crudo en ninguna pasada.
 * Los de charla solo se informan.
 */
class ExtraccionSinTramiteEvalTest {

    private static final int PASADAS = 3;

    /** Deberian salir SIN_TRAMITE (deseable, no exigido). */
    private static final List<String> CHARLA = List.of(
            "vale, gracias",
            "ok",
            "recibido 👍",
            "¿abrís el lunes?",
            "¿me mandáis la factura?",
            "buenos días");

    /** Nunca pueden salir SIN_TRAMITE (requisito, en las 3 pasadas). */
    private static final List<String> PIDEN_ALGO = List.of(
            "gracias, y se me ha muerto la 4521",
            "vale, pues la 0123 sale mañana al matadero",
            "ha nacido un ternero",
            "ok, apunta el censo",
            "gracias, ¿qué hago con la vaca que murió ayer?",
            "la 7788",
            "mañana vienen dos novillas de Pepe");

    /** {@code causa}: solo en un FALLO, el mensaje de ExtraccionFallidaException (nunca lleva la clave ni la
     * respuesta del modelo: ver AnthropicTramiteExtractionService). */
    private record Fila(String grupo, String mensaje, int pasada, String crudo, List<String> crotales, String trasRed,
                        String causa) {
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GANERA_EVAL_IA", matches = "true")
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".*\\S.*")
    void ningunMensajeQuePideAlgoSaleSinTramite() throws IOException {
        AnthropicTramiteExtractionService servicio =
                new AnthropicTramiteExtractionService(System.getenv("ANTHROPIC_API_KEY"), "", 2);
        List<Fila> filas = new ArrayList<>();
        try {
            for (String mensaje : CHARLA) {
                evaluar(servicio, "charla", mensaje, filas);
            }
            for (String mensaje : PIDEN_ALGO) {
                evaluar(servicio, "pide algo", mensaje, filas);
            }
        } finally {
            servicio.cerrar();
        }

        String informe = informe(filas);
        System.out.println(informe);
        Path destino = Path.of("target", "eval-sin-tramite.md");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, informe, StandardCharsets.UTF_8);

        // Primero los fallos: con todo en FALLO (sin clave, clave mala, sin red) no hay ningún «falso SIN_TRAMITE»
        // y la evaluación saldría en verde sin haber medido nada.
        List<String> fallidas = filas.stream()
                .filter(f -> f.crudo().equals("FALLO"))
                .map(f -> "«" + f.mensaje() + "» pasada " + f.pasada() + ": " + f.causa())
                .toList();
        assertThat(fallidas)
                .as("Extracciones fallidas: la evaluación no ha medido nada en esas filas (ver target/eval-sin-tramite.md)")
                .isEmpty();

        List<String> falsosSinTramite = filas.stream()
                .filter(f -> f.grupo().equals("pide algo") && f.crudo().equals("SIN_TRAMITE"))
                .map(f -> "«" + f.mensaje() + "» pasada " + f.pasada())
                .toList();
        assertThat(falsosSinTramite)
                .as("Mensajes que piden algo y la IA ha devuelto SIN_TRAMITE (ver target/eval-sin-tramite.md)")
                .isEmpty();
    }

    private static void evaluar(AnthropicTramiteExtractionService servicio, String grupo, String mensaje, List<Fila> filas) {
        for (int pasada = 1; pasada <= PASADAS; pasada++) {
            TramiteExtraido extraido;
            try {
                extraido = servicio.extraer(mensaje);
            } catch (ExtraccionFallidaException e) {
                filas.add(new Fila(grupo, mensaje, pasada, "FALLO", List.of(), "FALLO", e.getMessage()));
                continue;
            } catch (RuntimeException e) {
                // Cualquier otra excepcion tambien es un FALLO con informe (revision de T1, m4). Solo el nombre de la
                // clase: su mensaje no esta revisado y podria llevar cualquier cosa.
                filas.add(new Fila(grupo, mensaje, pasada, "FALLO", List.of(), "FALLO", e.getClass().getName()));
                continue;
            }
            ExtraccionValidada validada = ExtraccionValidador.validar(extraido);
            filas.add(new Fila(grupo, mensaje, pasada, tipo(extraido.tipoTramite(), extraido.sinTramite()),
                    extraido.crotales(), tipo(validada.tipo(), validada.sinTramite()), ""));
        }
    }

    private static String tipo(TipoTramite tipo, boolean sinTramite) {
        if (sinTramite) {
            return "SIN_TRAMITE";
        }
        return tipo == null ? "NO_IDENTIFICADO" : tipo.name();
    }

    private static String informe(List<Fila> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluación SIN_TRAMITE (").append(AnthropicTramiteExtractionService.MODELO).append(")\n\n");
        sb.append("| Grupo | Mensaje | Pasada | Tipo (crudo IA) | Crotales (crudos) | Tras red de seguridad D2 | Causa del fallo |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (Fila f : filas) {
            sb.append("| ").append(f.grupo())
                    .append(" | ").append(f.mensaje().replace("|", "\\|"))
                    .append(" | ").append(f.pasada())
                    .append(" | ").append(f.crudo())
                    .append(" | ").append(String.join(", ", f.crotales()).replace("|", "\\|"))
                    .append(" | ").append(f.trasRed())
                    .append(" | ").append(f.causa() == null ? "" : f.causa().replace("|", "\\|"))
                    .append(" |\n");
        }
        long falsos = filas.stream().filter(f -> f.grupo().equals("pide algo") && f.crudo().equals("SIN_TRAMITE")).count();
        long charlaAcertada = filas.stream().filter(f -> f.grupo().equals("charla") && f.crudo().equals("SIN_TRAMITE")).count();
        long charlaTotal = filas.stream().filter(f -> f.grupo().equals("charla")).count();
        long fallos = filas.stream().filter(f -> f.crudo().equals("FALLO")).count();
        sb.append("\nFalsos SIN_TRAMITE en mensajes que piden algo (requisito: 0): ").append(falsos).append('\n');
        sb.append("Charla reconocida como SIN_TRAMITE (deseable): ").append(charlaAcertada).append('/').append(charlaTotal).append('\n');
        sb.append("Extracciones fallidas: ").append(fallos).append('\n');
        return sb.toString();
    }
}
