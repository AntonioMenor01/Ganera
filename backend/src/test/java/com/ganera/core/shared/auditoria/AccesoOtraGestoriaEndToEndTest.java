package com.ganera.core.shared.auditoria;

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
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.whatsapp.EnvioWhatsappFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria base — T5 (plan 2026-10-21, D3). Acceso a un recurso de OTRA gestoria en CUALQUIER endpoint con id en la
 * ruta (y el explotacionId del cuerpo del PATCH de tramites y del vinculo de contactos): la respuesta sigue siendo
 * {@code 404} sin cuerpo, byte a byte igual a la de un id inexistente, pero un id ajeno deja una fila
 * {@code ACCESO_OTRA_GESTORIA} en la gestoria DEL ACTOR (operacion y entidad correctas, NINGUNA en la victima) y un
 * id inexistente no deja fila. La fila SOBREVIVE al rollback de las escrituras (registrarAparte, REQUIRES_NEW).
 *
 * <p>Twilio se finge con {@link EnvioWhatsappFalso} para que el POST de respuestas-whatsapp llegue al 404 (si no,
 * responderia 503 antes). El resto de endpoints no lo usan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EnvioWhatsappFalso.Config.class)
class AccesoOtraGestoriaEndToEndTest {

    private static final long ID_INEXISTENTE = 999_999_999L;

    enum Recurso { TRAMITE, GANADERO, EXPLOTACION, CONTACTO }

