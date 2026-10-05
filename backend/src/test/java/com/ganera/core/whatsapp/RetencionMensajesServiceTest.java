package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Retencion de mensajes (B1, T3, D6) sobre H2 con las migraciones reales. "Ahora" sale de un Clock
 * fijo en 2031 a proposito: una implementacion que usara la hora real (Instant.now() o now() en
 * SQL) no veria nada caducado y los tests fallarian.
 *
 * <p>Con los plazos por defecto (P30D y P12M) y AHORA = 2031-03-10T09:00Z (10:00 en Madrid):
 * borrar antes de 2031-02-08T09:00Z; vaciar antes de 2030-03-10T09:00Z.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class RetencionMensajesServiceTest {

    private static final Instant AHORA = Instant.parse("2031-03-10T09:00:00Z");
    private static final Instant LIMITE_BORRADO = Instant.parse("2031-02-08T09:00:00Z");
    private static final Instant LIMITE_VACIADO = Instant.parse("2030-03-10T09:00:00Z");
    private static final Duration UN_SEGUNDO = Duration.ofSeconds(1);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Contacto contactoA;
    private Contacto contactoB;
    private int secuencia;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria retencion A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria retencion B"));
        contactoA = contacto(gestoriaA, "+34600970001");
        contactoB = contacto(gestoriaB, "+34600970101");
    }

    private RetencionMensajesService servicio(Instant ahora, Period sinTramite, Period conTramite) {
        return new RetencionMensajesService(mensajeCampoRepository, Clock.fixed(ahora, ZoneOffset.UTC),
                sinTramite, conTramite);
    }

    private RetencionMensajesService porDefecto() {
        return servicio(AHORA, Period.ofDays(30), Period.ofMonths(12));
    }

    // ------------------------------------------------------------ borrado de mensajes sin tramite

    @Test
    void borraCadaResultadoSinTramiteJustoAntesDelLimiteYNoElDelLimiteNiElDeDespues() {
        ResultadoMensaje[] sinTramite = {ResultadoMensaje.NUMERO_DESCONOCIDO, ResultadoMensaje.CONTACTO_INACTIVO,
                ResultadoMensaje.ERROR_RECEPCION, null};
        for (ResultadoMensaje resultado : sinTramite) {
            String etiqueta = resultado == null ? "NULO" : resultado.name();
            mensaje("antes-" + etiqueta, resultado, null, LIMITE_BORRADO.minus(UN_SEGUNDO));
            mensaje("limite-" + etiqueta, resultado, null, LIMITE_BORRADO);
            mensaje("despues-" + etiqueta, resultado, null, LIMITE_BORRADO.plus(UN_SEGUNDO));
        }

        RetencionMensajesService.Resultado resultado = porDefecto().aplicar();

        assertThat(resultado.borrados()).isEqualTo(4);
        assertThat(resultado.vaciados()).isZero();
        assertThat(sids()).containsExactlyInAnyOrder(
                "limite-NUMERO_DESCONOCIDO", "despues-NUMERO_DESCONOCIDO",
                "limite-CONTACTO_INACTIVO", "despues-CONTACTO_INACTIVO",
                "limite-ERROR_RECEPCION", "despues-ERROR_RECEPCION",
                "limite-NULO", "despues-NULO");
    }

    @Test
    void borraLosMensajesSinTramiteDeCualquierGestoriaOSinGestoria() {
        MensajeCampo deA = mensaje("inactivo-A", ResultadoMensaje.CONTACTO_INACTIVO, null, LIMITE_BORRADO.minus(UN_SEGUNDO));
        deA.setGestoria(gestoriaA);
        MensajeCampo deB = mensaje("inactivo-B", ResultadoMensaje.CONTACTO_INACTIVO, null, LIMITE_BORRADO.minus(UN_SEGUNDO));
        deB.setGestoria(gestoriaB);
        mensaje("desconocido", ResultadoMensaje.NUMERO_DESCONOCIDO, null, LIMITE_BORRADO.minus(UN_SEGUNDO));
        entityManager.flush();

        assertThat(porDefecto().aplicar().borrados()).isEqualTo(3);
        assertThat(sids()).isEmpty();
    }

    @Test
    void nuncaBorraUnMensajeConTramiteAunqueSeaMuyAntiguo() {
        Tramite tramite = tramite(contactoA);
        mensaje("con-tramite-antiguo", ResultadoMensaje.TRAMITE_CREADO, tramite, Instant.parse("2020-01-01T00:00:00Z"));
        Tramite anterior = tramite(contactoB);
        // Fila anterior a B1 (resultado null) pero con Tramite: tampoco se borra.
        mensaje("con-tramite-sin-resultado", null, anterior, Instant.parse("2020-01-01T00:00:00Z"));

        RetencionMensajesService.Resultado resultado = porDefecto().aplicar();

        assertThat(resultado.borrados()).isZero();
        assertThat(sids()).containsExactlyInAnyOrder("con-tramite-antiguo", "con-tramite-sin-resultado");
    }

    // ------------------------------------------------------------ vaciado del texto con tramite

    @Test
    void vaciaSoloElTextoDeLosMensajesConTramiteQueHanPasadoElPlazo() {
        Tramite tramiteA = tramite(contactoA);
        Tramite tramiteB = tramite(contactoB);
        mensaje("antes-A", ResultadoMensaje.TRAMITE_CREADO, tramiteA, LIMITE_VACIADO.minus(UN_SEGUNDO));
        mensaje("antes-B", null, tramiteB, LIMITE_VACIADO.minus(UN_SEGUNDO));
        mensaje("limite", ResultadoMensaje.TRAMITE_CREADO, tramiteA, LIMITE_VACIADO);
        mensaje("despues", ResultadoMensaje.TRAMITE_CREADO, tramiteA, LIMITE_VACIADO.plus(UN_SEGUNDO));
        // Sin tramite y dentro de los 30 dias: ni se borra ni se vacia.
        mensaje("reciente-sin-tramite", ResultadoMensaje.NUMERO_DESCONOCIDO, null, AHORA.minus(Duration.ofDays(1)));
        Long versionA = tramiteRepository.findById(tramiteA.getId()).orElseThrow().getVersion();

        RetencionMensajesService.Resultado resultado = porDefecto().aplicar();

        assertThat(resultado.vaciados()).isEqualTo(2);
        assertThat(resultado.borrados()).isZero();
        Map<String, String> cuerpos = cuerpos();
        assertThat(cuerpos.get("antes-A")).isEqualTo(RetencionMensajesService.TEXTO_ELIMINADO);
        assertThat(cuerpos.get("antes-B")).isEqualTo(RetencionMensajesService.TEXTO_ELIMINADO);
        assertThat(cuerpos.get("limite")).isEqualTo("texto de limite");
        assertThat(cuerpos.get("despues")).isEqualTo("texto de despues");
        assertThat(cuerpos.get("reciente-sin-tramite")).isEqualTo("texto de reciente-sin-tramite");

        // El Tramite y sus metadatos no se tocan.
        Tramite despues = tramiteRepository.findById(tramiteA.getId()).orElseThrow();
        assertThat(despues.getVersion()).isEqualTo(versionA);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        MensajeCampo vaciado = mensajeCampoRepository.findAll().stream()
                .filter(m -> m.getMessageSid().equals("antes-A")).findFirst().orElseThrow();
        assertThat(vaciado.getTramite().getId()).isEqualTo(tramiteA.getId());
        assertThat(vaciado.getResultado()).isEqualTo(ResultadoMensaje.TRAMITE_CREADO);
        assertThat(vaciado.getTelefonoOrigen()).isEqualTo("+34600000000");
        assertThat(vaciado.getCreatedAt()).isEqualTo(LIMITE_VACIADO.minus(UN_SEGUNDO));
    }

    @Test
    void unaSegundaPasadaNoCambiaNada() {
        Tramite tramite = tramite(contactoA);
        mensaje("viejo-con-tramite", ResultadoMensaje.TRAMITE_CREADO, tramite, LIMITE_VACIADO.minus(UN_SEGUNDO));
        mensaje("viejo-sin-tramite", ResultadoMensaje.NUMERO_DESCONOCIDO, null, LIMITE_BORRADO.minus(UN_SEGUNDO));
        RetencionMensajesService servicio = porDefecto();

        RetencionMensajesService.Resultado primera = servicio.aplicar();
        RetencionMensajesService.Resultado segunda = servicio.aplicar();

        assertThat(primera).isEqualTo(new RetencionMensajesService.Resultado(1, 1));
        assertThat(segunda).isEqualTo(new RetencionMensajesService.Resultado(0, 0));
        assertThat(cuerpos()).containsExactly(Map.entry("viejo-con-tramite", RetencionMensajesService.TEXTO_ELIMINADO));
    }

    // ------------------------------------------------------------ plazos configurables

    @Test
    void respetaPlazosDistintosDeLosPorDefecto() {
        Tramite tramite = tramite(contactoA);
        // Plazos de 10 dias y 2 meses: AHORA - 10 dias = 2031-02-28T09:00Z; AHORA - 2 meses = 2031-01-10T09:00Z.
        mensaje("sin-tramite-11-dias", ResultadoMensaje.NUMERO_DESCONOCIDO, null, AHORA.minus(Duration.ofDays(11)));
        mensaje("sin-tramite-9-dias", ResultadoMensaje.NUMERO_DESCONOCIDO, null, AHORA.minus(Duration.ofDays(9)));
        mensaje("con-tramite-3-meses", ResultadoMensaje.TRAMITE_CREADO, tramite, Instant.parse("2030-12-10T09:00:00Z"));
        mensaje("con-tramite-1-mes", ResultadoMensaje.TRAMITE_CREADO, tramite, Instant.parse("2031-02-10T09:00:00Z"));

        RetencionMensajesService.Resultado resultado =
                servicio(AHORA, Period.ofDays(10), Period.ofMonths(2)).aplicar();

        assertThat(resultado).isEqualTo(new RetencionMensajesService.Resultado(1, 1));
        Map<String, String> cuerpos = cuerpos();
        assertThat(cuerpos).doesNotContainKey("sin-tramite-11-dias").containsKey("sin-tramite-9-dias");
        assertThat(cuerpos.get("con-tramite-3-meses")).isEqualTo(RetencionMensajesService.TEXTO_ELIMINADO);
        assertThat(cuerpos.get("con-tramite-1-mes")).isEqualTo("texto de con-tramite-1-mes");
    }

    @Test
    void elLimiteEnMesesSeCalculaEnLaHoraDeMadrid() {
        // 2031-03-31T23:30Z es el 1 de abril a la 01:30 en Madrid. Menos 1 mes en Madrid: 1 de marzo
        // 01:30 (CET) = 2031-03-01T00:30Z. En UTC seria 2031-02-28T23:30Z.
        Instant ahora = Instant.parse("2031-03-31T23:30:00Z");
        Tramite tramite = tramite(contactoA);
        mensaje("entre-los-dos-limites", ResultadoMensaje.TRAMITE_CREADO, tramite, Instant.parse("2031-03-01T00:00:00Z"));
        mensaje("despues-del-limite", ResultadoMensaje.TRAMITE_CREADO, tramite, Instant.parse("2031-03-01T00:31:00Z"));

        assertThat(servicio(ahora, Period.ofDays(30), Period.ofMonths(1)).aplicar().vaciados()).isEqualTo(1);
        assertThat(cuerpos().get("entre-los-dos-limites")).isEqualTo(RetencionMensajesService.TEXTO_ELIMINADO);
        assertThat(cuerpos().get("despues-del-limite")).isEqualTo("texto de despues-del-limite");
    }

    @Test
    void elLimiteEnDiasSeCalculaEnLaHoraDeMadridAunqueHayaCambioDeHora() {
        // 2031-04-05T09:00Z son las 11:00 CEST. Menos 30 dias en Madrid: 2031-03-06 11:00 CET =
        // 2031-03-06T10:00Z (entre medias, el cambio de hora del 30 de marzo). En UTC seria 09:00Z.
        Instant ahora = Instant.parse("2031-04-05T09:00:00Z");
        mensaje("entre-los-dos-limites", ResultadoMensaje.NUMERO_DESCONOCIDO, null, Instant.parse("2031-03-06T09:30:00Z"));
        mensaje("en-el-limite", ResultadoMensaje.NUMERO_DESCONOCIDO, null, Instant.parse("2031-03-06T10:00:00Z"));

        assertThat(servicio(ahora, Period.ofDays(30), Period.ofMonths(12)).aplicar().borrados()).isEqualTo(1);
        assertThat(sids()).containsExactly("en-el-limite");
    }

    @Test
    void unMensajeConTramiteSinTextoNoSeVaciaPasadoElPlazo() {
        Tramite tramite = tramite(contactoA);
        MensajeCampo sinTexto = mensaje("sin-texto", ResultadoMensaje.TRAMITE_CREADO, tramite,
                LIMITE_VACIADO.minus(Duration.ofDays(30)));
        sinTexto.setCuerpo("");
        mensajeCampoRepository.saveAndFlush(sinTexto);

        RetencionMensajesService.Resultado resultado = porDefecto().aplicar();

        assertThat(resultado.vaciados()).isZero();
        assertThat(cuerpos().get("sin-texto")).isEmpty();
    }

    @Test
    void losLimitesSalenDelRelojYDeLosPlazos() {
        RetencionMensajesService servicio = porDefecto();

        assertThat(servicio.limiteBorrado(AHORA)).isEqualTo(LIMITE_BORRADO);
        assertThat(servicio.limiteVaciado(AHORA)).isEqualTo(LIMITE_VACIADO);
    }

    // ------------------------------------------------------------ ayudas

    private Map<String, String> cuerpos() {
        entityManager.clear();
        return mensajeCampoRepository.findAll().stream()
                .collect(Collectors.toMap(MensajeCampo::getMessageSid, MensajeCampo::getCuerpo));
    }

    private java.util.List<String> sids() {
        entityManager.clear();
        return mensajeCampoRepository.findAll().stream().map(MensajeCampo::getMessageSid).toList();
    }

    private MensajeCampo mensaje(String sid, ResultadoMensaje resultado, Tramite tramite, Instant createdAt) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(sid);
        mensaje.setTelefonoOrigen("+34600000000");
        mensaje.setCuerpo("texto de " + sid);
        mensaje.setResultado(resultado);
        mensaje.setTramite(tramite);
        if (tramite != null) {
            mensaje.setGestoria(tramite.getGestoria());
            mensaje.setContacto(tramite.getContacto());
        }
        mensaje.setCreatedAt(createdAt);
        mensajeCampoRepository.saveAndFlush(mensaje);
        return mensaje;
    }

    private Tramite tramite(Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(contacto.getGestoria());
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.saveAndFlush(tramite);
    }

    private Contacto contacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto retencion " + (++secuencia));
        contacto.setActivo(true);
        return contactoRepository.save(contacto);
    }
}
