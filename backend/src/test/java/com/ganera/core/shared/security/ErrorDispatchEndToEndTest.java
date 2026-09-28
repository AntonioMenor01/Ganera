package com.ganera.core.shared.security;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision 29 (revision M1): /error es publico en SecurityConfig. Sin eso, el "error dispatch" de
 * Spring (p. ej. un cuerpo JSON mal formado -> 400) se ejecutaba sin autenticacion
 * (JwtAuthenticationFilter es OncePerRequestFilter y no corre en el dispatch de error) y acababa en
 * 401, que el frontend trata como "sesion caducada" y cierra la sesion. Servidor embebido real:
 * solo asi se ejercita el dispatch de error y la cadena de seguridad de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    private static final String NOMBRE_GESTORIA = "Gestoria MarcadorErrorDispatch";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private String token;

    @BeforeEach
    void preparar() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> alta = restTemplate.postForEntity("/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest(NOMBRE_GESTORIA, "errordispatch@test.com", "password123", "Usuario"),
                        headers),
                OnboardingResponse.class);
        assertThat(alta.getStatusCode().value()).isEqualTo(200);
        token = restTemplate.postForEntity("/auth/login",
                new LoginRequest("errordispatch@test.com", "password123"), LoginResponse.class).getBody().token();
    }

    @AfterEach
    void limpiar() {
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void unCuerpoConUnTipoIncorrectoDevuelve400NoUn401() {
        ResponseEntity<String> respuesta = peticion(HttpMethod.PATCH, "/tramites/1", token, "{\"crotales\":\"1234\"}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unCuerpoQueNoEsJsonDevuelve400NoUn401() {
        ResponseEntity<String> respuesta = peticion(HttpMethod.PATCH, "/tramites/1", token, "esto no es json");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unIdNoNumericoDevuelve400NoUn401() {
        ResponseEntity<String> respuesta = peticion(HttpMethod.GET, "/tramites/abc", token, null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
    }

    /** /error publico no hace publica ninguna otra ruta: sin JWT, todo lo protegido sigue en 401. */
    @Test
    void sinJwtLasRutasProtegidasSiguenDevolviendo401() {
        for (String ruta : List.of("/tramites", "/tramites/1", "/explotaciones", "/ganaderos", "/contactos",
                "/auth/me", "/facturacion/suscripcion", "/error/x", "/errores")) {
            assertThat(peticion(HttpMethod.GET, ruta, null, null).getStatusCode().value()).as(ruta).isEqualTo(401);
        }
        assertThat(peticion(HttpMethod.PATCH, "/tramites/1", null, "{\"crotales\":\"1234\"}").getStatusCode().value())
                .isEqualTo(401);
        assertThat(peticion(HttpMethod.POST, "/tramites/1/aprobar", null, "{\"version\":0}").getStatusCode().value())
                .isEqualTo(401);
    }

    /** Un GET directo a /error sin autenticar no devuelve 200 ni datos de ninguna Gestoria. */
    @Test
    void unGetDirectoAErrorSinAutenticarNoDevuelveDatos() {
        ResponseEntity<String> respuesta = peticion(HttpMethod.GET, "/error", null, null);

        assertThat(respuesta.getStatusCode().value()).isNotEqualTo(200);
        String cuerpo = respuesta.getBody() == null ? "" : respuesta.getBody().toLowerCase(Locale.ROOT);
        assertThat(cuerpo).doesNotContain("marcadorerrordispatch").doesNotContain("errordispatch@test.com")
                .doesNotContain("gestoria").doesNotContain("token").doesNotContain("exception");
    }

    private ResponseEntity<String> peticion(HttpMethod metodo, String ruta, String jwt, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        if (cuerpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }
}
