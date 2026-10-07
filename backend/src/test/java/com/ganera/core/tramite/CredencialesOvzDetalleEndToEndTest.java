package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ficha OVZ, T2b: el detalle del tramite (GET y PATCH /tramites/{id}) carga el Ganadero de la
 * explotacion -- y con el, EncryptedStringConverter descifra ovzPasswordCifrada en memoria (M2 de la
 * revision de T2, aceptado). Este E2E (HTTP real, dos gestorias) comprueba que ni la contrasena en
 * claro, ni el texto cifrado real de la BD, ni las claves ovzPassword/ovzUsuario salen en el cuerpo
 * crudo de la respuesta ni en los logs capturados durante las peticiones.
 *
 * La clave de cifrado y la contrasena son INVENTADAS solo para este test (la clave es Base64 de
 * "fake-clave-solo-para-el-test-T2b", 32 bytes); nunca la clave del entorno ni la del yml de test.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ganera.encryption.key=" + CredencialesOvzDetalleEndToEndTest.CLAVE_DE_TEST_INVENTADA)
class CredencialesOvzDetalleEndToEndTest {

    /** Base64 de "fake-clave-solo-para-el-test-T2b" (32 bytes, AES-256). Falsa a proposito. */
    static final String CLAVE_DE_TEST_INVENTADA = "ZmFrZS1jbGF2ZS1zb2xvLXBhcmEtZWwtdGVzdC1UMmI=";

    private static final String OVZ_USUARIO_INVENTADO = "usuario-ovz-inventado-T2b";
    private static final String OVZ_PASSWORD_INVENTADA = "contrasena-ovz-inventada-T2b";
    private static final String OVZ_PASSWORD_INVENTADA_B = "contrasena-ovz-inventada-T2b-de-B";
    /** Base64 valido (para que llegue al descifrado GCM) que no descifra con ninguna clave. */
    private static final String CIFRADO_CORRUPTO = Base64.getEncoder().encodeToString(
            "texto-cifrado-corrupto-inventado-T2b-0123456789".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private String tokenA;
    private Ganadero ganaderoA;
    private Explotacion explotacionA;
    private Explotacion explotacionB;
    private Contacto contactoA;
    private Contacto contactoB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria credenciales OVZ E2E A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria credenciales OVZ E2E B"));
        crearUsuario(gestoriaA, "credencialesOvzA@test.com");
        crearUsuario(gestoriaB, "credencialesOvzB@test.com");
        tokenA = login("credencialesOvzA@test.com");
        suscripcion(gestoriaA);
        suscripcion(gestoriaB);

        // La contrasena se guarda A TRAVES DE LA ENTIDAD, para que pase por EncryptedStringConverter.
        ganaderoA = nuevoGanadero(gestoriaA, "Titular OVZ de A", "33333333P", OVZ_PASSWORD_INVENTADA);
        explotacionA = nuevaExplotacion(gestoriaA, ganaderoA, "ES970000000001");
        Ganadero ganaderoB = nuevoGanadero(gestoriaB, "Titular OVZ de B", "44444444A", OVZ_PASSWORD_INVENTADA_B);
        explotacionB = nuevaExplotacion(gestoriaB, ganaderoB, "ES970000000101");
        contactoA = nuevoContacto(gestoriaA, "+34600970001");
        contactoB = nuevoContacto(gestoriaB, "+34600970002");
    }

