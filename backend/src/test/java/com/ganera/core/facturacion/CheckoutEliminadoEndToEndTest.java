package com.ganera.core.facturacion;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El pago ya no se inicia desde la app (plan 2026-10-04 "quitar el pago de la app"): el cobro pasa
 * a la landing. Fija con un despacho HTTP real que POST /facturacion/checkout ha desaparecido de
 * verdad -- 404 con un JWT valido, 401 sin el -- y que llamarlo no crea ninguna Suscripcion como
 * efecto secundario. El checkout antiguo solo la creaba en TRIAL (via obtenerOCrearSuscripcion)
 * cuando Stripe estaba configurado; en el entorno de test Stripe esta sin configurar y daba 503
 * sin tocar la BD, asi que esa asercion es una guarda hacia delante, no algo que fallara antes. La
 * Gestoria se crea a mano, sin onboarding, para que parta sin Suscripcion.
 *
 * El 404 es el NoResourceFoundException de Spring Boot para una ruta sin ningun handler; por eso
 * no es un 405 (ya no queda ningun mapping en /facturacion/checkout para ningun metodo HTTP).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutEliminadoEndToEndTest {

    private static final String EMAIL = "checkout-eliminado@test.com";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Gestoria gestoria;

    @BeforeEach
    void preparar() {
        gestoria = gestoriaRepository.save(new Gestoria("Gestoria E2E sin checkout"));
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    @AfterEach
    void limpiar() {
        suscripcionRepository.findByGestoriaId(gestoria.getId()).ifPresent(suscripcionRepository::delete);
        usuarioRepository.findByEmail(EMAIL).ifPresent(usuarioRepository::delete);
        gestoriaRepository.delete(gestoria);
    }

    @Test
    void checkoutConJwtValidoDevuelve404YNoCreaSuscripcion() {
        String token = login();
        assertThat(suscripcionRepository.findByGestoriaId(gestoria.getId())).isEmpty();
        assertThat(get("/facturacion/suscripcion", token).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> respuesta = post("/facturacion/checkout", token);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(suscripcionRepository.findByGestoriaId(gestoria.getId())).isEmpty();
        assertThat(get("/facturacion/suscripcion", token).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void checkoutSinJwtDevuelve401YNoCreaSuscripcion() {
        ResponseEntity<String> respuesta = post("/facturacion/checkout", null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
        assertThat(suscripcionRepository.findByGestoriaId(gestoria.getId())).isEmpty();
    }

    private String login() {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(EMAIL, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
    }
}