    /** Un endpoint con id de ruta. {@code ruta} lleva un %d para el id; {@code cuerpo} es el JSON (o null). */
    record Caso(String nombre, HttpMethod metodo, String ruta, String cuerpo, Recurso recurso,
                EntidadAuditada entidad, OperacionAuditada operacion) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    static Stream<Caso> casos() {
        String v0 = "{\"version\":0}";
        String respuesta = "{\"texto\":\"Hola\",\"claveIdempotencia\":\"" + UUID.randomUUID() + "\"}";
        return Stream.of(
                new Caso("GET tramite", HttpMethod.GET, "/tramites/%d", null,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.VER_TRAMITE),
                new Caso("PATCH tramite", HttpMethod.PATCH, "/tramites/%d", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.EDITAR_TRAMITE),
                new Caso("aprobar tramite", HttpMethod.POST, "/tramites/%d/aprobar", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.APROBAR),
                new Caso("rechazar tramite", HttpMethod.POST, "/tramites/%d/rechazar", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.RECHAZAR),
                new Caso("marcar hecho ovz", HttpMethod.POST, "/tramites/%d/marcar-hecho-ovz", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.MARCAR_HECHO_OVZ),
                new Caso("desmarcar hecho ovz", HttpMethod.POST, "/tramites/%d/desmarcar-hecho-ovz", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.DESMARCAR_HECHO_OVZ),
                new Caso("descartar tramite", HttpMethod.POST, "/tramites/%d/descartar", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.DESCARTAR),
                new Caso("deshacer descarte", HttpMethod.POST, "/tramites/%d/deshacer-descarte", v0,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.DESHACER_DESCARTE),
                new Caso("responder whatsapp", HttpMethod.POST, "/tramites/%d/respuestas-whatsapp", respuesta,
                        Recurso.TRAMITE, EntidadAuditada.TRAMITE, OperacionAuditada.RESPONDER_WHATSAPP),
                new Caso("GET ganadero", HttpMethod.GET, "/ganaderos/%d", null,
                        Recurso.GANADERO, EntidadAuditada.GANADERO, OperacionAuditada.VER_GANADERO),
                new Caso("GET explotacion", HttpMethod.GET, "/explotaciones/%d", null,
                        Recurso.EXPLOTACION, EntidadAuditada.EXPLOTACION, OperacionAuditada.VER_EXPLOTACION),
                new Caso("GET animales", HttpMethod.GET, "/explotaciones/%d/animales", null,
                        Recurso.EXPLOTACION, EntidadAuditada.EXPLOTACION, OperacionAuditada.VER_ANIMALES),
                new Caso("PUT contacto", HttpMethod.PUT, "/contactos/%d",
                        "{\"telefono\":\"+34600990123\",\"nombre\":\"X\"}",
                        Recurso.CONTACTO, EntidadAuditada.CONTACTO, OperacionAuditada.EDITAR_CONTACTO),
                new Caso("DELETE contacto", HttpMethod.DELETE, "/contactos/%d", null,
                        Recurso.CONTACTO, EntidadAuditada.CONTACTO, OperacionAuditada.DESACTIVAR_CONTACTO),
                new Caso("reactivar contacto", HttpMethod.POST, "/contactos/%d/reactivar", null,
                        Recurso.CONTACTO, EntidadAuditada.CONTACTO, OperacionAuditada.REACTIVAR_CONTACTO),
                new Caso("vincular contacto", HttpMethod.POST, "/contactos/%d/explotaciones",
                        "{\"explotacionId\":1,\"rol\":\"TITULAR\"}",
                        Recurso.CONTACTO, EntidadAuditada.CONTACTO, OperacionAuditada.VINCULAR_EXPLOTACION),
                new Caso("desvincular contacto", HttpMethod.DELETE, "/contactos/%d/explotaciones/1", null,
                        Recurso.CONTACTO, EntidadAuditada.CONTACTO, OperacionAuditada.DESVINCULAR_EXPLOTACION));
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
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
    private Usuario usuarioA;
    private String tokenA;
    private long tramiteBId;
    private long ganaderoBId;
    private long explotacionBId;
    private long contactoBId;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Acceso ajeno A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Acceso ajeno B"));
        usuarioA = crearUsuario(gestoriaA, "accesoA@test.com");
        crearUsuario(gestoriaB, "accesoB@test.com");
        tokenA = login("accesoA@test.com");
        suscripcion(gestoriaA); // ACTIVA: aprobar no se corta con 403 antes de llegar al 404
        // Todos los recursos "ajenos" viven en B.
        Explotacion explB = nuevaExplotacion(gestoriaB, "ES950000000001");
        explotacionBId = explB.getId();
        ganaderoBId = explB.getGanadero().getId();
        Contacto contactoB = nuevoContacto(gestoriaB, "+34600950001");
        contactoBId = contactoB.getId();
        tramiteBId = nuevoTramite(gestoriaB, contactoB).getId();
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from contacto_explotacion");
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void idDeOtraGestoriaDeja404IdenticoYFilaAccesoOtraGestoria(Caso caso) {
        long ajeno = idDe(caso.recurso());

        // 1) Id inexistente: 404 sin fila.
        ResponseEntity<String> rInexistente = llamar(caso.metodo(), caso.ruta().formatted(ID_INEXISTENTE), caso.cuerpo());
        assertThat(rInexistente.getStatusCode().value()).as(caso.nombre() + " inexistente").isEqualTo(404);
        assertThat(filas(gestoriaA)).as(caso.nombre() + " inexistente sin fila").isEmpty();

        // 2) Id de otra gestoria: 404 identico + una fila ACCESO_OTRA_GESTORIA en A, ninguna en B.
        ResponseEntity<String> rAjeno = llamar(caso.metodo(), caso.ruta().formatted(ajeno), caso.cuerpo());
        assertThat(rAjeno.getStatusCode()).as(caso.nombre() + " ajeno status").isEqualTo(rInexistente.getStatusCode());
        assertThat(rAjeno.getBody()).as(caso.nombre() + " ajeno cuerpo byte a byte").isEqualTo(rInexistente.getBody());
        assertThat(rAjeno.getBody()).as(caso.nombre() + " 404 sin cuerpo").isNull();

        List<Auditoria> filasA = filas(gestoriaA);
        assertThat(filasA).as(caso.nombre() + " una fila en A").hasSize(1);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioA.getId());
        assertThat(fila.getGestoria().getId()).as("va a la gestoria del actor").isEqualTo(gestoriaA.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(caso.entidad());
        assertThat(fila.getEntidadId()).isEqualTo(ajeno);
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isEqualTo("{\"operacion\":\"" + caso.operacion().name() + "\"}");
        assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(fila.getRequestId()).isNotBlank().isEqualTo(rAjeno.getHeaders().getFirst("X-Request-Id"));
        // Nunca una fila en la victima.
        assertThat(filas(gestoriaB)).as(caso.nombre() + " nada en la victima").isEmpty();
    }

    /** Id en el CUERPO (D3): PATCH con explotacionId ajeno y el tramite de la RUTA propio -> fila EXPLOTACION. */
    @Test
    void patchConExplotacionIdAjenoYTramitePropioAuditaLaExplotacion() {
        Tramite propio = nuevoTramite(gestoriaA, nuevoContacto(gestoriaA, "+34600940001"));
        long version = version(propio.getId());
        String cuerpo = "{\"version\":" + version + ",\"explotacionId\":" + explotacionBId + "}";

        ResponseEntity<String> r = llamar(HttpMethod.PATCH, "/tramites/" + propio.getId(), cuerpo);
        assertThat(r.getStatusCode().value()).isEqualTo(404);

        List<Auditoria> filasA = filas(gestoriaA);
        assertThat(filasA).hasSize(1);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.EXPLOTACION);
        assertThat(fila.getEntidadId()).isEqualTo(explotacionBId);
        assertThat(fila.getDespues()).isEqualTo("{\"operacion\":\"EDITAR_TRAMITE\"}");
        assertThat(filas(gestoriaB)).isEmpty();
    }

