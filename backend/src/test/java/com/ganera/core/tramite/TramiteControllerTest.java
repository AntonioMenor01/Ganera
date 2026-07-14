package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.TipoContacto;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
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

    @Test
    void listarFiltraPorEstadoCuandoSeIndica() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria listado"));
        Contacto contacto = nuevoContacto("+34600111222");

        guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        guardarTramite(gestoria, contacto, EstadoTramite.APROBADO);

        TramiteController controller = nuevoController();

        Page<TramiteResponse> pendientes = controller.listar(EstadoTramite.PENDIENTE_REVISION, PageRequest.of(0, 10));

        assertThat(pendientes.getContent()).hasSize(1);
        assertThat(pendientes.getContent().get(0).estado()).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void listarSinFiltroDevuelveTodosLosEstados() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria listado sin filtro"));
        Contacto contacto = nuevoContacto("+34600111223");

        guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        guardarTramite(gestoria, contacto, EstadoTramite.APROBADO);

        TramiteController controller = nuevoController();

        Page<TramiteResponse> todos = controller.listar(null, PageRequest.of(0, 10));

        assertThat(todos.getContent()).hasSize(2);
    }

    @Test
    void aprobarCambiaEstadoCuandoLaSuscripcionLoPermite() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria aprobar"));
        suscripcionActiva(gestoria);
        Contacto contacto = nuevoContacto("+34600111224");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteResponse> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().estado()).isEqualTo("APROBADO");
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

        Contacto contacto = nuevoContacto("+34600111225");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteResponse> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(tramiteRepository.findById(tramite.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarDevuelve404SiElTramiteNoExiste() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin tramite"));
        suscripcionActiva(gestoria);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteResponse> respuesta = controller.aprobar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), 999999L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rechazarCambiaEstado() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rechazar"));
        Contacto contacto = nuevoContacto("+34600111226");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteResponse> respuesta = controller.rechazar(tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().estado()).isEqualTo("RECHAZADO");
        assertThat(tramiteRepository.findById(tramite.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.RECHAZADO);
    }

    @Test
    void rechazarDevuelve404SiElTramiteNoExiste() {
        TramiteController controller = nuevoController();
        ResponseEntity<TramiteResponse> respuesta = controller.rechazar(999999L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void detalleIncluyeElMensajeOriginalYLaExplotacionResueltaCuandoExisten() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle"));
        Contacto contacto = nuevoContacto("+34600111227");
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES700000000001", "Finca detalle");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(TipoTramite.ALTA);
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
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.mensajeOriginal()).isEqualTo("Alta de 3 terneros en la finca");
        assertThat(cuerpo.tipoTramite()).isEqualTo("ALTA");
        assertThat(cuerpo.explotacionCodigoRega()).isEqualTo("ES700000000001");
        assertThat(cuerpo.explotacionNombre()).isEqualTo("Finca detalle");
    }

    @Test
    void detalleNoRompeCuandoNoHayMensajeNiExplotacionResuelta() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle sin resolver"));
        Contacto contacto = nuevoContacto("+34600111228");
        Tramite tramite = guardarTramite(gestoria, contacto, EstadoTramite.PENDIENTE_REVISION);

        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(tramite.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        TramiteDetalleResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.mensajeOriginal()).isNull();
        assertThat(cuerpo.explotacionId()).isNull();
        assertThat(cuerpo.tipoTramite()).isNull();
    }

    @Test
    void detalleDevuelve404SiElTramiteNoExiste() {
        TramiteController controller = nuevoController();
        ResponseEntity<TramiteDetalleResponse> respuesta = controller.detalle(999999L);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
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

    private TramiteController nuevoController() {
        return new TramiteController(
                tramiteRepository,
                new SuscripcionService(suscripcionRepository, gestoriaRepository),
                mensajeCampoRepository);
    }

    private void suscripcionActiva(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private Contacto nuevoContacto(String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto de prueba");
        contacto.setTipo(TipoContacto.TITULAR);
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
