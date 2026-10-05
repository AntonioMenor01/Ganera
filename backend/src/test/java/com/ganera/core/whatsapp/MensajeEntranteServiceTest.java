package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.tramite.EstadoExtraccion;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteCrotalRepository;
import com.ganera.core.tramite.TramiteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Recepcion de un mensaje (T1): enrutado por telefono, explotacion de D2, texto vacio, NumMedia e
 * idempotencia por MessageSid. Sin llamar a la IA (es de T2). Repositorios reales sobre H2 con las
 * migraciones de Flyway; la transaccion la pone @DataJpaTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class MensajeEntranteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T10:15:30Z");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;

    private MensajeEntranteService servicio;
    private Gestoria gestoriaA;
    private Gestoria gestoriaB;

    @BeforeEach
    void preparar() {
        servicio = new MensajeEntranteService(mensajeCampoRepository, contactoRepository,
                contactoExplotacionRepository, tramiteRepository, Clock.fixed(AHORA, ZoneOffset.UTC));
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria mensajes A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria mensajes B"));
    }

    // ------------------------------------------------------------ contacto activo

    @Test
    void contactoActivoConUnaExplotacionCreaElTramiteEnSuGestoriaConEsaExplotacion() {
        Contacto contacto = contacto(gestoriaA, "+34600960001", true);
        Explotacion explotacion = explotacion(gestoriaA, "ES960000000001");
        enlazar(gestoriaA, contacto, explotacion);

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-1", "whatsapp:+34600960001", "baja del 1234", 0));

        assertThat(recepcion.duplicado()).isFalse();
        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
        assertThat(recepcion.acusar()).isTrue();
        Tramite tramite = tramiteRepository.findByIdAndGestoriaId(recepcion.tramiteId(), gestoriaA.getId()).orElseThrow();
        assertThat(tramite.getContacto().getId()).isEqualTo(contacto.getId());
        assertThat(tramite.getExplotacion().getId()).isEqualTo(explotacion.getId());
        assertThat(tramite.getOrigen()).isEqualTo(OrigenTramite.WHATSAPP);
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_EXTRACCION);
        assertThat(tramite.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.PENDIENTE);
        assertThat(tramite.getProximoIntentoExtraccion()).isEqualTo(AHORA);
        assertThat(tramite.getIntentosExtraccion()).isZero();
        assertThat(tramite.getCrotalesDescartados()).isZero();
        assertThat(tramite.getVersionTrasFallo()).isNull();
        assertThat(tramite.getTipoTramite()).isNull();
        assertThat(tramiteCrotalRepository.findAll()).isEmpty();

        MensajeCampo mensaje = mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow();
        assertThat(mensaje.getTelefonoOrigen()).isEqualTo("+34600960001");
        assertThat(mensaje.getCuerpo()).isEqualTo("baja del 1234");
        assertThat(mensaje.getResultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
        assertThat(mensaje.getGestoria().getId()).isEqualTo(gestoriaA.getId());
        assertThat(mensaje.getContacto().getId()).isEqualTo(contacto.getId());
        assertThat(mensaje.getTramite().getId()).isEqualTo(tramite.getId());
    }

    @Test
    void contactoActivoSinExplotacionesDejaLaExplotacionNula() {
        contacto(gestoriaA, "+34600960002", true);
        explotacion(gestoriaA, "ES960000000002"); // existe, pero no esta enlazada al contacto

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-2", "whatsapp:+34600960002", "alta", 0));

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
        assertThat(tramite(recepcion).getExplotacion()).isNull();
    }

    @Test
    void contactoActivoConDosExplotacionesDejaLaExplotacionNula() {
        Contacto contacto = contacto(gestoriaA, "+34600960003", true);
        enlazar(gestoriaA, contacto, explotacion(gestoriaA, "ES960000000003"));
        enlazar(gestoriaA, contacto, explotacion(gestoriaA, "ES960000000004"));

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-3", "whatsapp:+34600960003", "censo", 0));

        assertThat(tramite(recepcion).getExplotacion()).isNull();
    }

    /**
     * Datos inconsistentes (se saltan la comprobacion de la decision 15): un enlace del contacto de
     * A a una explotacion de B, y un enlace con gestoria_id de B. Ninguno cuenta: el unico enlace
     * valido es el de A, asi que se asigna esa explotacion (y nunca la de B).
     */
    @Test
    void losEnlacesConExplotacionOGestoriaDeOtraGestoriaNoCuentan() {
        Contacto contacto = contacto(gestoriaA, "+34600960004", true);
        Explotacion propia = explotacion(gestoriaA, "ES960000000005");
        Explotacion ajena1 = explotacion(gestoriaB, "ES960000000105");
        Explotacion ajena2 = explotacion(gestoriaB, "ES960000000106");
        enlazar(gestoriaA, contacto, propia);
        enlazar(gestoriaA, contacto, ajena1);
        enlazar(gestoriaB, contacto, ajena2);

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-4", "whatsapp:+34600960004", "baja", 0));

        Tramite tramite = tramite(recepcion);
        assertThat(tramite.getGestoria().getId()).isEqualTo(gestoriaA.getId());
        assertThat(tramite.getExplotacion().getId()).isEqualTo(propia.getId());
    }

    @Test
    void cuerpoVacioOEnBlancoDejaElTramiteEnRevisionSinTexto() {
        Contacto contacto = contacto(gestoriaA, "+34600960005", true);
        enlazar(gestoriaA, contacto, explotacion(gestoriaA, "ES960000000006"));

        RecepcionMensaje vacio = servicio.registrar(entrante("SM-s-5a", "whatsapp:+34600960005", "", 1));
        RecepcionMensaje blanco = servicio.registrar(entrante("SM-s-5b", "whatsapp:+34600960005", "  \n\t ", 2));

        for (RecepcionMensaje recepcion : new RecepcionMensaje[]{vacio, blanco}) {
            assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
            assertThat(recepcion.acusar()).isTrue();
            Tramite tramite = tramite(recepcion);
            assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
            assertThat(tramite.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.SIN_TEXTO);
            assertThat(tramite.getProximoIntentoExtraccion()).isNull();
            assertThat(tramite.getOrigen()).isEqualTo(OrigenTramite.WHATSAPP);
            assertThat(tramite.getExplotacion()).isNotNull();
        }
        assertThat(mensajeCampoRepository.findById(blanco.mensajeId()).orElseThrow().getNumMedia()).isEqualTo(2);
    }

    // ------------------------------------------------------- inactivo / desconocido

    @Test
    void contactoInactivoGuardaElMensajeConSuGestoriaPeroNoCreaTramite() {
        Contacto contacto = contacto(gestoriaA, "+34600960006", false);
        enlazar(gestoriaA, contacto, explotacion(gestoriaA, "ES960000000007"));

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-6", "whatsapp:+34600960006", "baja del 1234", 0));

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.CONTACTO_INACTIVO);
        assertThat(recepcion.acusar()).isFalse();
        assertThat(recepcion.tramiteId()).isNull();
        assertThat(tramiteRepository.count()).isZero();
        MensajeCampo mensaje = mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow();
        assertThat(mensaje.getResultado()).isEqualTo(ResultadoMensaje.CONTACTO_INACTIVO);
        assertThat(mensaje.getGestoria().getId()).isEqualTo(gestoriaA.getId());
        assertThat(mensaje.getContacto().getId()).isEqualTo(contacto.getId());
        assertThat(mensaje.getTramite()).isNull();
    }

    @Test
    void numeroDesconocidoGuardaElMensajeSinGestoriaYNoCreaTramite() {
        contacto(gestoriaA, "+34600960007", true);

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-7", "whatsapp:+34600960999", "hola", 0));

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.NUMERO_DESCONOCIDO);
        assertThat(recepcion.acusar()).isFalse();
        assertThat(tramiteRepository.count()).isZero();
        MensajeCampo mensaje = mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow();
        assertThat(mensaje.getResultado()).isEqualTo(ResultadoMensaje.NUMERO_DESCONOCIDO);
        assertThat(mensaje.getTelefonoOrigen()).isEqualTo("+34600960999");
        assertThat(mensaje.getGestoria()).isNull();
        assertThat(mensaje.getContacto()).isNull();
        assertThat(mensaje.getTramite()).isNull();
    }

    @Test
    void unFromQueNoSeNormalizaSeGuardaTalCualComoNumeroDesconocido() {
        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-8", "whatsapp:basura", "hola", 0));

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.NUMERO_DESCONOCIDO);
        assertThat(mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow().getTelefonoOrigen())
                .isEqualTo("whatsapp:basura");
        assertThat(tramiteRepository.count()).isZero();
    }

    /** m2: telefono_origen es VARCHAR(30); un From no normalizable mas largo se trunca, no revienta. */
    @Test
    void unFromNoNormalizableDeMasDe30CaracteresSeGuardaTruncadoComoNumeroDesconocido() {
        String from = "whatsapp:canal-raro-0123456789abcdefghij"; // 40 caracteres
        assertThat(from).hasSize(40);

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-8b", from, "hola", 0));
        entityManager.flush();

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.NUMERO_DESCONOCIDO);
        assertThat(mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow().getTelefonoOrigen())
                .isEqualTo(from.substring(0, 30));
    }

    @Test
    void telefonoParaGuardarNormalizaOTruncaA30() {
        assertThat(new MensajeEntrante("SM", "whatsapp:+34 600 111 222", "", 0).telefonoParaGuardar())
                .isEqualTo("+34600111222");
        assertThat(new MensajeEntrante("SM", "x".repeat(45), "", 0).telefonoParaGuardar()).isEqualTo("x".repeat(30));
        assertThat(new MensajeEntrante("SM", "corto", "", 0).telefonoParaGuardar()).isEqualTo("corto");
    }

    /** n4: la fecha de recepcion sale del Clock (la purga de T3 se prueba con ella). */
    @Test
    void laFechaDeRecepcionDelMensajeSaleDelReloj() {
        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-12", "whatsapp:+34600960012", "hola", 0));

        assertThat(mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow().getCreatedAt())
                .isEqualTo(AHORA);
    }

    /** n2: el toString del record no puede llevar ni el texto ni el telefono (por si alguien lo loguea). */
    @Test
    void elToStringDeMensajeEntranteNoLlevaNiTextoNiTelefono() {
        String texto = new MensajeEntrante("SM-ts", "whatsapp:+34600960013", "baja del 1234 secreto", 2).toString();

        assertThat(texto).contains("SM-ts").contains("2")
                .doesNotContain("600960013").doesNotContain("baja").doesNotContain("secreto");
    }

    /** El From de Twilio llega como whatsapp:+34...; el Contacto se guardo normalizado (E.164). */
    @Test
    void elFromConPrefijoWhatsappYSeparadoresEncuentraAlContactoNormalizado() {
        Contacto contacto = contacto(gestoriaA, "+34600960009", true);

        RecepcionMensaje recepcion = servicio.registrar(entrante("SM-s-9", "WhatsApp:+34 600 96 00 09", "alta", 0));

        assertThat(recepcion.resultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
        assertThat(tramite(recepcion).getContacto().getId()).isEqualTo(contacto.getId());
    }

    // ------------------------------------------------------------ idempotencia

    @Test
    void unMessageSidYaGuardadoEsDuplicadoYNoCreaNadaMas() {
        contacto(gestoriaA, "+34600960010", true);
        servicio.registrar(entrante("SM-s-10", "whatsapp:+34600960010", "baja", 0));

        RecepcionMensaje repetido = servicio.registrar(entrante("SM-s-10", "whatsapp:+34600960010", "baja", 0));

        assertThat(repetido.duplicado()).isTrue();
        assertThat(repetido.acusar()).isFalse();
        assertThat(mensajeCampoRepository.count()).isEqualTo(1);
        assertThat(tramiteRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------ NumMedia

    @Test
    void numMediaSeLeeDelFormularioYSiFaltaONoEsNumeroEsCero() {
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", "b", "3").numMedia()).isEqualTo(3);
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", "b", null).numMedia()).isZero();
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", "b", "").numMedia()).isZero();
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", "b", "dos").numMedia()).isZero();
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", "b", "-1").numMedia()).isZero();
        assertThat(MensajeEntrante.desdeFormulario("SM", "f", null, "0").cuerpo()).isEmpty();
    }

    @Test
    void numMediaSeGuardaEnElMensaje() {
        RecepcionMensaje recepcion = servicio.registrar(
                MensajeEntrante.desdeFormulario("SM-s-11", "whatsapp:+34600960011", null, "2"));

        MensajeCampo mensaje = mensajeCampoRepository.findById(recepcion.mensajeId()).orElseThrow();
        assertThat(mensaje.getNumMedia()).isEqualTo(2);
        assertThat(mensaje.getCuerpo()).isEmpty();
    }

    // ------------------------------------------------------------ ayudas

    private static MensajeEntrante entrante(String sid, String from, String cuerpo, int numMedia) {
        return new MensajeEntrante(sid, from, cuerpo, numMedia);
    }

    private Tramite tramite(RecepcionMensaje recepcion) {
        entityManager.flush();
        entityManager.clear();
        return tramiteRepository.findByIdAndGestoriaId(recepcion.tramiteId(), gestoriaA.getId()).orElseThrow();
    }

    private Contacto contacto(Gestoria gestoria, String telefono, boolean activo) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto " + telefono);
        contacto.setActivo(activo);
        return contactoRepository.save(contacto);
    }

    private Explotacion explotacion(Gestoria gestoria, String codigoRega) {
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

    private void enlazar(Gestoria gestoriaDelEnlace, Contacto contacto, Explotacion explotacion) {
        ContactoExplotacion enlace = new ContactoExplotacion();
        enlace.setGestoria(gestoriaDelEnlace);
        enlace.setContacto(contacto);
        enlace.setExplotacion(explotacion);
        enlace.setRol(RolContacto.TITULAR);
        contactoExplotacionRepository.save(enlace);
    }
}
