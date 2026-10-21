package com.ganera.core.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.whatsapp.RetencionMensajesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Migracion a Supabase, T3: la base de datos del perfil clever contra el PostgreSQL embebido preparado como Supabase
 * (script 01, roles {@code ganera_app}/{@code ganera_propietario}, RLS por el callback, SSL con el certificado de
 * prueba). Fail-closed al arrancar (cada variable obligatoria, {@code verify-full} de verdad, el esquema que no crea
 * Flyway) y un flujo real por HTTP como {@code ganera_app} con RLS activa.
 */
class BaseDeDatosPerfilCleverPostgresTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @ParameterizedTest
    @ValueSource(strings = {"SUPABASE_DB_HOST", "SUPABASE_DB_PORT", "SUPABASE_DB_NAME", "SUPABASE_DB_USER",
            "SUPABASE_DB_PASSWORD", "SUPABASE_FLYWAY_USER", "SUPABASE_FLYWAY_PASSWORD"})
    void sinCadaVariableLaAppNoArranca(String variable) {
        // Revision M3: si la shell tiene esa variable (la terminal del smoke de T5 la exporta), la fuente del entorno la
        // aportaria aunque el test la quite de los argumentos. Entonces el caso no se puede probar aqui: se omite.
        assumeTrue(System.getenv(variable) == null, variable + " esta definida en el entorno real");
        Map<String, String> variables = PostgresEmbebido.variablesSupabase(PostgresEmbebido.nuevaBaseDeDatos());
        assertThat(variables.remove(variable)).isNotNull();

        // Falla al preparar el Environment (ConexionBaseDeDatosEstricta), antes de crear ningun bean: no ha llegado a
        // intentar conectar. Sin eso, con las contrasenas el embebido (autenticacion trust) aceptaria cualquier valor.
        assertThatThrownBy(() -> PostgresEmbebido.arrancarPerfilClever(variables,
                "--FRONTEND_ORIGEN=https://app.ganera.es").close())
                .hasStackTraceContaining("Could not resolve placeholder '" + variable + "'")
                .satisfies(e -> assertThat(trazaCompleta(e)).doesNotContain("BeanCreationException"));
    }

    /**
     * Todas las peticiones piden JSON, como el frontend: Twilio trae jackson-dataformat-xml y, con {@code String.class},
     * TestRestTemplate manda un Accept que admite XML y el servidor contestaria en XML.
     */
    private static HttpHeaders cabeceras() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Revision I1: definida pero vacia es lo mismo que si faltara, tambien dentro de la URL (con el host vacio pgjdbc
     * conectaria a localhost; con la base vacia, a la que se llama como el usuario). Falla antes de crear ningun bean.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SUPABASE_DB_HOST", "SUPABASE_DB_PORT", "SUPABASE_DB_NAME", "SUPABASE_DB_USER",
            "SUPABASE_DB_PASSWORD", "SUPABASE_FLYWAY_USER", "SUPABASE_FLYWAY_PASSWORD"})
    void conCadaVariableVaciaLaAppNoArranca(String variable) {
        // Como arriba (M3): una variable real del entorno no cambia este caso (los argumentos ganan), pero se omite
        // igual para que los dos parametrizados se comporten igual en la terminal del smoke.
        assumeTrue(System.getenv(variable) == null, variable + " esta definida en el entorno real");
        Map<String, String> variables = PostgresEmbebido.variablesSupabase(PostgresEmbebido.nuevaBaseDeDatos());
        assertThat(variables.put(variable, "")).isNotNull();

        assertThatThrownBy(() -> PostgresEmbebido.arrancarPerfilClever(variables,
                "--FRONTEND_ORIGEN=https://app.ganera.es").close())
                .hasStackTraceContaining(variable)
                .hasStackTraceContaining("en blanco")
                .satisfies(e -> assertThat(trazaCompleta(e)).doesNotContain("BeanCreationException"));
    }

    private static String trazaCompleta(Throwable e) {
        java.io.StringWriter traza = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(traza));
        return traza.toString();
    }

    /**
     * El certificado del servidor solo vale para {@code localhost}: con {@code 127.0.0.1} (el mismo servidor, la misma
     * CA) {@code verify-full} rechaza el nombre. Con {@code sslmode=require} conectaria.
     */
    @Test
    void conUnHostQueNoCasaConElCertificadoLaAppNoArranca() {
        Map<String, String> variables =
                PostgresEmbebido.variablesSupabase(PostgresEmbebido.nuevaBaseDeDatos(), "127.0.0.1");

        assertThatThrownBy(() -> PostgresEmbebido.arrancarPerfilClever(variables,
                "--FRONTEND_ORIGEN=https://app.ganera.es").close())
                .hasStackTraceContaining("The hostname 127.0.0.1 could not be verified");
    }

    /** El esquema lo crea el script 01, no Flyway ({@code create-schemas: false}): sin el, la app no arranca. */
    @Test
    void sinElEsquemaGaneraLaAppNoArranca() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        new JdbcTemplate(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos))
                .execute("DROP SCHEMA ganera CASCADE");

        assertThatThrownBy(() -> PostgresEmbebido.arrancarPerfilClever(baseDeDatos).close())
                .hasStackTraceContaining("ganera");
        // Y no lo ha creado nadie.
        assertThat(new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos)).queryForObject(
                "select count(*) from pg_namespace where nspname = 'ganera'", Long.class)).isZero();
    }

    /**
     * Flujo real por HTTP con la app conectada como {@code ganera_app} (RLS activa, sin ser duena de nada): alta,
     * login, importar el Excel de prueba (filas en transacciones REQUIRES_NEW), buscar, aprobar un tramite (pasa por
     * {@code SELECT ... FOR UPDATE}) y la retencion (DELETE y UPDATE en bloque).
     */
    @Test
    void elFlujoRealFuncionaComoGaneraAppConRlsActiva() throws Exception {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        ObjectMapper json = new ObjectMapper();

        try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarPerfilClever(baseDeDatos)) {
            JdbcTemplate app = contexto.getBean(JdbcTemplate.class);
            assertThat(app.queryForObject("select current_user", String.class)).isEqualTo(PostgresEmbebido.APP);
            JdbcTemplate superusuario = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
            assertThat(superusuario.queryForList("select relname from pg_class c join pg_namespace n "
                    + "on n.oid = c.relnamespace where n.nspname = 'ganera' and c.relkind = 'r' "
                    + "and not c.relrowsecurity", String.class)).as("tablas sin RLS").isEmpty();

            TestRestTemplate http = new TestRestTemplate(new RestTemplateBuilder().rootUri(
                    "http://localhost:" + contexto.getEnvironment().getProperty("local.server.port")));

            // Alta (onboarding interno) y login.
            HttpHeaders secreto = cabeceras();
            secreto.setContentType(MediaType.APPLICATION_JSON);
            secreto.set("X-Internal-Secret", SECRETO_ONBOARDING);
            ResponseEntity<String> alta = http.postForEntity("/internal/onboarding/gestoria", new HttpEntity<>("""
                    {"nombreGestoria":"Gestoria Supabase","emailUsuario":"supabase@gestoria.com",
                     "passwordUsuario":"password123","nombreUsuario":"Empleada"}""", secreto), String.class);
            assertThat(alta.getStatusCode().value()).isEqualTo(200);
            long gestoriaId = json.readTree(alta.getBody()).get("gestoriaId").asLong();

            HttpHeaders jsonHeaders = cabeceras();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> login = http.postForEntity("/auth/login", new HttpEntity<>(
                    "{\"email\":\"supabase@gestoria.com\",\"password\":\"password123\"}", jsonHeaders), String.class);
            assertThat(login.getStatusCode().value()).isEqualTo(200);
            String token = json.readTree(login.getBody()).get("token").asText();

            // Importar el Excel de prueba (2 explotaciones, 2 animales validos y 2 filas con error).
            HttpHeaders multipart = cabeceras();
            multipart.setBearerAuth(token);
            multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
            cuerpo.add("archivo", new ClassPathResource("inventario-prueba.xlsx"));
            ResponseEntity<String> importacion = http.postForEntity("/explotaciones/importar",
                    new HttpEntity<>(cuerpo, multipart), String.class);
            assertThat(importacion.getStatusCode().value()).isEqualTo(200);
            assertThat(superusuario.queryForObject("select count(*) from ganera.explotacion", Long.class))
                    .isEqualTo(2);
            assertThat(superusuario.queryForObject("select count(*) from ganera.animal", Long.class)).isEqualTo(2);

            // Buscar.
            JsonNode busqueda = json.readTree(get(http, "/explotaciones?q=alameda", token).getBody());
            assertThat(busqueda.get("totalElements").asLong()).isEqualTo(1);
            JsonNode explotacion = busqueda.get("content").get(0);
            assertThat(explotacion.get("codigoRega").asText()).isEqualTo("ES120000000001");
            long explotacionId = explotacion.get("id").asLong();

            // Un tramite listo para aprobar y dos mensajes sin tramite (uno antiguo, uno de hoy), sembrados con SQL
            // como el propietario (asi se siembra en Supabase), con created_at explicito.
            JdbcTemplate propietario =
                    new JdbcTemplate(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos));
            Long contactoId = propietario.queryForObject("insert into ganera.contacto "
                    + "(telefono, nombre, gestoria_id, created_at) values ('+34600000001', 'Contacto Supabase', ?, "
                    + "now() at time zone 'UTC') returning id", Long.class, gestoriaId);
            Long tramiteId = propietario.queryForObject("insert into ganera.tramite "
                    + "(gestoria_id, contacto_id, explotacion_id, tipo_tramite, estado, created_at) "
                    + "values (?, ?, ?, 'BAJA_MUERTE', 'PENDIENTE_REVISION', now() at time zone 'UTC') returning id",
                    Long.class, gestoriaId, contactoId, explotacionId);
            propietario.update("insert into ganera.mensaje_campo (message_sid, telefono_origen, cuerpo, resultado, "
                    + "created_at) values ('SM_antiguo', '+34600000009', 'hola', 'NUMERO_DESCONOCIDO', "
                    + "timestamp '2020-01-01 10:00:00')");
            propietario.update("insert into ganera.mensaje_campo (message_sid, telefono_origen, cuerpo, resultado, "
                    + "created_at) values ('SM_reciente', '+34600000009', 'hola', 'NUMERO_DESCONOCIDO', "
                    + "now() at time zone 'UTC')");

            // Aprobar (SELECT ... FOR UPDATE como ganera_app).
            HttpHeaders aprobar = cabeceras();
            aprobar.setBearerAuth(token);
            aprobar.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> aprobado = http.postForEntity("/tramites/" + tramiteId + "/aprobar",
                    new HttpEntity<>("{\"version\":0}", aprobar), String.class);
            assertThat(aprobado.getStatusCode().value()).as(aprobado.getBody()).isEqualTo(200);
            assertThat(json.readTree(aprobado.getBody()).get("estado").asText()).isEqualTo("APROBADO");
            assertThat(superusuario.queryForObject("select estado from ganera.tramite where id = ?", String.class,
                    tramiteId)).isEqualTo("APROBADO");

            // Retencion: borra el antiguo, deja el de hoy, y ninguna sentencia falla por permisos (-1).
            RetencionMensajesService.Resultado resultado = contexto.getBean(RetencionMensajesService.class).aplicar();
            assertThat(resultado.borrados()).isEqualTo(1);
            assertThat(resultado.vaciados()).isZero();
            assertThat(resultado.respuestasVaciadas()).isZero();
            assertThat(superusuario.queryForList("select message_sid from ganera.mensaje_campo", String.class))
                    .containsExactly("SM_reciente");

            // La guardia sigue dando 0 filas tras el flujo.
            assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos)).isEmpty();
        }
    }

    private static ResponseEntity<String> get(TestRestTemplate http, String ruta, String token) {
        HttpHeaders headers = cabeceras();
        headers.setBearerAuth(token);
        ResponseEntity<String> respuesta = http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
        assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(200);
        return respuesta;
    }
}