    /** Id en el CUERPO (D3): vincular con explotacionId ajeno y el contacto de la RUTA propio -> fila EXPLOTACION. */
    @Test
    void vincularConExplotacionIdAjenoYContactoPropioAuditaLaExplotacion() {
        Contacto propio = nuevoContacto(gestoriaA, "+34600940002");
        String cuerpo = "{\"explotacionId\":" + explotacionBId + ",\"rol\":\"TITULAR\"}";

        ResponseEntity<String> r = llamar(HttpMethod.POST, "/contactos/" + propio.getId() + "/explotaciones", cuerpo);
        assertThat(r.getStatusCode().value()).isEqualTo(404);

        List<Auditoria> filasA = filas(gestoriaA);
        assertThat(filasA).hasSize(1);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.EXPLOTACION);
        assertThat(fila.getEntidadId()).isEqualTo(explotacionBId);
        assertThat(fila.getDespues()).isEqualTo("{\"operacion\":\"VINCULAR_EXPLOTACION\"}");
        assertThat(filas(gestoriaB)).isEmpty();
    }

    /** La fila ACCESO_OTRA_GESTORIA de una escritura SOBREVIVE al rollback de su transaccion (REQUIRES_NEW). */
    @Test
    void laFilaSobreviveAlRollbackDeUnaAprobacion() {
        ResponseEntity<String> r = llamar(HttpMethod.POST, "/tramites/" + tramiteBId + "/aprobar", "{\"version\":0}");
        assertThat(r.getStatusCode().value()).isEqualTo(404);

        // La transaccion de aprobar revirtio (tramite no encontrado); la fila, en su propia transaccion, quedo.
        assertThat(filas(gestoriaA)).extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(filas(gestoriaA).get(0).getEntidadId()).isEqualTo(tramiteBId);
    }

    // --------------------------------------------------------------- helpers

    private long idDe(Recurso recurso) {
        return switch (recurso) {
            case TRAMITE -> tramiteBId;
            case GANADERO -> ganaderoBId;
            case EXPLOTACION -> explotacionBId;
            case CONTACTO -> contactoBId;
        };
    }

    private ResponseEntity<String> llamar(HttpMethod metodo, String ruta, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        if (cuerpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private List<Auditoria> filas(Gestoria g) {
        return auditoriaRepository.findByGestoriaIdOrderByIdAsc(g.getId());
    }

    private long version(Long tramiteId) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramiteId);
    }

    private void suscripcion(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);
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
        contacto.setNombre("Contacto acceso ajeno");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private Usuario crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario acceso ajeno");
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
