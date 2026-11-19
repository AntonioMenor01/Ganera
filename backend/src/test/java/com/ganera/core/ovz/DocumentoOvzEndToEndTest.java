package com.ganera.core.ovz;

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
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.TramiteRepository;
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
 * Justificantes OVZ sobre servidor real y DOS gestorias (T3). Cubre el listado por ganadero, la descarga del PDF como
 * adjunto, el aislamiento multi-tenant (recurso ajeno -> 404 sin cuerpo + ACCESO_OTRA_GESTORIA en la gestoria del
 * actor) y la fila LECTURA_DOCUMENTO_OVZ de la descarga. {@code descargarUnDocumentoAjenoDa404} es la mutacion
 * «descarga sin filtro de gestoria»: sin {@code findByIdAndGestoriaId} devolveria 200 y el test iria en rojo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(com.ganera.core.shared.auditoria.GuardiaDmlExtension.class)
class DocumentoOvzEndToEndTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DocumentoOvzService documentoOvzService;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private TramiteRepository tramiteRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private String tokenA;
    private long ganaderoAId;
    private long ganaderoBId;
    private long documentoAId;
    private long documentoBId;
    private byte[] pdfA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Documentos E2E A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Documentos E2E B"));
        crearUsuario(gestoriaA, "docsA@test.com");
        crearUsuario(gestoriaB, "docsB@test.com");
        tokenA = login("docsA@test.com");

        pdfA = PdfDePrueba.conLineas("Justificante de baja A", "Crotal: ES010000009999");
        long[] a = sembrarDocumento(gestoriaA, "ES010000000001", "Baja Animal A.pdf", pdfA);
        ganaderoAId = a[0];
        documentoAId = a[1];
        byte[] pdfB = PdfDePrueba.conLineas("Justificante de baja B", "Crotal: ES020000009999");
        long[] b = sembrarDocumento(gestoriaB, "ES020000000001", "Baja Animal B.pdf", pdfB);
        ganaderoBId = b[0];
        documentoBId = b[1];

        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from documento_ovz_contenido");
        jdbcTemplate.update("delete from documento_ovz");
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void listarLosDocumentosDeUnGanaderoPropio() {
        ResponseEntity<String> r = get("/ganaderos/" + ganaderoAId + "/documentos");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("Baja Animal A.pdf").contains("BAJA_ANIMAL")
                .contains("application/pdf");
        // Ninguna fila de lectura: el listado de metadatos no se audita (solo la descarga).
        assertThat(acciones(gestoriaA)).isEmpty();
    }

    @Test
    void listarLosDocumentosDeUnGanaderoAjenoDa404YAccesoOtraGestoria() {
        ResponseEntity<String> r = get("/ganaderos/" + ganaderoBId + "/documentos");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody()).isNull();
        assertThat(acciones(gestoriaA)).containsExactly("ACCESO_OTRA_GESTORIA");
        assertThat(acciones(gestoriaB)).isEmpty();
    }

    @Test
    void descargarUnDocumentoPropioDevuelveElPdfComoAdjuntoYDejaLecturaDocumentoOvz() {
        ResponseEntity<byte[]> r = getBytes("/documentos-ovz/" + documentoAId + "/contenido");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).isEqualTo(pdfA);
        assertThat(r.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(r.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(r.getHeaders().getContentDisposition().getFilename()).isEqualTo("Baja Animal A.pdf");

        List<String> filas = acciones(gestoriaA);
        assertThat(filas).containsExactly("LECTURA_DOCUMENTO_OVZ");
    }

    @Test
    void descargarUnDocumentoAjenoDa404YAccesoOtraGestoria() {
        ResponseEntity<byte[]> r = getBytes("/documentos-ovz/" + documentoBId + "/contenido");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        // La fila queda en la gestoria del actor (A), nunca en B; y NUNCA LECTURA_DOCUMENTO_OVZ de un ajeno.
        assertThat(acciones(gestoriaA)).containsExactly("ACCESO_OTRA_GESTORIA");
        assertThat(acciones(gestoriaB)).isEmpty();
    }

    @Test
    void descargarUnDocumentoInexistenteDa404SinAcceso() {
        ResponseEntity<byte[]> r = getBytes("/documentos-ovz/999999/contenido");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(acciones(gestoriaA)).isEmpty();
    }

    // --------------------------------------------------------------- helpers

    /** Siembra ganadero + explotacion + tramite + documento en la gestoria. Devuelve {ganaderoId, documentoId}. */
    private long[] sembrarDocumento(Gestoria gestoria, String rega, String nombre, byte[] pdf) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + rega);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(rega);
        explotacion.setNombre("Finca " + rega);
        explotacionRepository.save(explotacion);
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.EJECUTADO_OVZ);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(tramite);
        DocumentoOvz doc = documentoOvzService.guardar(
                tramite, ganadero, explotacion, TipoDocumentoOvz.BAJA_ANIMAL, nombre, pdf, Actor.sistema());
        return new long[]{ganadero.getId(), doc.getId()};
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> getBytes(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private List<String> acciones(Gestoria gestoria) {
        return jdbcTemplate.queryForList(
                "select accion from auditoria where gestoria_id = ? order by id asc", String.class, gestoria.getId());
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario documentos");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
