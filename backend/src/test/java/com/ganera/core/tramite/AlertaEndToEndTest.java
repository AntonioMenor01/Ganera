package com.ganera.core.tramite;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento multi-tenant de GET /alertas sobre servidor real y DOS gestorías con sus JWT. Las alertas no
 * tienen tabla propia: {@link AlertaService} las deriva de los trámites de la gestoría con
 * {@code findByGestoriaId} (gestoriaId del JWT como parámetro explícito). Este guard de regresión afirma que
 * una alerta nacida de un trámite de A NUNCA aparece en las alertas de B. Si el finder dejara de estar scoped
 * (o se usara el filtro ambiente de Hibernate sin el id explícito), B vería la alerta de A y el test iría en rojo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertaEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void laAlertaDeUnTramiteDeAnuncaApareceEnLasAlertasDeB() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria Alertas A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria Alertas B"));
        crearUsuario(gestoriaA, "alertaA@test.com");
        crearUsuario(gestoriaB, "alertaB@test.com");

        // Trámite de A que dispara una alerta de plazo legal de forma determinista: BAJA_MUERTE con la muerte
        // hace 7 días -> el plazo de 7 días vence hoy, dentro de la ventana [hoy-1, hoy+3] que mira AlertaService.
        Tramite tramiteA = nuevoTramite(gestoriaA, "+34600000201");
        tramiteA.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteA.setFechaHecho(LocalDate.now().minusDays(7));
        tramiteA = tramiteRepository.save(tramiteA);

        String tokenA = login("alertaA@test.com");
        String tokenB = login("alertaB@test.com");

        ResponseEntity<String> alertasA = get("/alertas", tokenA);
        ResponseEntity<String> alertasB = get("/alertas", tokenB);

        // A ve su alerta, referida a su propio trámite.
        assertThat(alertasA.getStatusCode().value()).isEqualTo(200);
        assertThat(alertasA.getBody()).contains("\"tramiteId\":" + tramiteA.getId());
        // B, sin trámites, no ve ninguna: ni su lista tiene alertas ni asoma el id del trámite de A.
        assertThat(alertasB.getStatusCode().value()).isEqualTo(200);
        assertThat(alertasB.getBody()).isEqualTo("[]");
        assertThat(alertasB.getBody()).doesNotContain("\"tramiteId\":" + tramiteA.getId());
    }

    private Tramite nuevoTramite(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto Alertas");
        contacto.setGestoria(gestoria);
        contactoRepository.save(contacto);

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario Alertas");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