    @AfterEach
    void limpiar() {
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        // En bloque (sin cargar entidades): el caso de error deja un cifrado que no descifra.
        ganaderoRepository.deleteAllInBatch();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void laContrasenaSeGuardaCifradaEnLaBaseDeDatos() {
        String cifrado = cifradoEnBd(ganaderoA);

        assertThat(cifrado).isNotBlank();
        assertThat(cifrado).isNotEqualTo(OVZ_PASSWORD_INVENTADA);
        assertThat(cifrado).doesNotContain(OVZ_PASSWORD_INVENTADA);
    }

    @Test
    void niElGetNiElPatchDelDetalleSacanLasCredencialesEnElCuerpoNiEnLosLogs(CapturedOutput salida)
            throws IOException {
        String cifrado = cifradoEnBd(ganaderoA);
        assertThat(cifrado).isNotBlank().isNotEqualTo(OVZ_PASSWORD_INVENTADA);
        Tramite conExplotacion = nuevoTramite(gestoriaA, contactoA, explotacionA);
        Tramite sinExplotacion = nuevoTramite(gestoriaA, contactoA, null);

        int inicioLogs = salida.getAll().length();
        ResponseEntity<String> detalle = get("/tramites/" + conExplotacion.getId(), tokenA);
        ResponseEntity<String> parcheado = patch(sinExplotacion, tokenA, Map.of("explotacionId", explotacionA.getId()));
        String logsDeLasPeticiones = salida.getAll().substring(inicioLogs);

        assertThat(detalle.getStatusCode().value()).isEqualTo(200);
        assertThat(parcheado.getStatusCode().value()).isEqualTo(200);
        // El detalle de verdad ha cargado el ganadero (y por tanto ha descifrado la contrasena).
        JsonNode cuerpoDetalle = objectMapper.readTree(detalle.getBody());
        JsonNode cuerpoParcheado = objectMapper.readTree(parcheado.getBody());
        assertThat(cuerpoDetalle.get("ganaderoNombre").asText()).isEqualTo("Titular OVZ de A");
        assertThat(cuerpoParcheado.get("ganaderoNombre").asText()).isEqualTo("Titular OVZ de A");

        for (String texto : new String[] {detalle.getBody(), parcheado.getBody(), logsDeLasPeticiones}) {
            assertThat(texto).doesNotContain(OVZ_PASSWORD_INVENTADA);
            assertThat(texto).doesNotContain(OVZ_PASSWORD_INVENTADA_B);
            assertThat(texto).doesNotContain(cifrado);
            assertThat(texto).doesNotContain(OVZ_USUARIO_INVENTADO);
            assertThat(texto).doesNotContain("ovzPassword");
            assertThat(texto).doesNotContain("ovzUsuario");
        }
    }

    /**
     * Caracterizacion del M2 aceptado: con un texto cifrado corrupto el detalle da hoy 500 (al cargar
     * el ganadero, el converter no puede descifrar). Ni el cuerpo ni los logs llevan el texto corrupto.
     */
    @Test
    void conUnCifradoCorruptoElDetalleDa500SinSacarElCifradoNiEnElCuerpoNiEnLosLogs(CapturedOutput salida)
            throws IOException {
        jdbcTemplate.update("update ganadero set ovz_password_cifrada = ? where id = ?",
                CIFRADO_CORRUPTO, ganaderoA.getId());
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA);

        int inicioLogs = salida.getAll().length();
        ResponseEntity<String> respuesta = get("/tramites/" + tramite.getId(), tokenA);
        String logsDeLaPeticion = salida.getAll().substring(inicioLogs);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        String cuerpo = respuesta.getBody() == null ? "" : respuesta.getBody();
        // Cuerpo de error por defecto de Boot: sin mensaje, sin traza, sin excepcion.
        JsonNode json = objectMapper.readTree(cuerpo);
        assertThat(json.get("status").asInt()).isEqualTo(500);
        assertThat(json.get("error").asText()).isEqualTo("Internal Server Error");
        assertThat(json.get("path").asText()).isEqualTo("/tramites/" + tramite.getId());
        assertThat(json.has("message")).isFalse();
        assertThat(json.has("trace")).isFalse();
        assertThat(json.has("exception")).isFalse();
        // Tomcat registra el fallo como ERROR con la causa (Tag mismatch de GCM), no con el valor.
        assertThat(logsDeLaPeticion).contains("Error attempting to apply AttributeConverter");
        for (String texto : new String[] {cuerpo, logsDeLaPeticion}) {
            assertThat(texto).doesNotContain(CIFRADO_CORRUPTO);
            assertThat(texto).doesNotContain(OVZ_USUARIO_INVENTADO);
            assertThat(texto).doesNotContain("ovzPassword");
            assertThat(texto).doesNotContain("ovzUsuario");
        }
    }

    @Test
    void elDetalleDelTramiteDeBConElTokenDeASigueDando404SinCuerpo(CapturedOutput salida) {
        Tramite tramiteB = nuevoTramite(gestoriaB, contactoB, explotacionB);

        int inicioLogs = salida.getAll().length();
        ResponseEntity<String> respuesta = get("/tramites/" + tramiteB.getId(), tokenA);
        String logsDeLaPeticion = salida.getAll().substring(inicioLogs);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(logsDeLaPeticion).doesNotContain(OVZ_PASSWORD_INVENTADA_B);
    }

    // ------------------------------------------------------------------ helpers

    private String cifradoEnBd(Ganadero ganadero) {
        return jdbcTemplate.queryForObject(
                "select ovz_password_cifrada from ganadero where id = ?", String.class, ganadero.getId());
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> patch(Tramite tramite, String token, Map<String, Object> cuerpo) {
        Map<String, Object> conVersion = new java.util.HashMap<>(cuerpo);
        conVersion.put("version", jdbcTemplate.queryForObject(
                "select version from tramite where id = ?", Long.class, tramite.getId()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/tramites/" + tramite.getId(), HttpMethod.PATCH,
                new HttpEntity<>(conVersion, headers), String.class);
    }

    private void suscripcion(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private Ganadero nuevoGanadero(Gestoria gestoria, String nombre, String nif, String passwordOvz) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        ganadero.setNif(nif);
        ganadero.setOvzUsuario(OVZ_USUARIO_INVENTADO);
        ganadero.setOvzPasswordCifrada(passwordOvz);
        return ganaderoRepository.save(ganadero);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, Ganadero ganadero, String codigoRega) {
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto credenciales OVZ E2E");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto, Explotacion explotacion) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario credenciales OVZ E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
