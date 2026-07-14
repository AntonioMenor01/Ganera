package com.ganera.core.shared.tenant;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberadamente NO es un @DataJpaTest ni una llamada directa a
 * TenantFilterActivationInterceptor.preHandle() (como TenantFilterActivationInterceptorTest) --
 * este es el UNICO tipo de test que puede detectar un bug de ORDEN de HandlerInterceptor de
 * Spring MVC: hace falta un servidor embebido real, con los interceptors registrados de verdad
 * por WebMvcConfigurationSupport, atendiendo una request HTTP real en su propio hilo. Un test que
 * invoca el interceptor a mano nunca ejercita ese registro/orden real y por eso el bug que este
 * test cubre (ver WebMvcTenantConfig) llevaba latente desde que se creo el interceptor sin que
 * ningun test lo detectara -- ninguna verificacion previa (ni unit test ni smoke test manual)
 * habia probado dos Gestorias reales con datos solapados via HTTP real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiar() {
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void dosGestoriasDistintasNuncaVenLosDatosLaUnaDeLaOtraViaHttpReal() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E B"));

        crearUsuario(gestoriaA, "e2eA@test.com");
        crearUsuario(gestoriaB, "e2eB@test.com");

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoriaA);
        ganadero.setNombre("Ganadero E2E");
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoriaA);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES900000000001");
        explotacion.setNombre("Finca E2E");
        explotacionRepository.save(explotacion);

        String tokenA = login("e2eA@test.com");
        String tokenB = login("e2eB@test.com");

        ResponseEntity<String> respuestaA = getExplotaciones(tokenA);
        ResponseEntity<String> respuestaB = getExplotaciones(tokenB);

        assertThat(respuestaA.getBody()).contains("ES900000000001");
        assertThat(respuestaB.getBody()).doesNotContain("ES900000000001");
        assertThat(respuestaB.getBody()).contains("\"totalElements\":0");
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }

    private ResponseEntity<String> getExplotaciones(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                "/explotaciones", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
