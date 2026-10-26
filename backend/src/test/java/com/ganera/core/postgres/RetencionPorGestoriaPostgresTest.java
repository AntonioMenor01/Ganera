package com.ganera.core.postgres;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.whatsapp.EstadoRespuestaWhatsapp;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import com.ganera.core.whatsapp.RespuestaWhatsapp;
import com.ganera.core.whatsapp.RespuestaWhatsappRepository;
import com.ganera.core.whatsapp.ResultadoMensaje;
import com.ganera.core.whatsapp.RetencionMensajesService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retencion POR GESTORIA (T4, D6) contra PostgreSQL 17 real: los mismos totales que en H2 y una fila de auditoria por
 * gestoria y sentencia, cada una en la gestoria de su grupo. Complementa a RetencionMensajesServiceTest (H2); aqui lo
 * que importa es que el agrupado por gestoria y el conteo de filas se comportan igual sobre PostgreSQL. El trigger de
 * inmutabilidad de V25 no estorba: solo se INSERTAn filas de auditoria (nunca UPDATE/DELETE) y la base es nueva.
 */
class RetencionPorGestoriaPostgresTest {

    private static final Instant AHORA = Instant.parse("2031-03-10T09:00:00Z");
    private static final Instant LIMITE_BORRADO = Instant.parse("2031-02-08T09:00:00Z");
    private static final Instant LIMITE_VACIADO = Instant.parse("2030-03-10T09:00:00Z");
    private static final Duration UN_SEGUNDO = Duration.ofSeconds(1);

    @Test
    void laRetencionPorGestoriaDaLosMismosTotalesYUnaFilaDeAuditoriaPorGestoriaYSentencia() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarAplicacion(baseDeDatos, "validate")) {
            GestoriaRepository gestorias = contexto.getBean(GestoriaRepository.class);
            ContactoRepository contactos = contexto.getBean(ContactoRepository.class);
            UsuarioRepository usuarios = contexto.getBean(UsuarioRepository.class);
            TramiteRepository tramites = contexto.getBean(TramiteRepository.class);
            MensajeCampoRepository mensajes = contexto.getBean(MensajeCampoRepository.class);
            RespuestaWhatsappRepository respuestas = contexto.getBean(RespuestaWhatsappRepository.class);
            AuditoriaRepository auditoria = contexto.getBean(AuditoriaRepository.class);

            Gestoria gestoriaA = gestorias.save(new Gestoria("Gestoria retencion PG A"));
            Gestoria gestoriaB = gestorias.save(new Gestoria("Gestoria retencion PG B"));
            Contacto contactoA = contacto(contactos, gestoriaA, "+34600970001");

            // A: un mensaje sin tramite (con gestoria) a borrar, uno con tramite a vaciar y una respuesta a vaciar.
            MensajeCampo inactivoA = new MensajeCampo();
            inactivoA.setMessageSid("SM-inactivo-A");
            inactivoA.setTelefonoOrigen("+34600000000");
            inactivoA.setCuerpo("texto");
            inactivoA.setResultado(ResultadoMensaje.CONTACTO_INACTIVO);
            inactivoA.setGestoria(gestoriaA);
            inactivoA.setCreatedAt(LIMITE_BORRADO.minus(UN_SEGUNDO));
            mensajes.save(inactivoA);

            Tramite tramiteA = tramite(tramites, gestoriaA, contactoA);
            MensajeCampo conTramiteA = new MensajeCampo();
            conTramiteA.setMessageSid("SM-con-tramite-A");
            conTramiteA.setTelefonoOrigen("+34600000000");
            conTramiteA.setCuerpo("texto con tramite");
            conTramiteA.setResultado(ResultadoMensaje.TRAMITE_CREADO);
            conTramiteA.setGestoria(gestoriaA);
            conTramiteA.setContacto(contactoA);
            conTramiteA.setTramite(tramiteA);
            conTramiteA.setCreatedAt(LIMITE_VACIADO.minus(UN_SEGUNDO));
            mensajes.save(conTramiteA);

            Usuario usuarioA = usuario(usuarios, gestoriaA);
            RespuestaWhatsapp respuestaA = new RespuestaWhatsapp();
            respuestaA.setGestoria(gestoriaA);
            respuestaA.setTramite(tramiteA);
            respuestaA.setContacto(contactoA);
            respuestaA.setTelefonoDestino("+34600970001");
            respuestaA.setCuerpo("respuesta");
            respuestaA.setEnviadoPor(usuarioA);
            respuestaA.setCreatedAt(LIMITE_VACIADO.minus(UN_SEGUNDO));
            respuestaA.setActualizadoEn(LIMITE_VACIADO.minus(UN_SEGUNDO));
            respuestaA.setEstado(EstadoRespuestaWhatsapp.ENVIADO);
            respuestaA.setTwilioMessageSid("SID-resp-A");
            respuestaA.setClaveIdempotencia("clave-resp-A");
            respuestas.save(respuestaA);

            // Un mensaje sin gestoria (numero desconocido): se borra fuera de la auditoria.
            MensajeCampo desconocido = new MensajeCampo();
            desconocido.setMessageSid("SM-desconocido");
            desconocido.setTelefonoOrigen("+34600000000");
            desconocido.setCuerpo("hola");
            desconocido.setResultado(ResultadoMensaje.NUMERO_DESCONOCIDO);
            desconocido.setCreatedAt(LIMITE_BORRADO.minus(UN_SEGUNDO));
            mensajes.save(desconocido);

            RetencionMensajesService servicio = new RetencionMensajesService(
                    mensajes, respuestas, contexto.getBean(AuditoriaService.class),
                    contexto.getBean(PlatformTransactionManager.class),
                    Clock.fixed(AHORA, ZoneOffset.UTC), Period.ofDays(30), Period.ofMonths(12));

            RetencionMensajesService.Resultado resultado = servicio.aplicar();

            // Mismos totales que en H2: 2 borrados (A + sin gestoria), 1 vaciado, 1 respuesta vaciada.
            assertThat(resultado).isEqualTo(new RetencionMensajesService.Resultado(2, 1, 1));

            List<Auditoria> filasA = auditoria.findByGestoriaIdOrderByIdAsc(gestoriaA.getId());
            assertThat(filasA).extracting(Auditoria::getAccion).containsExactlyInAnyOrder(
                    AccionAuditoria.RETENCION_MENSAJES_BORRADOS,
                    AccionAuditoria.RETENCION_TEXTO_VACIADO,
                    AccionAuditoria.RETENCION_RESPUESTAS_VACIADAS);
            filasA.forEach(fila -> {
                assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.PLANIFICADOR);
                assertThat(fila.getUsuarioId()).isNull();
                assertThat(fila.getIp()).isNull();
                assertThat(fila.getRequestId()).isNull();
                assertThat(fila.getEntidadId()).isNull();
                assertThat(fila.getDespues()).contains("\"filas\":1").contains("\"limite\":");
            });
            assertThat(auditoria.findByGestoriaIdOrderByIdAsc(gestoriaB.getId())).isEmpty();
        }
    }

    private static Contacto contacto(ContactoRepository contactos, Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto retencion PG");
        contacto.setActivo(true);
        return contactos.save(contacto);
    }

    private static Tramite tramite(TramiteRepository tramites, Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramites.save(tramite);
    }

    private static Usuario usuario(UsuarioRepository usuarios, Gestoria gestoria) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail("retencion-pg@test.com");
        usuario.setPasswordHash("x");
        usuario.setNombre("Usuario retencion PG");
        usuario.setActivo(true);
        return usuarios.save(usuario);
    }
}
