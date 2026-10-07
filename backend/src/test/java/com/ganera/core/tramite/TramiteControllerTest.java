package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({TramiteCrotalService.class, TramiteRevisionService.class})
class TramiteControllerTest {

    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private TramiteCrotalService tramiteCrotalService;
    @Autowired
    private TramiteRevisionService tramiteRevisionService;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void listarFiltraPorEstadoCuandoSeIndica() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria listado"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111222");

        guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        guardarTramite(gestoria, contacto, EstadoTramite.APROBADO);

        TramiteController controller = nuevoController();

        Page<TramiteResponse> pendientes = pagina(controller.listar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"),
                EstadoTramite.PENDIENTE_REVISION, PageRequest.of(0, 10)));

        assertThat(pendientes.getContent()).hasSize(1);
        assertThat(pendientes.getContent().get(0).estado()).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void listarSinFiltroDevuelveTodosLosEstados() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria listado sin filtro"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111223");

        guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        guardarTramite(gestoria, contacto, EstadoTramite.APROBADO);

        TramiteController controller = nuevoController();

        Page<TramiteResponse> todos = pagina(controller.listar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), null, PageRequest.of(0, 10)));

        assertThat(todos.getContent()).hasSize(2);
    }

    @Test
    void aprobarCambiaEstadoCuandoLaSuscripcionLoPermite() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria aprobar"));
        suscripcionActiva(gestoria);
        Contacto contacto = nuevoContacto(gestoria, "+34600111224");
        // Regla de aprobacion (Task 6): hace falta explotacion y tipo para aprobar.
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(nuevaExplotacion(gestoria, "ES700000000020", "Finca aprobar"));
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramite);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId(), aprobarCon(tramite));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(((TramiteResponse) respuesta.getBody()).estado()).isEqualTo("APROBADO");
        assertThat(tramiteRepository.findById(tramite.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.APROBADO);
    }

    @Test
    void aprobarDevuelve403CuandoLaGestoriaNoPuedeAprobar() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria suspendida"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcion);

        Contacto contacto = nuevoContacto(gestoria, "+34600111225");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId(), aprobarCon(tramite));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR));
        assertThat(tramiteRepository.findById(tramite.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarDevuelve404SiElTramiteNoExiste() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin tramite"));
        suscripcionActiva(gestoria);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), 999999L, new TramiteAprobarRequest(0L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rechazarCambiaEstado() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111226");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.rechazar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId(), rechazarCon(tramite));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(((TramiteResponse) respuesta.getBody()).estado()).isEqualTo("RECHAZADO");
        assertThat(tramiteRepository.findById(tramite.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.RECHAZADO);
    }

    @Test
    void rechazarDevuelve404SiElTramiteNoExiste() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar sin tramite"));

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.rechazar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), 999999L, new TramiteRechazarRequest(0L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rechazarUnTramiteDeOtraGestoriaDevuelve404YNoLoCambia() {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena rechazar"));
        Gestoria gestoriaPropia = gestoriaRepository.save(new Gestoria("Gestoria propia rechazar"));
        Contacto contacto = nuevoContacto(gestoriaAjena, "+34600111229");
        Tramite tramiteAjeno = guardarTramite(gestoriaAjena, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.rechazar(
                new GaneraUserPrincipal(1L, gestoriaPropia.getId(), "empleado@test.com"), tramiteAjeno.getId(),
                rechazarCon(tramiteAjeno));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(tramiteRepository.findById(tramiteAjeno.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarUnTramiteDeOtraGestoriaDevuelve404YNoLoCambia() {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena aprobar"));
        Gestoria gestoriaPropia = gestoriaRepository.save(new Gestoria("Gestoria propia aprobar"));
        suscripcionActiva(gestoriaPropia);
        Contacto contacto = nuevoContacto(gestoriaAjena, "+34600111230");
        // Aprobable si fuera de la Gestoria propia: el 404 solo puede venir del aislamiento.
        Tramite tramiteAjeno = guardarTramite(gestoriaAjena, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramiteAjeno.setExplotacion(nuevaExplotacion(gestoriaAjena, "ES700000000021", "Finca ajena"));
        tramiteAjeno.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramiteAjeno);

        TramiteController controller = nuevoController();
        ResponseEntity<?> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoriaPropia.getId(), "empleado@test.com"), tramiteAjeno.getId(),
                aprobarCon(tramiteAjeno));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(tramiteRepository.findById(tramiteAjeno.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void detalleIncluyeElMensajeOriginalYLaExplotacionResueltaCuandoExisten() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111227");
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES700000000001", "Finca detalle");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramite);

        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid("SM-detalle-1");
        mensaje.setTelefonoOrigen("+34600111227");
        mensaje.setCuerpo("Alta de 3 terneros en la finca");
        mensaje.setGestoria(gestoria);
        mensaje.setContacto(contacto);
        mensaje.setTramite(tramite);
        mensajeCampoRepository.save(mensaje);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.mensajeOriginal()).isEqualTo("Alta de 3 terneros en la finca");
        assertThat(cuerpo.tipoTramite()).isEqualTo("ALTA_NACIMIENTO");
        assertThat(cuerpo.explotacionCodigoRega()).isEqualTo("ES700000000001");
        assertThat(cuerpo.explotacionNombre()).isEqualTo("Finca detalle");
        // Ficha OVZ (T2): el titular sale del ganadero de la explotacion; este no tiene NIF.
        assertThat(cuerpo.ganaderoNombre()).isEqualTo("Ganadero de prueba detalle");
        assertThat(cuerpo.ganaderoNif()).isNull();
    }

    @Test
    void detalleIncluyeElNombreYElNifDelGanaderoDeLaExplotacion() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle ganadero"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111299");
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES700000000099", "Finca titular");
        Ganadero ganadero = explotacion.getGanadero();
        ganadero.setNombre("Maria Lopez Garcia");
        ganadero.setNif("12345678Z");
        ganadero.setOvzUsuario("usuario-ovz-secreto");
        ganaderoRepository.save(ganadero);
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(explotacion);
        tramiteRepository.save(tramite);

        ResponseEntity<TramiteDetalleResponse> respuesta = nuevoController().detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.ganaderoNombre()).isEqualTo("Maria Lopez Garcia");
        assertThat(cuerpo.ganaderoNif()).isEqualTo("12345678Z");
        assertThat(cuerpo.toString()).doesNotContain("usuario-ovz-secreto");
    }

    @Test
    void detalleNoRompeCuandoNoHayMensajeNiExplotacionResuelta() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle sin resolver"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111228");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.mensajeOriginal()).isNull();
        assertThat(cuerpo.explotacionId()).isNull();
        assertThat(cuerpo.tipoTramite()).isNull();
        assertThat(cuerpo.ganaderoNombre()).isNull();
        assertThat(cuerpo.ganaderoNif()).isNull();
    }

    @Test
    void detalleDevuelve404SiElTramiteNoExiste() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle sin tramite"));

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), 999999L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void detalleDeUnTramiteDeOtraGestoriaDevuelve404NoLosDatos() {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena detalle"));
        Gestoria gestoriaPropia = gestoriaRepository.save(new Gestoria("Gestoria propia detalle"));
        Contacto contacto = nuevoContacto(gestoriaAjena, "+34600111231");
        Tramite tramiteAjeno = guardarTramite(gestoriaAjena, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(
                new GaneraUserPrincipal(1L, gestoriaPropia.getId(), "empleado@test.com"), tramiteAjeno.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    // --- crotales en listado y detalle (Task 5) ---

    @Test
    void listarIncluyeLosCrotalesDeCadaTramite() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria listado crotales"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111240");
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES700000000010", "Finca crotales");
        Animal animal = nuevoAnimal(gestoria, explotacion, "ES700000001234");
        Tramite conCrotales = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        conCrotales.setExplotacion(explotacion);
        tramiteRepository.save(conCrotales);
        Tramite sinCrotales = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(conCrotales, List.of("1234", "ES000000000000"), gestoria.getId());

        Page<TramiteResponse> pagina = pagina(nuevoController().listar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), null, PageRequest.of(0, 10)));

        Map<Long, TramiteResponse> porId = new java.util.HashMap<>();
        pagina.getContent().forEach(t -> porId.put(t.id(), t));
        assertThat(porId.get(conCrotales.getId()).crotales()).containsExactly(
                new TramiteCrotalResponse("1234", "ES700000001234", false, animal.getId(), true, "EN_INVENTARIO"),
                new TramiteCrotalResponse("ES000000000000", "ES000000000000", true, null, false, "NO_ENCONTRADO"));
        assertThat(porId.get(sinCrotales.getId()).crotales()).isEmpty();
        assertThat(porId.get(conCrotales.getId()).explotacionId()).isEqualTo(explotacion.getId());
        assertThat(porId.get(conCrotales.getId()).explotacionCodigoRega()).isEqualTo("ES700000000010");
        assertThat(porId.get(conCrotales.getId()).explotacionNombre()).isEqualTo("Finca crotales");
        assertThat(porId.get(sinCrotales.getId()).explotacionCodigoRega()).isNull();
        assertThat(porId.get(sinCrotales.getId()).explotacionNombre()).isNull();
    }

    /**
     * En @DataJpaTest el gestoriaFilter no esta activo. Desde la decision 21 el listado ya lleva el
     * gestoriaId del usuario como parametro real (findByGestoriaId), asi que B ni siquiera recibe el
     * Tramite de A -- y la carga en lote de crotales tampoco devuelve los de A aunque se le pida
     * explicitamente su id con el gestoriaId de B (antes esto se comprobaba solo a traves del
     * listado, que ahora ya no incluye el Tramite de A y dejaria la asercion vacia).
     */
    @Test
    void laCargaDeCrotalesDelListadoUsaElGestoriaIdDelUsuarioNoElFiltroAmbiente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A listado crotales"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B listado crotales"));
        Tramite tramiteA = guardarTramite(gestoriaA, nuevoContacto(gestoriaA, "+34600111241"), EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramiteA, List.of("4444"), gestoriaA.getId());
        Tramite tramiteB = guardarTramite(gestoriaB, nuevoContacto(gestoriaB, "+34600111243"), EstadoTramite.PENDIENTE_REVISION);

        Page<TramiteResponse> vistoPorB = pagina(nuevoController().listar(
                new GaneraUserPrincipal(2L, gestoriaB.getId(), "b@test.com"), null, PageRequest.of(0, 50)));

        assertThat(vistoPorB.getContent()).extracting(TramiteResponse::id).containsExactly(tramiteB.getId());
        vistoPorB.getContent().forEach(t -> assertThat(t.crotales()).isEmpty());
        assertThat(tramiteCrotalService.crotalesPorTramite(List.of(tramiteA.getId()), gestoriaB.getId())
                .getOrDefault(tramiteA.getId(), List.of())).isEmpty();
        assertThat(tramiteCrotalService.crotalesPorTramite(List.of(tramiteA.getId()), gestoriaA.getId())
                .get(tramiteA.getId())).extracting(TramiteCrotalResponse::crotalIndicado).containsExactly("4444");
    }

    @Test
    void listarConUnCampoDeOrdenacionNoPermitidoDevuelve400ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria orden tramites"));

        ResponseEntity<?> respuesta = nuevoController().listar(principal(gestoria), null,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("contacto.telefono")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(
                new MotivoErrorResponse(com.ganera.core.shared.web.OrdenacionPermitida.MOTIVO));
    }

    @Test
    void detalleIncluyeLosCrotalesDelTramite() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle crotales"));
        Contacto contacto = nuevoContacto(gestoria, "+34600111242");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("5555"), gestoria.getId());

        ResponseEntity<TramiteDetalleResponse> respuesta = nuevoController().detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getBody().crotales()).containsExactly(
                new TramiteCrotalResponse("5555", "5555", false, null, false, "SIN_EXPLOTACION"));
    }

    @Test
    void detalleSinCrotalesDevuelveListaVacia() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle sin crotales"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111243"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<TramiteDetalleResponse> respuesta = nuevoController().detalle(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getBody().crotales()).isNotNull().isEmpty();
    }

    // --- revision: PATCH, reglas de aprobar/rechazar y traduccion de errores (Task 6) ---

    @Test
    void patchDevuelveElDetalleActualizado() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch"));
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES700000000030", "Finca patch");
        Animal animal = nuevoAnimal(gestoria, explotacion, "ES700000031234");
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111250"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(version(tramite), explotacion.getId(), " baja_muerte ", List.of("12-34")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = (TramiteDetalleResponse) respuesta.getBody();
        assertThat(cuerpo.explotacionId()).isEqualTo(explotacion.getId());
        assertThat(cuerpo.tipoTramite()).isEqualTo("BAJA_MUERTE");
        assertThat(cuerpo.crotales()).containsExactly(
                new TramiteCrotalResponse("1234", "ES700000031234", false, animal.getId(), true, "EN_INVENTARIO"));
    }

    @Test
    void patchConTipoInvalidoDevuelve400ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch tipo"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111251"), EstadoTramite.PENDIENTE_REVISION);

        // Los tipos de antes de la V20 (ficha OVZ) tampoco valen.
        for (String tipo : List.of("TRASLADO", "", "  ", "ALTA", "BAJA", "MOVIMIENTO", "CENSO", "DEMORA")) {
            ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                    new TramitePatchRequest(version(tramite), null, tipo, null));

            assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
            assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_TIPO_INVALIDO));
        }
        assertThat(tramite.getTipoTramite()).isNull();
    }

    @Test
    void patchConCrotalInvalidoDevuelve400ConElMotivoDelNormalizador() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch crotal"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111252"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(version(tramite), null, null, List.of("123")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(
                new MotivoErrorResponse("Crotal demasiado corto: indica al menos los últimos 4 dígitos"));
    }

    @Test
    void patchConExplotacionDeOtraGestoriaDevuelve404SinCuerpo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch propia"));
        Gestoria ajena = gestoriaRepository.save(new Gestoria("Gestoria patch ajena"));
        Explotacion explotacionAjena = nuevaExplotacion(ajena, "ES700000000031", "Finca ajena patch");
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111253"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(version(tramite), explotacionAjena.getId(), null, null));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNull();
        assertThat(tramite.getExplotacion()).isNull();
    }

    @Test
    void patchDeUnTramiteDeOtraGestoriaDevuelve404() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch B"));
        Gestoria ajena = gestoriaRepository.save(new Gestoria("Gestoria patch A"));
        Tramite tramiteAjeno = guardarTramite(ajena, nuevoContacto(ajena, "+34600111254"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramiteAjeno.getId(),
                new TramitePatchRequest(version(tramiteAjeno), null, "ALTA_NACIMIENTO", null));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNull();
        assertThat(tramiteAjeno.getTipoTramite()).isNull();
    }

    @Test
    void patchFueraDePendienteRevisionDevuelve409ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch aprobado"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111255"), EstadoTramite.APROBADO);

        ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(version(tramite), null, "ALTA_NACIMIENTO", null));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody()).isEqualTo(
                new MotivoErrorResponse("Solo se puede editar un trámite pendiente de revisión."));
    }

    @Test
    void aprobarSinExplotacionNiTipoDevuelve409ConMotivoYNoCambiaElEstado() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria aprobar incompleto"));
        suscripcionActiva(gestoria);
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111256"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().aprobar(principal(gestoria), tramite.getId(), aprobarCon(tramite));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody()).isEqualTo(
                new MotivoErrorResponse("Falta asignar la explotación. Falta el tipo de trámite."));
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarYRechazarDesdeAprobadoDevuelven409() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria reaprobar"));
        suscripcionActiva(gestoria);
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111257"), EstadoTramite.APROBADO);
        tramite.setExplotacion(nuevaExplotacion(gestoria, "ES700000000032", "Finca reaprobar"));
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramite);

        ResponseEntity<?> aprobar = nuevoController().aprobar(principal(gestoria), tramite.getId(), aprobarCon(tramite));
        ResponseEntity<?> rechazar = nuevoController().rechazar(principal(gestoria), tramite.getId(), rechazarCon(tramite));

        assertThat(aprobar.getStatusCode().value()).isEqualTo(409);
        assertThat(aprobar.getBody()).isEqualTo(
                new MotivoErrorResponse("Solo se puede aprobar un trámite pendiente de revisión."));
        assertThat(rechazar.getStatusCode().value()).isEqualTo(409);
        assertThat(rechazar.getBody()).isEqualTo(
                new MotivoErrorResponse("Solo se puede rechazar un trámite pendiente de revisión."));
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.APROBADO);
    }

    /** Orden de comprobaciones: la suscripcion va primero, antes incluso del estado. */
    @Test
    void aprobarSinSuscripcionDevuelve403AunqueElTramiteNoEsteEnRevision() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin suscripcion"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111258"), EstadoTramite.APROBADO);

        ResponseEntity<?> respuesta = nuevoController().aprobar(principal(gestoria), tramite.getId(), aprobarCon(tramite));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
    }

    // --- version optimista (decision 27) ---

    /** Sin version (sin cuerpo o con version null) -> 400 con motivo y nada cambia. El 400 sale
     * antes de buscar el Tramite: es identico para uno propio, uno de otra Gestoria o uno que no
     * existe (no revela nada). */
    @Test
    void aprobarSinVersionDevuelve400ConMotivoSinTocarNada() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria aprobar sin version"));
        suscripcionActiva(gestoria);
        Gestoria ajena = gestoriaRepository.save(new Gestoria("Gestoria ajena aprobar sin version"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111260"), EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(nuevaExplotacion(gestoria, "ES700000000060", "Finca sin version"));
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramite);
        Tramite tramiteAjeno = guardarTramite(ajena, nuevoContacto(ajena, "+34600111261"), EstadoTramite.PENDIENTE_REVISION);
        long versionAntes = version(tramite);

        for (Long id : List.of(tramite.getId(), tramiteAjeno.getId(), 999999L)) {
            for (TramiteAprobarRequest cuerpo : java.util.Arrays.asList(null, new TramiteAprobarRequest(null))) {
                ResponseEntity<?> respuesta = nuevoController().aprobar(principal(gestoria), id, cuerpo);

                assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(400);
                assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_FALTA_VERSION));
            }
        }
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(versionAntes);
    }

    @Test
    void patchSinVersionDevuelve400ConMotivoSinTocarNada() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria patch sin version"));
        Gestoria ajena = gestoriaRepository.save(new Gestoria("Gestoria ajena patch sin version"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111262"), EstadoTramite.PENDIENTE_REVISION);
        Tramite tramiteAjeno = guardarTramite(ajena, nuevoContacto(ajena, "+34600111263"), EstadoTramite.PENDIENTE_REVISION);
        long versionAntes = version(tramite);

        for (Long id : List.of(tramite.getId(), tramiteAjeno.getId(), 999999L)) {
            ResponseEntity<?> respuesta = nuevoController().actualizar(principal(gestoria), id,
                    new TramitePatchRequest(null, null, "BAJA_MUERTE", List.of("1234")));

            assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(400);
            assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_FALTA_VERSION));
        }
        assertThat(tramite.getTipoTramite()).isNull();
        assertThat(version(tramite)).isEqualTo(versionAntes);
    }

    /** El 403 de suscripcion sigue siendo lo primero, antes que la version ausente. */
    @Test
    void aprobarSinSuscripcionYSinVersionDevuelve403() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin suscripcion ni version"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111264"), EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<?> respuesta = nuevoController().aprobar(principal(gestoria), tramite.getId(), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR));
    }

    /** Plan 2026-10-04 (quitar el pago de la app, D2): el texto del 403 ya no remite a Facturacion
     * (la pagina desaparece) sino a contactar con Ganera; el frontend usa el mismo texto de reserva. */
    @Test
    void elMotivoDel403PideContactarConGaneraYNoRemiteAFacturacion() {
        assertThat(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR).isEqualTo(
                "Tu suscripción no permite aprobar trámites ahora mismo (prueba terminada o suscripción "
                        + "suspendida). Ponte en contacto con Ganera para regularizarla.");
    }

    /** Mini-prompt tras A2 (punto 4): rechazar sin version (sin cuerpo o version null) -> 400 con
     * motivo, ANTES de buscar el Tramite: identico para uno propio, uno de otra Gestoria o uno que
     * no existe, y nada cambia. */
    @Test
    void rechazarSinVersionDevuelve400ConMotivoSinTocarNada() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar sin version"));
        Gestoria ajena = gestoriaRepository.save(new Gestoria("Gestoria ajena rechazar sin version"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111266"), EstadoTramite.PENDIENTE_REVISION);
        Tramite tramiteAjeno = guardarTramite(ajena, nuevoContacto(ajena, "+34600111267"), EstadoTramite.PENDIENTE_REVISION);
        long versionAntes = version(tramite);
        long versionAjenoAntes = version(tramiteAjeno);

        for (Long id : List.of(tramite.getId(), tramiteAjeno.getId(), 999999L)) {
            for (TramiteRechazarRequest cuerpo : java.util.Arrays.asList(null, new TramiteRechazarRequest(null))) {
                ResponseEntity<?> respuesta = nuevoController().rechazar(principal(gestoria), id, cuerpo);

                assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(400);
                assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_FALTA_VERSION));
            }
        }
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(versionAntes);
        assertThat(tramiteAjeno.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramiteAjeno)).isEqualTo(versionAjenoAntes);
    }

    @Test
    void rechazarConVersionDesfasadaDevuelve409ConMotivoSinTocarNada() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar desfasada"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111268"), EstadoTramite.PENDIENTE_REVISION);
        long vieja = version(tramite);
        assertThat(nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(vieja, null, "BAJA_MUERTE", null)).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<?> respuesta = nuevoController().rechazar(principal(gestoria), tramite.getId(),
                new TramiteRechazarRequest(vieja));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteRevisionService.MOTIVO_VERSION_DESFASADA));
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(vieja + 1);
    }

    @Test
    void rechazarConLaVersionActualDevuelve200YLaIncrementaEnUno() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar version buena"));
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111269"), EstadoTramite.PENDIENTE_REVISION);
        long v0 = version(tramite);

        ResponseEntity<?> respuesta = nuevoController().rechazar(principal(gestoria), tramite.getId(),
                new TramiteRechazarRequest(v0));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteResponse cuerpo = (TramiteResponse) respuesta.getBody();
        assertThat(cuerpo.estado()).isEqualTo("RECHAZADO");
        assertThat(cuerpo.version()).isEqualTo(v0 + 1);
        assertThat(version(tramite)).isEqualTo(v0 + 1);
    }

    @Test
    void patchYAprobarConVersionDesfasadaDevuelven409ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria version desfasada"));
        suscripcionActiva(gestoria);
        Tramite tramite = guardarTramite(gestoria, nuevoContacto(gestoria, "+34600111265"), EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(nuevaExplotacion(gestoria, "ES700000000065", "Finca desfasada"));
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramiteRepository.save(tramite);
        long vieja = version(tramite);
        assertThat(nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(vieja, null, "BAJA_MUERTE", null)).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<?> patch = nuevoController().actualizar(principal(gestoria), tramite.getId(),
                new TramitePatchRequest(vieja, null, "ALTA_NACIMIENTO", null));
        ResponseEntity<?> aprobar = nuevoController().aprobar(principal(gestoria), tramite.getId(),
                new TramiteAprobarRequest(vieja));

        for (ResponseEntity<?> respuesta : List.of(patch, aprobar)) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(respuesta.getBody()).isEqualTo(
                    new MotivoErrorResponse(TramiteRevisionService.MOTIVO_VERSION_DESFASADA));
        }
        assertThat(tramite.getTipoTramite()).isEqualTo(TipoTramite.BAJA_MUERTE);
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(vieja + 1);
    }

    private static GaneraUserPrincipal principal(Gestoria gestoria) {
        return new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com");
    }

    private Animal nuevoAnimal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        return animalRepository.save(animal);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega, String nombre) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba detalle");
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre(nombre);
        return explotacionRepository.save(explotacion);
    }

    @SuppressWarnings("unchecked")
    private static Page<TramiteResponse> pagina(ResponseEntity<?> respuesta) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return (Page<TramiteResponse>) respuesta.getBody();
    }

    /** Version actual del Tramite (tras volcar lo pendiente): la que mostraria la pantalla. */
    private long version(Tramite tramite) {
        entityManager.flush();
        return tramite.getVersion();
    }

    private TramiteAprobarRequest aprobarCon(Tramite tramite) {
        return new TramiteAprobarRequest(version(tramite));
    }

    private TramiteRechazarRequest rechazarCon(Tramite tramite) {
        return new TramiteRechazarRequest(version(tramite));
    }

    private TramiteController nuevoController() {
        return new TramiteController(
                tramiteRepository,
                new SuscripcionService(suscripcionRepository, gestoriaRepository),
                mensajeCampoRepository,
                tramiteCrotalService,
                tramiteRevisionService);
    }

    private void suscripcionActiva(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto de prueba");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite guardarTramite(Gestoria gestoria, Contacto contacto, EstadoTramite estado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(estado);
        return tramiteRepository.save(tramite);
    }
}
