package com.ganera.core.shared.auditoria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
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
import com.ganera.core.tramite.EstadoExtraccion;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.tramite.TipoTramite;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria base — T3: una fila por cada escritura de usuario (plan 2026-10-21, tabla «Cobertura»). Dos
 * gestorias reales A y B sobre un servidor real: cada accion deja su fila EXACTA en la gestoria del actor
 * (accion, actor USUARIO con su usuario_id del JWT, entidad, antes/despues, ip 127.0.0.1 y request_id = la
 * cabecera X-Request-Id de ESA respuesta) y NINGUNA en la otra gestoria; los casos sin cambio (409 de
 * version, DELETE de un contacto ya inactivo, 404) no dejan fila. El login va en AuditoriaLoginEndToEndTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class AuditoriaUsuarioEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
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
    private AnimalRepository animalRepository;
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
    private Explotacion explotacionA;
    private Contacto contactoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Auditoria E2E A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Auditoria E2E B"));
        usuarioA = crearUsuario(gestoriaA, "auditoriaA@test.com");
        crearUsuario(gestoriaB, "auditoriaB@test.com");
        tokenA = login("auditoriaA@test.com");
        suscripcion(gestoriaA);
        suscripcion(gestoriaB);
        explotacionA = nuevaExplotacion(gestoriaA, "ES970000000001");
        contactoA = nuevoContacto(gestoriaA, "+34600970001");
        // El login de arriba deja una fila LOGIN_CORRECTO (registrarAparte): se borra para que cada test
        // empiece con auditoria vacia y los recuentos/«sin fila» sean exactos.
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from tramite_evento");
        jdbcTemplate.update("delete from contacto_explotacion");
        jdbcTemplate.update("delete from tramite_crotal");
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        contactoRepository.deleteAll();
        animalRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // --------------------------------------------------------------- tramite

    @Test
    void patchDejaUnaFilaTramiteEditadoSoloEnLaGestoriaDelActor() throws IOException {
        Animal animal = nuevoAnimal(gestoriaA, explotacionA, "ES970000011234");
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, null);

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("version", version(tramite));
        cuerpo.put("explotacionId", explotacionA.getId());
        cuerpo.put("tipoTramite", "BAJA_MUERTE");
        cuerpo.put("crotales", List.of("1234")); // resuelve EN_INVENTARIO al animal de arriba (...1234)
        ResponseEntity<String> r = patch(tramite.getId(), cuerpo);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        Auditoria fila = ultima(gestoriaA);
        comun(fila, AccionAuditoria.TRAMITE_EDITADO, EntidadAuditada.TRAMITE, tramite.getId(), r);
        // antes: sin explotacion, sin tipo, sin crotales ni animales.
        assertThat(antes(fila).get("explotacion_id").isNull()).isTrue();
        assertThat(antes(fila).get("tipo_tramite").isNull()).isTrue();
        assertThat(antes(fila).get("num_crotales").asInt()).isZero();
        assertThat(antes(fila).get("animal_ids")).isEmpty();
        // despues: la explotacion, el tipo, un crotal y el id del animal resuelto (D8: lista de ids).
        assertThat(despues(fila).get("explotacion_id").asLong()).isEqualTo(explotacionA.getId());
        assertThat(despues(fila).get("tipo_tramite").asText()).isEqualTo("BAJA_MUERTE");
        assertThat(despues(fila).get("num_crotales").asInt()).isEqualTo(1);
        assertThat(despues(fila).get("animal_ids")).hasSize(1);
        assertThat(despues(fila).get("animal_ids").get(0).asLong()).isEqualTo(animal.getId());
        assertThat(filas(gestoriaB)).isEmpty();
    }

    @Test
    void aprobarDejaUnaFilaTramiteAprobado() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, TipoTramite.ALTA_NACIMIENTO);
        tramite.setExplotacion(explotacionA);
        tramiteRepository.save(tramite);

        ResponseEntity<String> r = post("/tramites/" + tramite.getId() + "/aprobar", version(tramite));
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        Auditoria fila = ultima(gestoriaA);
        comun(fila, AccionAuditoria.TRAMITE_APROBADO, EntidadAuditada.TRAMITE, tramite.getId(), r);
        assertThat(antes(fila).get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(despues(fila).get("estado").asText()).isEqualTo("APROBADO");
        assertThat(filas(gestoriaB)).isEmpty();
    }

    @Test
    void rechazarDejaUnaFilaTramiteRechazado() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, null);

        ResponseEntity<String> r = post("/tramites/" + tramite.getId() + "/rechazar", version(tramite));
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        comun(ultima(gestoriaA), AccionAuditoria.TRAMITE_RECHAZADO, EntidadAuditada.TRAMITE, tramite.getId(), r);
        assertThat(despues(ultima(gestoriaA)).get("estado").asText()).isEqualTo("RECHAZADO");
    }

    @Test
    void marcarYDesmarcarHechoOvzDejanSusFilas() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.APROBADO, TipoTramite.ALTA_NACIMIENTO);

        ResponseEntity<String> marcar = post("/tramites/" + tramite.getId() + "/marcar-hecho-ovz", version(tramite));
        assertThat(marcar.getStatusCode().value()).isEqualTo(200);
        Auditoria filaMarcar = ultima(gestoriaA);
        comun(filaMarcar, AccionAuditoria.TRAMITE_MARCADO_HECHO_OVZ, EntidadAuditada.TRAMITE, tramite.getId(), marcar);
        assertThat(antes(filaMarcar).get("estado").asText()).isEqualTo("APROBADO");
        assertThat(despues(filaMarcar).get("estado").asText()).isEqualTo("HECHO_EN_OVZ");

        ResponseEntity<String> desmarcar = post("/tramites/" + tramite.getId() + "/desmarcar-hecho-ovz", version(tramite));
        assertThat(desmarcar.getStatusCode().value()).isEqualTo(200);
        Auditoria filaDesmarcar = ultima(gestoriaA);
        comun(filaDesmarcar, AccionAuditoria.TRAMITE_DESMARCADO_HECHO_OVZ, EntidadAuditada.TRAMITE, tramite.getId(),
                desmarcar);
        assertThat(antes(filaDesmarcar).get("estado").asText()).isEqualTo("HECHO_EN_OVZ");
        assertThat(despues(filaDesmarcar).get("estado").asText()).isEqualTo("APROBADO");
    }

    @Test
    void descartarYDeshacerDejanSusFilas() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, null);
        tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TRAMITE);
        tramiteRepository.save(tramite);

        ResponseEntity<String> descartar = post("/tramites/" + tramite.getId() + "/descartar", version(tramite));
        assertThat(descartar.getStatusCode().value()).isEqualTo(200);
        Auditoria filaDescartar = ultima(gestoriaA);
        comun(filaDescartar, AccionAuditoria.TRAMITE_DESCARTADO, EntidadAuditada.TRAMITE, tramite.getId(), descartar);
        assertThat(antes(filaDescartar).get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(despues(filaDescartar).get("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(despues(filaDescartar).get("motivo").asText()).isEqualTo("SIN_TRAMITE");

        ResponseEntity<String> deshacer = post("/tramites/" + tramite.getId() + "/deshacer-descarte", version(tramite));
        assertThat(deshacer.getStatusCode().value()).isEqualTo(200);
        Auditoria filaDeshacer = ultima(gestoriaA);
        comun(filaDeshacer, AccionAuditoria.TRAMITE_DESCARTE_DESHECHO, EntidadAuditada.TRAMITE, tramite.getId(),
                deshacer);
        assertThat(antes(filaDeshacer).get("motivo").asText()).isEqualTo("SIN_TRAMITE");
        assertThat(despues(filaDeshacer).get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
    }

    /** El unico 409 que PERSISTE (noRollbackFor): la re-resolucion de crotales deja TRAMITE_CROTALES_RERESUELTOS. */
    @Test
    void aprobarQueReResuelveCrotalesDeja409YFilaReresueltos() throws IOException {
        nuevoAnimal(gestoriaA, explotacionA, "ES970000011234");
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, TipoTramite.BAJA_MUERTE);
        tramite.setExplotacion(explotacionA);
        tramiteRepository.save(tramite);
        // Un crotal que ahora resuelve EN_INVENTARIO (un unico animal con ...1234); se vera tras el PATCH.
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("version", version(tramite));
        cuerpo.put("crotales", List.of("1234"));
        assertThat(patch(tramite.getId(), cuerpo).getStatusCode().value()).isEqualTo(200);
        // El inventario cambia bajo los pies: aparece un segundo ...1234, asi que al aprobar el crotal
        // pasa de EN_INVENTARIO a AMBIGUO (la resolucion cambia respecto a lo que vio el revisor).
        nuevoAnimal(gestoriaA, explotacionA, "ES970000021234");

        ResponseEntity<String> r = post("/tramites/" + tramite.getId() + "/aprobar", version(tramite));
        assertThat(r.getStatusCode().value()).isEqualTo(409);

        List<Auditoria> filasA = filas(gestoriaA);
        Auditoria fila = filasA.get(filasA.size() - 1);
        comun(fila, AccionAuditoria.TRAMITE_CROTALES_RERESUELTOS, EntidadAuditada.TRAMITE, tramite.getId(), r);
    }

    @Test
    @SinGuardiaDml(value = "El escenario siembra trámites en el cuerpo; el 409 de versión no escribe ni "
            + "audita nada (eso es justo lo que comprueba).", tablas = "tramite")
    void un409DeVersionNoDejaFila() {
        Tramite tramite = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, null);
        long vieja = version(tramite);
        // Primero un rechazo valido sube la version; su fila es TRAMITE_RECHAZADO (esperada).
        // Para probar «sin fila» usamos una aprobacion con version desfasada sobre un tramite pendiente.
        Tramite otro = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, TipoTramite.ALTA_NACIMIENTO);
        otro.setExplotacion(explotacionA);
        tramiteRepository.save(otro);

        ResponseEntity<String> r = post("/tramites/" + otro.getId() + "/aprobar", vieja + 999);
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(filas(gestoriaA)).isEmpty();
    }

    @Test
    void un404NoDejaFila() {
        ResponseEntity<String> r = post("/tramites/999999/rechazar", 0L);
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(filas(gestoriaA)).isEmpty();
    }

    // --------------------------------------------------------------- contactos

    @Test
    void crearModificarDesactivarYReactivarContactoDejanSusFilas() throws IOException {
        ResponseEntity<String> crear = postJson("/contactos", "{\"telefono\":\"+34600970123\",\"nombre\":\"Nuevo\"}");
        assertThat(crear.getStatusCode().value()).isEqualTo(201);
        long id = objectMapper.readTree(crear.getBody()).get("id").asLong();
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_CREADO, EntidadAuditada.CONTACTO, id, crear);

        ResponseEntity<String> modificar = putJson("/contactos/" + id,
                "{\"telefono\":\"+34600970123\",\"nombre\":\"Cambiado\"}");
        assertThat(modificar.getStatusCode().value()).isEqualTo(200);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_MODIFICADO, EntidadAuditada.CONTACTO, id, modificar);

        ResponseEntity<String> borrar = exchange("/contactos/" + id, HttpMethod.DELETE, null);
        assertThat(borrar.getStatusCode().value()).isEqualTo(204);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_DESACTIVADO, EntidadAuditada.CONTACTO, id, borrar);

        // DELETE idempotente de uno ya inactivo: no cambia nada -> sin fila nueva.
        int filasAntes = filas(gestoriaA).size();
        assertThat(exchange("/contactos/" + id, HttpMethod.DELETE, null).getStatusCode().value()).isEqualTo(204);
        assertThat(filas(gestoriaA)).hasSize(filasAntes);

        ResponseEntity<String> reactivar = post("/contactos/" + id + "/reactivar", null);
        assertThat(reactivar.getStatusCode().value()).isEqualTo(200);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_REACTIVADO, EntidadAuditada.CONTACTO, id, reactivar);
    }

    @Test
    void vincularCambiarRolYDesvincularDejanSusFilas() throws IOException {
        ResponseEntity<String> vincular = postJson("/contactos/" + contactoA.getId() + "/explotaciones",
                "{\"explotacionId\":" + explotacionA.getId() + ",\"rol\":\"TITULAR\"}");
        assertThat(vincular.getStatusCode().value()).isEqualTo(200);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_VINCULADO, EntidadAuditada.CONTACTO, contactoA.getId(), vincular);

        ResponseEntity<String> rol = postJson("/contactos/" + contactoA.getId() + "/explotaciones",
                "{\"explotacionId\":" + explotacionA.getId() + ",\"rol\":\"EMPLEADO\"}");
        assertThat(rol.getStatusCode().value()).isEqualTo(200);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_ROL_CAMBIADO, EntidadAuditada.CONTACTO, contactoA.getId(), rol);

        // Volver a vincular con el MISMO rol no cambia nada -> sin fila nueva.
        int filasAntes = filas(gestoriaA).size();
        assertThat(postJson("/contactos/" + contactoA.getId() + "/explotaciones",
                "{\"explotacionId\":" + explotacionA.getId() + ",\"rol\":\"EMPLEADO\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(filas(gestoriaA)).hasSize(filasAntes);

        ResponseEntity<String> desvincular = exchange(
                "/contactos/" + contactoA.getId() + "/explotaciones/" + explotacionA.getId(), HttpMethod.DELETE, null);
        assertThat(desvincular.getStatusCode().value()).isEqualTo(204);
        comun(ultima(gestoriaA), AccionAuditoria.CONTACTO_DESVINCULADO, EntidadAuditada.CONTACTO, contactoA.getId(),
                desvincular);
    }

    // --------------------------------------------------------------- importacion Excel

    @Test
    void importarExcelDejaUnaFilaPorEntidadYUnResumen() throws IOException {
        byte[] excel = excelConUnaFilaPorHoja();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", new ByteArrayResource(excel) {
            @Override
            public String getFilename() {
                return "inventario.xlsx";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = restTemplate.exchange("/explotaciones/importar", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        List<AccionAuditoria> acciones = filas(gestoriaA).stream().map(Auditoria::getAccion).toList();
        assertThat(acciones).contains(AccionAuditoria.GANADERO_CREADO, AccionAuditoria.EXPLOTACION_CREADA,
                AccionAuditoria.ANIMAL_CREADO, AccionAuditoria.CONTACTO_CREADO, AccionAuditoria.CONTACTO_VINCULADO);
        // El resumen va al final (actor USUARIO, entidad IMPORTACION, sin id).
        Auditoria resumen = ultima(gestoriaA);
        assertThat(resumen.getAccion()).isEqualTo(AccionAuditoria.IMPORTACION_EXCEL);
        assertThat(resumen.getEntidadTipo()).isEqualTo(EntidadAuditada.IMPORTACION);
        assertThat(resumen.getEntidadId()).isNull();
        assertThat(resumen.getUsuarioId()).isEqualTo(usuarioA.getId());
        assertThat(resumen.getRequestId()).isEqualTo(r.getHeaders().getFirst("X-Request-Id"));
        assertThat(filas(gestoriaB)).isEmpty();

        // Reimportacion identica: las entidades no cambian -> solo el resumen deja fila.
        int antes = filas(gestoriaA).size();
        restTemplate.exchange("/explotaciones/importar", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        List<Auditoria> despues = filas(gestoriaA);
        assertThat(despues).hasSize(antes + 1);
        assertThat(despues.get(despues.size() - 1).getAccion()).isEqualTo(AccionAuditoria.IMPORTACION_EXCEL);
    }

    /** Una fila de Excel que falla (su REQUIRES_NEW hace rollback) no deja su fila de auditoria, pero el
     * resumen si (D9). La explotacion del Excel choca con el codigo_rega global de B: procesarExplotacion
     * crea el ganadero (con su GANADERO_CREADO) y al guardar la explotacion choca con el UNIQUE global, asi
     * que la transaccion de la fila revierte la fila ENTERA y su auditoria con ella; Animales y Contactos
     * referencian esa explotacion que en A no existe y tambien fallan. Solo queda IMPORTACION_EXCEL. */
    @Test
    void unaFilaDeExcelQueFallaNoDejaSuAuditoriaPeroSiElResumen() throws IOException {
        nuevaExplotacion(gestoriaB, "ES960000000001");

        ResponseEntity<String> r = importar(excelConUnaFilaPorHoja());
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        assertThat(filas(gestoriaA)).extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.IMPORTACION_EXCEL);
        assertThat(filas(gestoriaB)).isEmpty();
    }

    // --------------------------------------------------------------- helpers

    private ResponseEntity<String> importar(byte[] excel) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", new ByteArrayResource(excel) {
            @Override
            public String getFilename() {
                return "inventario.xlsx";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/explotaciones/importar", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private byte[] excelConUnaFilaPorHoja() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet expl = wb.createSheet("Explotaciones");
            fila(expl, 0, "codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero");
            fila(expl, 1, "ES960000000001", "Finca importada", "12345678U", "Ganadero importado");
            Sheet ani = wb.createSheet("Animales");
            fila(ani, 0, "crotal", "especie", "codigo_rega_explotacion");
            fila(ani, 1, "ES960000019999", "BOVINO", "ES960000000001");
            Sheet con = wb.createSheet("Contactos");
            fila(con, 0, "telefono", "nombre", "codigo_explotacion", "rol");
            fila(con, 1, "+34600960999", "Contacto importado", "ES960000000001", "TITULAR");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void fila(Sheet hoja, int fila, String... valores) {
        Row row = hoja.createRow(fila);
        for (int c = 0; c < valores.length; c++) {
            row.createCell(c).setCellValue(valores[c]);
        }
    }

    private void comun(Auditoria fila, AccionAuditoria accion, EntidadAuditada tipo, Long entidadId,
                       ResponseEntity<String> respuesta) {
        assertThat(fila.getAccion()).isEqualTo(accion);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioA.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(tipo);
        assertThat(fila.getEntidadId()).isEqualTo(entidadId);
        // Loopback local: TestRestTemplate conecta a 127.0.0.1 (o ::1 segun la pila).
        assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(fila.getRequestId()).isNotBlank()
                .isEqualTo(respuesta.getHeaders().getFirst("X-Request-Id"));
    }

    private JsonNode antes(Auditoria fila) throws IOException {
        return objectMapper.readTree(fila.getAntes());
    }

    private JsonNode despues(Auditoria fila) throws IOException {
        return objectMapper.readTree(fila.getDespues());
    }

    private List<Auditoria> filas(Gestoria g) {
        return auditoriaRepository.findByGestoriaIdOrderByIdAsc(g.getId());
    }

    private Auditoria ultima(Gestoria g) {
        List<Auditoria> filas = filas(g);
        assertThat(filas).isNotEmpty();
        return filas.get(filas.size() - 1);
    }

    private ResponseEntity<String> patch(Long id, Map<String, Object> cuerpo) {
        HttpHeaders headers = cabeceras(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/tramites/" + id, HttpMethod.PATCH, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private ResponseEntity<String> post(String path, Long version) {
        String cuerpo = version == null ? null : "{\"version\":" + version + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        if (cuerpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private ResponseEntity<String> postJson(String path, String json) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(json, cabeceras(MediaType.APPLICATION_JSON)), String.class);
    }

    private ResponseEntity<String> putJson(String path, String json) {
        return restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(json, cabeceras(MediaType.APPLICATION_JSON)), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod metodo, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, metodo, new HttpEntity<>(json, headers), String.class);
    }

    private HttpHeaders cabeceras(MediaType tipo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(tipo);
        return headers;
    }

    private long version(Tramite tramite) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramite.getId());
    }

    private Suscripcion suscripcion(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        return suscripcionRepository.save(suscripcion);
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

    private Animal nuevoAnimal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        return animalRepository.save(animal);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto auditoria E2E");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, EstadoTramite estado, TipoTramite tipo) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contactoA.getGestoria() == gestoria ? contactoA : nuevoContacto(gestoria, "+34600970500"));
        tramite.setEstado(estado);
        tramite.setTipoTramite(tipo);
        return tramiteRepository.save(tramite);
    }

    private Usuario crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario auditoria E2E");
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
