package com.ganera.core.registro;

import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test HTTP real de POST /gestorias/registro: sin ninguna cabecera de autenticacion (para
 * confirmar que de verdad es publico -- si SecurityConfig no tuviera el permitAll nuevo, esto
 * fallaria con 401 antes de llegar al controller). stripe.price-id-explotacion esta en blanco en
 * application.yml de test, asi que el camino de exito real observable aqui es 503 (Stripe no
 * configurado) con Gestoria+Usuario ya persistidos -- limitacion ya aceptada mientras el Price
 * de Stripe siga sin crearse (ver CLAUDE.md, Prompt 2.7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistroGestoriaEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Cuerpo exacto de todo 400 de registro: {motivo} (MotivoErrorResponse), el mismo texto para
     * cualquier causa, y ninguna clave "mensaje" (el antiguo RegistroErrorResponse). */
    private static final String CUERPO_UNIFORME = "{\"motivo\":\"No se ha podido completar el registro con esos "
            + "datos. Revisa el email y la contraseña e inténtalo de nuevo.\"}";

    @AfterEach
    void limpiar() {
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    private ResponseEntity<String> registrar(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/gestorias/registro", new HttpEntity<>(json, headers), String.class);
    }

    @Test
    void registroSinAutenticacionNoDevuelve401YCreaGestoriaYUsuario() {
        ResponseEntity<String> respuesta = registrar("""
                {"nombreGestoria":"Gestoria E2E Registro","nombreUsuario":"Empleado E2E",
                 "email":"e2e-registro@gestoria.com","password":"password123","rangoClientes":"UNO_A_DIEZ"}
                """);

        assertThat(respuesta.getStatusCode().value()).isNotEqualTo(401);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(usuarioRepository.findByEmail("e2e-registro@gestoria.com")).isPresent();
    }

    @Test
    void segundoRegistroConElMismoEmailDevuelve400ConMensajeGenericoYNoDejaGestoriaHuerfano() {
        registrar("""
                {"nombreGestoria":"Gestoria E2E Uno","nombreUsuario":"Empleado Uno",
                 "email":"e2e-duplicado@gestoria.com","password":"password123","rangoClientes":"UNO_A_DIEZ"}
                """);
        long totalGestoriasTrasElPrimero = gestoriaRepository.count();

        ResponseEntity<String> segundaRespuesta = registrar("""
                {"nombreGestoria":"Gestoria E2E Dos","nombreUsuario":"Empleado Dos",
                 "email":"e2e-duplicado@gestoria.com","password":"password456","rangoClientes":"ONCE_A_TREINTA"}
                """);

        assertThat(segundaRespuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(segundaRespuesta.getBody()).isEqualTo(CUERPO_UNIFORME);
        assertThat(gestoriaRepository.count()).isEqualTo(totalGestoriasTrasElPrimero);
    }

    @Test
    void registroConPasswordDebilDevuelve400YNoCreaNada() {
        ResponseEntity<String> respuesta = registrar("""
                {"nombreGestoria":"Gestoria E2E Debil","nombreUsuario":"Empleado",
                 "email":"e2e-debil@gestoria.com","password":"corta","rangoClientes":"UNO_A_DIEZ"}
                """);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(CUERPO_UNIFORME);
        assertThat(usuarioRepository.findByEmail("e2e-debil@gestoria.com")).isEmpty();
    }

    /** Sin oraculo: email duplicado, password debil y email mal formado dan bytes identicos, con
     * Content-Type JSON, clave "motivo" y sin "mensaje". */
    @Test
    void emailDuplicadoPasswordDebilYEmailMalFormadoDevuelvenElMismoCuerpoExacto() {
        registrar("""
                {"nombreGestoria":"Gestoria E2E Oraculo","nombreUsuario":"Empleado",
                 "email":"e2e-oraculo@gestoria.com","password":"password123","rangoClientes":"UNO_A_DIEZ"}
                """);

        ResponseEntity<String> duplicado = registrar("""
                {"nombreGestoria":"Gestoria E2E Oraculo Dos","nombreUsuario":"Empleado Dos",
                 "email":"e2e-oraculo@gestoria.com","password":"password456","rangoClientes":"UNO_A_DIEZ"}
                """);
        ResponseEntity<String> debil = registrar("""
                {"nombreGestoria":"Gestoria E2E Oraculo Tres","nombreUsuario":"Empleado Tres",
                 "email":"e2e-oraculo-debil@gestoria.com","password":"corta","rangoClientes":"UNO_A_DIEZ"}
                """);
        ResponseEntity<String> malFormado = registrar("""
                {"nombreGestoria":"Gestoria E2E Oraculo Cuatro","nombreUsuario":"Empleado Cuatro",
                 "email":"no-es-un-email","password":"password123","rangoClientes":"UNO_A_DIEZ"}
                """);

        for (ResponseEntity<String> respuesta : List.of(duplicado, debil, malFormado)) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
            assertThat(respuesta.getHeaders().getContentType()).isNotNull();
            assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            assertThat(respuesta.getBody()).isEqualTo(CUERPO_UNIFORME).doesNotContain("mensaje");
        }
        assertThat(duplicado.getBody().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(debil.getBody().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(malFormado.getBody().getBytes(StandardCharsets.UTF_8));
    }
}
