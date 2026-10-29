package com.ganera.core.shared.auditoria;

import com.ganera.core.auth.LoginRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria del login (A2): un login correcto y un login fallido con email EXISTENTE dejan fila en la
 * gestoria de ese usuario (con {@code registrarAparte}, que sobrevive al 401); un email desconocido NO deja
 * fila y solo un log SIN el email. El {@code 401} es identico byte a byte en los tres casos (el motivo solo
 * va a la fila de auditoria, nunca al cliente).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({OutputCaptureExtension.class, GuardiaDmlExtension.class})
class AuditoriaLoginEndToEndTest {

    private static final String EMAIL_ACTIVO = "login-activo@test.com";
    private static final String EMAIL_INACTIVO = "login-inactivo@test.com";
    private static final String EMAIL_DESCONOCIDO = "login-desconocido-unico@test.com";

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
    private Usuario usuarioActivo;

    @BeforeEach
    void preparar() {
        gestoria = gestoriaRepository.save(new Gestoria("Auditoria login E2E"));
        usuarioActivo = crearUsuario(EMAIL_ACTIVO, true);
        crearUsuario(EMAIL_INACTIVO, false);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void loginCorrectoDejaUnaFilaLoginCorrecto() {
        ResponseEntity<String> r = login(EMAIL_ACTIVO, "password123");
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).hasSize(1);
        Auditoria fila = filas.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.LOGIN_CORRECTO);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioActivo.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.USUARIO);
        assertThat(fila.getEntidadId()).isEqualTo(usuarioActivo.getId());
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isNull();
        assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(fila.getRequestId()).isNotBlank();
    }

    @Test
    void contrasenaIncorrectaConEmailExistenteDejaLoginFallido() {
        ResponseEntity<String> r = login(EMAIL_ACTIVO, "password-mala");
        assertThat(r.getStatusCode().value()).isEqualTo(401);

        Auditoria fila = unica();
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.LOGIN_FALLIDO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioActivo.getId());
        assertThat(fila.getDespues()).isEqualTo("{\"motivo\":\"CONTRASENA_INCORRECTA\"}");
    }

    @Test
    void usuarioInactivoConContrasenaBuenaDejaLoginFallido() {
        ResponseEntity<String> r = login(EMAIL_INACTIVO, "password123");
        assertThat(r.getStatusCode().value()).isEqualTo(401);

        Auditoria fila = unica();
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.LOGIN_FALLIDO);
        assertThat(fila.getDespues()).isEqualTo("{\"motivo\":\"USUARIO_INACTIVO\"}");
    }

    @Test
    void emailDesconocidoNoDejaFilaYElLogNoLlevaElEmail(CapturedOutput salida) {
        ResponseEntity<String> r = login(EMAIL_DESCONOCIDO, "password123");
        assertThat(r.getStatusCode().value()).isEqualTo(401);

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId())).isEmpty();
        assertThat(salida.getAll()).doesNotContain(EMAIL_DESCONOCIDO);
    }

    @Test
    void losTresCuatrocientosUnoSonIdenticosByteAByte() {
        String malaPassword = login(EMAIL_ACTIVO, "password-mala").getBody();
        String inactivo = login(EMAIL_INACTIVO, "password123").getBody();
        String desconocido = login(EMAIL_DESCONOCIDO, "password123").getBody();

        assertThat(malaPassword).isEqualTo(inactivo).isEqualTo(desconocido);
    }

    private Auditoria unica() {
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).hasSize(1);
        return filas.get(0);
    }

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), headers), String.class);
    }

    private Usuario crearUsuario(String email, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario login E2E");
        usuario.setActivo(activo);
        return usuarioRepository.save(usuario);
    }
}
