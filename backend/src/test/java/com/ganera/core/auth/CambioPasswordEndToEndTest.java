package com.ganera.core.auth;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST /auth/cambiar-password (rama feat/cambio-password-forzado). Un cambio correcto baja el flag
 * {@code debeCambiarPassword}, cambia de verdad la contrasena (la vieja deja de valer, la nueva vale) y deja
 * UNA fila de auditoria {@code USUARIO_CAMBIO_PASSWORD} sin la contrasena (antes/despues null). La contrasena
 * actual incorrecta y la nueva demasiado corta dan 400 {motivo} sin cambiar nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class CambioPasswordEndToEndTest {

    private static final String EMAIL = "cambio-password@test.com";
    private static final String PASSWORD_TEMPORAL = "temporal123";
    private static final String PASSWORD_NUEVA = "miClaveNueva1";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoria;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        gestoria = gestoriaRepository.save(new Gestoria("Cambio password E2E"));
        usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD_TEMPORAL));
        usuario.setNombre("Usuario cambio password");
        usuario.setActivo(true);
        usuario.setDebeCambiarPassword(true);
        usuario = usuarioRepository.save(usuario);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void cambioCorrectoBajaElFlagCambiaLaContrasenaYDejaFilaDeAuditoria() {
        String token = login(PASSWORD_TEMPORAL);

        ResponseEntity<String> r = cambiar(token, PASSWORD_TEMPORAL, PASSWORD_NUEVA);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"debeCambiarPassword\":false");

        // El flag persiste en false.
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().isDebeCambiarPassword()).isFalse();

        // La vieja ya no vale; la nueva, si.
        assertThat(loginRespuesta(PASSWORD_TEMPORAL).getStatusCode().value()).isEqualTo(401);
        assertThat(loginRespuesta(PASSWORD_NUEVA).getStatusCode().value()).isEqualTo(200);

        // Exactamente una fila USUARIO_CAMBIO_PASSWORD, sin la contrasena.
        List<Auditoria> cambios = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId()).stream()
                .filter(a -> a.getAccion() == AccionAuditoria.USUARIO_CAMBIO_PASSWORD)
                .toList();
        assertThat(cambios).hasSize(1);
        Auditoria fila = cambios.get(0);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuario.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.USUARIO);
        assertThat(fila.getEntidadId()).isEqualTo(usuario.getId());
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isNull();
    }

    @Test
    void contrasenaActualIncorrectaDa400YNoCambiaNada() {
        String token = login(PASSWORD_TEMPORAL);

        ResponseEntity<String> r = cambiar(token, "no-es-la-actual", PASSWORD_NUEVA);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).contains("\"motivo\"");

        // No ha cambiado nada: la vieja sigue valiendo y el flag sigue en true.
        assertThat(loginRespuesta(PASSWORD_TEMPORAL).getStatusCode().value()).isEqualTo(200);
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().isDebeCambiarPassword()).isTrue();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId()).stream()
                .noneMatch(a -> a.getAccion() == AccionAuditoria.USUARIO_CAMBIO_PASSWORD)).isTrue();
    }

    @Test
    void contrasenaNuevaDemasiadoCortaDa400() {
        String token = login(PASSWORD_TEMPORAL);

        ResponseEntity<String> r = cambiar(token, PASSWORD_TEMPORAL, "corta");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).contains("\"motivo\"");
        assertThat(loginRespuesta(PASSWORD_TEMPORAL).getStatusCode().value()).isEqualTo(200);
    }

    private String login(String password) {
        ResponseEntity<LoginResponse> r = restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(EMAIL, password), jsonHeaders(null)), LoginResponse.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return r.getBody().token();
    }

    private ResponseEntity<String> loginRespuesta(String password) {
        return restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(EMAIL, password), jsonHeaders(null)), String.class);
    }

    private ResponseEntity<String> cambiar(String token, String actual, String nueva) {
        return restTemplate.exchange("/auth/cambiar-password", HttpMethod.POST,
                new HttpEntity<>(new CambioPasswordRequest(actual, nueva), jsonHeaders(token)), String.class);
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
