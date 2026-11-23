package com.ganera.core.tramite;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.ovz.PresentacionAutomaticaProperties;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.auditoria.SinGuardiaDml;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Presentación automática de la baja en OVZ — T2 (cola {@code trabajo_ovz}): encolado al aprobar y reencolado (D11),
 * sobre un servidor real y DOS gestorías. El encolado va en la MISMA transacción que la aprobación y solo si el
 * ganadero tiene credenciales y no usa certificado (D4/D8); el reencolado de un error INCIERTO exige confirmación
 * explícita y queda auditado. Cada acción deja su fila EXACTA en la gestoría del actor y ninguna en la otra.
 *
 * <p>El interruptor maestro {@code ganera.ovz.presentacion-automatica.activa} se enciende SOLO aquí
 * ({@code @TestPropertySource}, T3). Además (T6) el encolado exige que la gestoría esté en la lista
 * {@code ganera.ovz.presentacion-automatica.gestorias}: el {@code @BeforeEach} mete la gestoría A en la lista (vía
 * el bean de propiedades, porque los ids son dinámicos) y deja la B FUERA, así que A encola y B no (maestro
 * encendido pero gestoría no habilitada). La posición apagada del maestro se prueba en
 * {@code PresentacionOvzInterruptorApagadoEndToEndTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ganera.ovz.presentacion-automatica.activa=true")
@ExtendWith(GuardiaDmlExtension.class)
class PresentacionOvzEndToEndTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CifradorAesGcm cifrador;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private TrabajoOvzRepository trabajoOvzRepository;
    @Autowired private SuscripcionRepository suscripcionRepository;
    @Autowired private PresentacionAutomaticaProperties presentacionAutomatica;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Presentacion OVZ A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Presentacion OVZ B"));
        crearUsuario(gestoriaA, "presentacionA@test.com");
        crearUsuario(gestoriaB, "presentacionB@test.com");
        tokenA = login("presentacionA@test.com");
        tokenB = login("presentacionB@test.com");
        suscripcion(gestoriaA);
        suscripcion(gestoriaB);
        // T6: la gestoría A está en la lista de habilitadas (ids dinámicos → vía el bean); la B queda FUERA.
        presentacionAutomatica.getGestorias().clear();
        presentacionAutomatica.getGestorias().add(gestoriaA.getId());
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        presentacionAutomatica.getGestorias().clear();
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from trabajo_ovz");
        jdbcTemplate.update("delete from tramite_crotal");
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // ==================== Encolado al aprobar ====================

    @Test
    void aprobarBajaMuerteConCredencialesEncolaUnTrabajo() {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000001", true, false);
        Tramite tramite = bajaMuertePendiente(gestoriaA, explotacion);

        ResponseEntity<String> r = aprobar(tramite);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        TrabajoOvz trabajo = trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramite.getId(), gestoriaA.getId())
                .orElseThrow();
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajoOvz.PENDIENTE);
        assertThat(trabajo.getTipo()).isEqualTo(TipoTrabajoOvz.BAJA_MUERTE);
        assertThat(trabajo.getClaseError()).isNull();
        // El trámite queda APROBADO (la presentación la hará el worker fuera de esta petición).
        assertThat(estadoTramite(tramite)).isEqualTo("APROBADO");
        // Una fila OVZ_TRABAJO_ENCOLADO en la gestoría del actor, ninguna en la otra.
        assertThat(acciones(gestoriaA)).contains("OVZ_TRABAJO_ENCOLADO");
        assertThat(acciones(gestoriaB)).isEmpty();
    }

    @Test
    void aprobarBajaMuerteSinCredencialesNoEncola() {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000002", false, false);
        Tramite tramite = bajaMuertePendiente(gestoriaA, explotacion);

        assertThat(aprobar(tramite).getStatusCode().value()).isEqualTo(200);

        assertThat(trabajoOvzRepository.existsByTramiteId(tramite.getId())).isFalse();
        assertThat(acciones(gestoriaA)).doesNotContain("OVZ_TRABAJO_ENCOLADO");
    }

    @Test
    void aprobarBajaMuerteConCertificadoNoEncola() {
        // Con certificado no hay usuario/contraseña válidos (D8): vía manual, nunca automático.
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000003", true, true);
        Tramite tramite = bajaMuertePendiente(gestoriaA, explotacion);

        assertThat(aprobar(tramite).getStatusCode().value()).isEqualTo(200);

        assertThat(trabajoOvzRepository.existsByTramiteId(tramite.getId())).isFalse();
    }

    @Test
    void aprobarOtroTipoConCredencialesNoEncola() {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000004", true, false);
        Tramite tramite = tramitePendiente(gestoriaA, explotacion, TipoTramite.ALTA_NACIMIENTO);

        assertThat(aprobar(tramite).getStatusCode().value()).isEqualTo(200);

        assertThat(trabajoOvzRepository.existsByTramiteId(tramite.getId())).isFalse();
    }

    @Test
    void aprobarBajaMuerteDeUnaGestoriaFueraDeLaListaNoEncola() {
        // T6: maestro ENCENDIDO (este contexto) pero la gestoría B NO está en la lista de habilitadas. Aunque el
        // ganadero tiene credenciales válidas, al aprobar NO se encola (fail-closed: activa Y en la lista).
        Explotacion explotacion = explotacionConGanadero(gestoriaB, "ES020000000050", true, false);
        Tramite tramite = bajaMuertePendiente(gestoriaB, explotacion);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenB);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = restTemplate.postForEntity("/tramites/" + tramite.getId() + "/aprobar",
                new HttpEntity<>(Map.of("version", versionTramite(tramite.getId())), headers), String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(trabajoOvzRepository.existsByTramiteId(tramite.getId())).isFalse();
        assertThat(acciones(gestoriaB)).contains("TRAMITE_APROBADO").doesNotContain("OVZ_TRABAJO_ENCOLADO");
    }

    // ==================== Detalle: se presentará automáticamente / estado del trabajo (T5) ====================

    @Test
    void elDetalleDeUnaBajaConCredencialesDicePresentaraAutomaticamenteYSinTrabajoAun() throws Exception {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000020", true, false);
        Tramite tramite = bajaMuertePendiente(gestoriaA, explotacion);

        var detalle = detalleJson(tramite.getId());
        assertThat(detalle.get("sePresentaraAutomaticamente").asBoolean()).isTrue();
        // Pendiente de revisión: aún no hay trabajo encolado (solo se encola al aprobar).
        assertThat(detalle.get("presentacionOvz").isNull()).isTrue();
    }

    @Test
    void elDetalleSinCredencialesNoSePresentaAutomaticamente() throws Exception {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000021", false, false);
        Tramite tramite = bajaMuertePendiente(gestoriaA, explotacion);

        var detalle = detalleJson(tramite.getId());
        assertThat(detalle.get("sePresentaraAutomaticamente").asBoolean()).isFalse();
        assertThat(detalle.get("presentacionOvz").isNull()).isTrue();
    }

    @Test
    void elDetalleDeUnTrabajoEnErrorInciertoLlevaElEstadoYLaClaseDeError() throws Exception {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000022", TipoErrorOvz.INCIERTO);
        long tramiteId = trabajo.getTramite().getId();

        var detalle = detalleJson(tramiteId);
        var presentacion = detalle.get("presentacionOvz");
        assertThat(presentacion.isNull()).isFalse();
        assertThat(presentacion.get("estadoTrabajo").asText()).isEqualTo("ERROR");
        assertThat(presentacion.get("claseError").asText()).isEqualTo("INCIERTO");
        // presentacionOvz nunca lleva el texto del motivo: ese va en tramite.motivoError (lo fija el worker).
        assertThat(presentacion.has("motivoError")).isFalse();
    }

    // ==================== Reencolado (D11) ====================

    @Test
    void reencolarErrorSeguroVuelveAPendienteYApruebaElTramite() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000010", TipoErrorOvz.SEGURO);
        long tramiteId = trabajo.getTramite().getId();

        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId), null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        TrabajoOvz despues = trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaA.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTrabajoOvz.PENDIENTE);
        assertThat(despues.getClaseError()).isNull();
        assertThat(despues.getMotivoError()).isNull();
        assertThat(estadoTramite(tramiteId)).isEqualTo("APROBADO");
        assertThat(acciones(gestoriaA)).contains("OVZ_TRABAJO_REENCOLADO");
        assertThat(acciones(gestoriaB)).isEmpty();
    }

    @Test
    @SinGuardiaDml("Siembra el trámite y su trabajo en error en el cuerpo; el 409 de incierto-sin-confirmación no "
            + "escribe ni audita nada (eso es lo que comprueba).")
    void reencolarErrorInciertoSinConfirmacionDa409() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000011", TipoErrorOvz.INCIERTO);
        long tramiteId = trabajo.getTramite().getId();

        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId), null);
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody()).contains("riesgo de duplicarla");

        // No cambia nada: sigue en ERROR INCIERTO y el trámite en ERROR_OVZ.
        TrabajoOvz despues = trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaA.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTrabajoOvz.ERROR);
        assertThat(despues.getClaseError()).isEqualTo(TipoErrorOvz.INCIERTO);
        assertThat(estadoTramite(tramiteId)).isEqualTo("ERROR_OVZ");
        assertThat(acciones(gestoriaA)).isEmpty();
    }

    @Test
    void reencolarErrorInciertoConConfirmacionReencolaYAudita() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000012", TipoErrorOvz.INCIERTO);
        long tramiteId = trabajo.getTramite().getId();

        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId), Boolean.TRUE);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        TrabajoOvz despues = trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaA.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTrabajoOvz.PENDIENTE);
        assertThat(estadoTramite(tramiteId)).isEqualTo("APROBADO");
        // La confirmación de un incierto tiene su propia acción auditada (D11).
        assertThat(acciones(gestoriaA)).contains("OVZ_INCIERTO_CONFIRMADO").doesNotContain("OVZ_TRABAJO_REENCOLADO");
    }

    @Test
    @SinGuardiaDml("Siembra un trabajo EJECUTADO en el cuerpo; el 409 no audita.")
    void reencolarUnTrabajoEjecutadoDa409() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000013", TipoErrorOvz.SEGURO);
        trabajo.setEstado(EstadoTrabajoOvz.EJECUTADO);
        trabajo.setClaseError(null);
        trabajoOvzRepository.saveAndFlush(trabajo);
        long tramiteId = trabajo.getTramite().getId();

        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId), Boolean.TRUE);
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaA.getId()).orElseThrow()
                .getEstado()).isEqualTo(EstadoTrabajoOvz.EJECUTADO);
    }

    @Test
    @SinGuardiaDml("Siembra el trámite y su trabajo en el cuerpo; el 400 (sin version) no llega a tocar nada.")
    void reencolarSinVersionDa400() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000014", TipoErrorOvz.SEGURO);
        ResponseEntity<String> r = reencolar(trabajo.getTramite().getId(), null, null);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @SinGuardiaDml("Siembra el trámite y su trabajo en el cuerpo; el 409 de versión desfasada no audita.")
    void reencolarConVersionDesfasadaDa409() {
        TrabajoOvz trabajo = trabajoEnError(gestoriaA, "ES010000000015", TipoErrorOvz.SEGURO);
        long tramiteId = trabajo.getTramite().getId();
        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId) + 999, null);
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(acciones(gestoriaA)).isEmpty();
    }

    @Test
    @SinGuardiaDml("Siembra un trámite sin trabajo en el cuerpo; el 409 no audita.")
    void reencolarSinTrabajoDa409() {
        Explotacion explotacion = explotacionConGanadero(gestoriaA, "ES010000000016", true, false);
        Tramite tramite = tramitePendiente(gestoriaA, explotacion, TipoTramite.BAJA_MUERTE);
        ResponseEntity<String> r = reencolar(tramite.getId(), versionTramite(tramite.getId()), null);
        assertThat(r.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void reencolarUnTramiteDeOtraGestoriaDa404YDejaAccesoAjeno() {
        TrabajoOvz trabajoB = trabajoEnError(gestoriaB, "ES020000000001", TipoErrorOvz.SEGURO);
        long tramiteId = trabajoB.getTramite().getId();

        ResponseEntity<String> r = reencolar(tramiteId, versionTramite(tramiteId), null);
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        // Sigue en ERROR; el 404 no toca el trabajo ajeno.
        assertThat(trabajoOvzRepository.findByTramiteIdAndGestoriaId(tramiteId, gestoriaB.getId()).orElseThrow()
                .getEstado()).isEqualTo(EstadoTrabajoOvz.ERROR);
        // La fila ACCESO_OTRA_GESTORIA queda en la gestoría del actor (A), nunca en B.
        assertThat(acciones(gestoriaA)).contains("ACCESO_OTRA_GESTORIA");
        assertThat(acciones(gestoriaB)).isEmpty();
    }

    // ==================== Helpers ====================

    private Explotacion explotacionConGanadero(Gestoria gestoria, String rega, boolean conCredenciales,
                                               boolean usaCertificado) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + rega);
        if (usaCertificado) {
            ganadero.setUsaCertificado(true);
        } else if (conCredenciales) {
            ganadero.setOvzUsuario("ovzuser");
            ganadero.setOvzPasswordCifrada(cifrador.cifrar("ovzpass"));
        }
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(rega);
        explotacion.setNombre("Finca " + rega);
        return explotacionRepository.save(explotacion);
    }

    private Tramite bajaMuertePendiente(Gestoria gestoria, Explotacion explotacion) {
        return tramitePendiente(gestoria, explotacion, TipoTramite.BAJA_MUERTE);
    }

    private Tramite tramitePendiente(Gestoria gestoria, Explotacion explotacion, TipoTramite tipo) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setTipoTramite(tipo);
        if (tipo == TipoTramite.ALTA_NACIMIENTO) {
            tramite.setRaza("14"); // RETINTA: el alta exige raza para aprobar (D1)
        }
        return tramiteRepository.save(tramite);
    }

    /** Un trámite BAJA_MUERTE en ERROR_OVZ con su trabajo en ERROR de la clase indicada (como lo dejaría el worker). */
    private TrabajoOvz trabajoEnError(Gestoria gestoria, String rega, TipoErrorOvz clase) {
        Explotacion explotacion = explotacionConGanadero(gestoria, rega, true, false);
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.ERROR_OVZ);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(tramite);
        Instant ahora = Instant.now();
        TrabajoOvz trabajo = new TrabajoOvz();
        trabajo.setGestoria(gestoria);
        trabajo.setTramite(tramite);
        trabajo.setGanadero(explotacion.getGanadero());
        trabajo.setTipo(TipoTrabajoOvz.BAJA_MUERTE);
        trabajo.setEstado(EstadoTrabajoOvz.ERROR);
        trabajo.setClaseError(clase);
        trabajo.setMotivoError("fallo de prueba");
        trabajo.setCreatedAt(ahora);
        trabajo.setActualizadoEn(ahora);
        return trabajoOvzRepository.save(trabajo);
    }

    private ResponseEntity<String> aprobar(Tramite tramite) {
        return post("/tramites/" + tramite.getId() + "/aprobar", Map.of("version", versionTramite(tramite.getId())));
    }

    private ResponseEntity<String> reencolar(long tramiteId, Long version, Boolean confirmado) {
        Map<String, Object> cuerpo = new java.util.HashMap<>();
        if (version != null) {
            cuerpo.put("version", version);
        }
        if (confirmado != null) {
            cuerpo.put("confirmadoNoPresentado", confirmado);
        }
        return post("/tramites/" + tramiteId + "/reencolar-ovz", cuerpo);
    }

    private ResponseEntity<String> post(String path, Map<String, Object> cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** GET /tramites/{id} con Accept: application/json (el SDK de Twilio trae jackson-xml y si no se pide JSON la
     * respuesta llega en XML, footgun del CLAUDE.md). Devuelve el árbol JSON. */
    private com.fasterxml.jackson.databind.JsonNode detalleJson(long tramiteId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> r = restTemplate.exchange(
                "/tramites/" + tramiteId, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return JSON.readTree(r.getBody());
    }

    private long versionTramite(long tramiteId) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramiteId);
    }

    private String estadoTramite(Tramite tramite) {
        return estadoTramite(tramite.getId());
    }

    private String estadoTramite(long tramiteId) {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, tramiteId);
    }

    private List<String> acciones(Gestoria gestoria) {
        return jdbcTemplate.queryForList(
                "select accion from auditoria where gestoria_id = ? order by id asc", String.class, gestoria.getId());
    }

    private void suscripcion(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario presentacion OVZ");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
