package com.ganera.core.registro;

import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt C, T2 (D4): con la configuracion por defecto (src/test/resources/application.yml no
 * define ganera.registro.abierto, asi que manda el valor por defecto del codigo) el registro
 * publico esta CERRADO: POST /gestorias/registro responde 404 sin cuerpo antes de leer nada, con
 * o sin JWT, y no crea Gestoria, Usuario ni Suscripcion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistroCerradoEndToEndTest {

    private static final String CUERPO_VALIDO = """
            {"nombreGestoria":"Gestoria Cerrada","nombreUsuario":"Empleado",
             "email":"registro-cerrado@gestoria.com","password":"password123","rangoClientes":"UNO_A_DIEZ"}
            """;
    private static final String CUERPO_INVALIDO = """
            {"nombreGestoria":"Gestoria Cerrada","nombreUsuario":"Empleado",
             "email":"no-es-un-email","password":"corta","rangoClientes":"UNO_A_DIEZ"}
            """;
    private static final String JSON_MAL_FORMADO = "{\"nombreGestoria\":";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private Environment environment;

    private ResponseEntity<String> enviar(HttpMethod metodo, String cuerpo, MediaType tipo, String autorizacion) {
        HttpHeaders headers = new HttpHeaders();
        if (tipo != null) {
            headers.setContentType(tipo);
        }
        if (autorizacion != null) {
            headers.set(HttpHeaders.AUTHORIZATION, autorizacion);
        }
        return restTemplate.exchange("/gestorias/registro", metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    @Test
    void registroCerradoDevuelve404SinCuerpoConCualquierCuerpoYConOSinJwtYNoCreaNada() {
        long gestorias = gestoriaRepository.count();
        long usuarios = usuarioRepository.count();
        long suscripciones = suscripcionRepository.count();

        String jwtValido = "Bearer " + jwtService.generarToken(new GaneraUserPrincipal(1L, 1L, "alguien@gestoria.com"));
        List<String> autorizaciones = new ArrayList<>();
        autorizaciones.add(null);
        autorizaciones.add(jwtValido);
        autorizaciones.add("Bearer token-que-no-es-un-jwt");

        List<ResponseEntity<String>> respuestas = new ArrayList<>();
        for (String autorizacion : autorizaciones) {
            respuestas.add(enviar(HttpMethod.POST, CUERPO_VALIDO, MediaType.APPLICATION_JSON, autorizacion));
            respuestas.add(enviar(HttpMethod.POST, CUERPO_INVALIDO, MediaType.APPLICATION_JSON, autorizacion));
            respuestas.add(enviar(HttpMethod.POST, JSON_MAL_FORMADO, MediaType.APPLICATION_JSON, autorizacion));
            respuestas.add(enviar(HttpMethod.POST, null, null, autorizacion));
            respuestas.add(enviar(HttpMethod.POST, CUERPO_VALIDO, MediaType.TEXT_PLAIN, autorizacion));
            respuestas.add(enviar(HttpMethod.GET, null, null, autorizacion));
        }

        assertThat(respuestas).allSatisfy(respuesta -> {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
            assertThat(respuesta.getBody()).isNull();
        });
        assertThat(gestoriaRepository.count()).isEqualTo(gestorias);
        assertThat(usuarioRepository.count()).isEqualTo(usuarios);
        assertThat(suscripcionRepository.count()).isEqualTo(suscripciones);
        assertThat(usuarioRepository.findByEmail("registro-cerrado@gestoria.com")).isEmpty();
    }

    /** El cierre solo afecta a esa ruta: el resto de rutas publicas y protegidas siguen igual. */
    @Test
    void elCierreNoAfectaAOtrasRutas() {
        ResponseEntity<String> login = restTemplate.postForEntity("/auth/login",
                new HttpEntity<>("{\"email\":\"nadie@gestoria.com\",\"password\":\"x\"}", jsonHeaders()), String.class);
        ResponseEntity<String> protegida = restTemplate.getForEntity("/tramites", String.class);

        assertThat(login.getStatusCode().value()).isEqualTo(401);
        assertThat(protegida.getStatusCode().value()).isEqualTo(401);
    }

    /** Esta suite corre con el H2 de los tests: el perfil clever no se activa fuera de su test. */
    @Test
    void laSuiteNormalNoUsaElPerfilCleverNiAbreElRegistro() {
        assertThat(environment.getActiveProfiles()).doesNotContain("clever");
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:");
        assertThat(environment.getProperty("ganera.registro.abierto")).isNull();
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
