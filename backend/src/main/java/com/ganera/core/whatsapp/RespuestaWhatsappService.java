package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Envio de una respuesta por WhatsApp desde un Tramite (B2 reducido, D6/D7/D9/D11), en TRES pasos. El
 * servicio entero NO es @Transactional a proposito: la llamada a Twilio nunca va dentro de una transaccion
 * ni con el Tramite bloqueado (riesgo M2 de A1).
 *
 * <ol>
 *   <li><b>Transaccion corta:</b> Tramite con {@code findByIdAndGestoriaId} (sin bloqueo: no se toca) →
 *       404; clave de idempotencia ya usada → la guardada (mismo tramite y texto) o 409; si no,
 *       disponibilidad (409 con su motivo) → usuario del JWT → fila en ENVIANDO con el telefono ACTUAL del
 *       Contacto del Tramite. Si dos peticiones con la misma clave chocan en el UNIQUE, la excepcion sale
 *       de esa transaccion (revertida) y aqui fuera se relee la existente.</li>
 *   <li><b>Sin transaccion:</b> {@link EnvioWhatsapp#enviar}. Una sola vez; nunca se reintenta.</li>
 *   <li><b>Transaccion corta:</b> SID y ENVIADO, o FALLIDO con su codigo, o SIN_CONFIRMAR.</li>
 * </ol>
 *
 * <p>El destinatario sale SOLO de {@code tramite.contacto}; el texto se envia tal cual (la firma va en el
 * texto que propone el frontend, D3). La version y el estado del Tramite no cambian nunca. Sin comprobacion
 * de suscripcion (D11). Logs: solo ids, SID, estado y codigo; nunca el texto ni el telefono.
 */
@Slf4j
@Service
public class RespuestaWhatsappService {

    static final String MOTIVO_CLAVE_REUTILIZADA =
            "Esta respuesta ya se envió con otro texto. Escribe otra.";

    private final TramiteRepository tramiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final MensajeCampoRepository mensajeCampoRepository;
    private final RespuestaWhatsappRepository respuestaWhatsappRepository;
    private final EnvioWhatsapp envioWhatsapp;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public RespuestaWhatsappService(TramiteRepository tramiteRepository,
                                    UsuarioRepository usuarioRepository,
                                    MensajeCampoRepository mensajeCampoRepository,
                                    RespuestaWhatsappRepository respuestaWhatsappRepository,
                                    EnvioWhatsapp envioWhatsapp,
                                    Clock reloj,
                                    PlatformTransactionManager transactionManager) {
        this.tramiteRepository = tramiteRepository;
        this.usuarioRepository = usuarioRepository;
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.envioWhatsapp = envioWhatsapp;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** Si el envio esta configurado (sin el, el endpoint responde 503 sin crear nada). */
    public boolean configurado() {
        return envioWhatsapp.configurado();
    }

    /** Resultado de responder: la respuesta y si es nueva (201) o la ya guardada con esa clave (200). */
    public record Respuesta(RespuestaWhatsappResponse respuesta, boolean nueva) {
    }

    /** Lo que deja el paso 1: o una respuesta ya guardada con esa clave, o la fila nueva a enviar. */
    private record Preparada(RespuestaWhatsapp existente, Long nuevaId, String telefono) {
    }

    /**
     * @throws RecursoNoEncontradoException tramite de otra gestoria o inexistente (404)
     * @throws RespuestaWhatsappConflictoException clave reutilizada con otro texto/tramite, o no se puede
     *         responder (409 con su motivo)
     */
    public Respuesta responder(Long gestoriaId, Long usuarioId, Long tramiteId, String texto, String claveIdempotencia) {
        Preparada preparada;
        try {
            preparada = transaccion.execute(estado ->
                    preparar(gestoriaId, usuarioId, tramiteId, texto, claveIdempotencia));
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones con la misma clave a la vez: la otra gano el UNIQUE. Se relee la suya.
            Optional<RespuestaWhatsapp> ganadora = transaccion.execute(estado ->
                    respuestaWhatsappRepository.findByGestoriaIdAndClaveIdempotencia(gestoriaId, claveIdempotencia));
            if (ganadora == null || ganadora.isEmpty()) {
                throw e;
            }
            return new Respuesta(existente(ganadora.get(), tramiteId, texto), false);
        }
        if (preparada.existente() != null) {
            return new Respuesta(RespuestaWhatsappResponse.from(preparada.existente(), reloj.instant()), false);
        }

        ResultadoEnvioWhatsapp resultado;
        try {
            resultado = envioWhatsapp.enviar(preparada.telefono(), texto);
        } catch (RuntimeException e) {
            // El puerto no deberia lanzar; si lo hace, no sabemos si salio.
            log.warn("Envio por WhatsApp con excepcion inesperada: respuestaId={}, excepcion={}",
                    preparada.nuevaId(), e.getClass().getName());
            resultado = new ResultadoEnvioWhatsapp.SinConfirmar();
        }

        ResultadoEnvioWhatsapp finalResultado = resultado;
        RespuestaWhatsappResponse guardada = transaccion.execute(estado ->
                registrarResultado(gestoriaId, usuarioId, tramiteId, preparada.nuevaId(), finalResultado));
        return new Respuesta(guardada, true);
    }

    /** Paso 1, dentro de su transaccion. */
    private Preparada preparar(Long gestoriaId, Long usuarioId, Long tramiteId, String texto, String clave) {
        Tramite tramite = tramiteRepository.findByIdAndGestoriaId(tramiteId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        // Revision T1, N3: el Contacto del Tramite tiene que ser de la misma gestoria (solo datos sembrados a
        // mano lo romperian). Si no, 404 como un tramite ajeno: nunca se escribe a un contacto de otra gestoria.
        if (!ReglasRespuestaWhatsapp.contactoDeLaGestoria(tramite.getContacto(), gestoriaId)) {
            throw new RecursoNoEncontradoException();
        }
        Optional<RespuestaWhatsapp> conLaClave =
                respuestaWhatsappRepository.findByGestoriaIdAndClaveIdempotencia(gestoriaId, clave);
        if (conLaClave.isPresent()) {
            comprobarMismaPeticion(conLaClave.get(), tramiteId, texto);
            return new Preparada(conLaClave.get(), null, null);
        }

        Contacto contacto = tramite.getContacto();
        Instant ahora = ahora();
        Instant ventanaAbiertaHasta = ReglasRespuestaWhatsapp.ventanaAbiertaHasta(
                mensajeCampoRepository.ultimoMensajeDelContacto(contacto.getId(), gestoriaId));
        String motivo = ReglasRespuestaWhatsapp.motivoNoDisponible(tramite.getOrigen(), tramite.getEstado(),
                tramite.getMotivoRechazo(),
                contacto.isActivo(), ventanaAbiertaHasta, ahora);
        if (motivo != null) {
            throw new RespuestaWhatsappConflictoException(motivo);
        }
        Usuario usuario = usuarioRepository.findByIdAndGestoriaId(usuarioId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);

        RespuestaWhatsapp respuesta = new RespuestaWhatsapp();
        respuesta.setGestoria(tramite.getGestoria());
        respuesta.setTramite(tramite);
        respuesta.setContacto(contacto);
        respuesta.setTelefonoDestino(contacto.getTelefono());
        respuesta.setCuerpo(texto);
        respuesta.setEnviadoPor(usuario);
        respuesta.setCreatedAt(ahora);
        respuesta.setActualizadoEn(ahora);
        respuesta.setEstado(EstadoRespuestaWhatsapp.ENVIANDO);
        respuesta.setClaveIdempotencia(clave);
        respuestaWhatsappRepository.saveAndFlush(respuesta);
        return new Preparada(null, respuesta.getId(), contacto.getTelefono());
    }

    /** Paso 3, dentro de su transaccion. */
    private RespuestaWhatsappResponse registrarResultado(Long gestoriaId, Long usuarioId, Long tramiteId,
                                                         Long respuestaId, ResultadoEnvioWhatsapp resultado) {
        RespuestaWhatsapp respuesta = respuestaWhatsappRepository.findByIdAndGestoriaId(respuestaId, gestoriaId)
                .orElseThrow(() -> new IllegalStateException("La respuesta recien creada no existe: " + respuestaId));
        switch (resultado) {
            case ResultadoEnvioWhatsapp.Aceptado aceptado -> {
                respuesta.setEstado(EstadoRespuestaWhatsapp.ENVIADO);
                respuesta.setTwilioMessageSid(aceptado.messageSid());
            }
            case ResultadoEnvioWhatsapp.Rechazado rechazado -> {
                respuesta.setEstado(EstadoRespuestaWhatsapp.FALLIDO);
                respuesta.setCodigoErrorTwilio(rechazado.codigoError());
            }
            case ResultadoEnvioWhatsapp.SinConfirmar sinConfirmar ->
                    respuesta.setEstado(EstadoRespuestaWhatsapp.SIN_CONFIRMAR);
        }
        Instant ahora = ahora();
        respuesta.setActualizadoEn(ahora);
        respuestaWhatsappRepository.saveAndFlush(respuesta);
        log.info("Respuesta por WhatsApp: respuestaId={}, tramiteId={}, usuarioId={}, sid={}, estado={}, codigo={}",
                respuestaId, tramiteId, usuarioId, respuesta.getTwilioMessageSid(), respuesta.getEstado(),
                respuesta.getCodigoErrorTwilio());
        return RespuestaWhatsappResponse.from(respuesta, ahora);
    }

    private RespuestaWhatsappResponse existente(RespuestaWhatsapp respuesta, Long tramiteId, String texto) {
        comprobarMismaPeticion(respuesta, tramiteId, texto);
        return RespuestaWhatsappResponse.from(respuesta, reloj.instant());
    }

    /** Misma clave: solo vale si es el mismo tramite y el mismo texto; si no, 409. */
    private static void comprobarMismaPeticion(RespuestaWhatsapp respuesta, Long tramiteId, String texto) {
        if (!Objects.equals(respuesta.getTramite().getId(), tramiteId) || !Objects.equals(respuesta.getCuerpo(), texto)) {
            throw new RespuestaWhatsappConflictoException(MOTIVO_CLAVE_REUTILIZADA);
        }
    }

    /** Instant del Clock truncado a microsegundos: lo que devuelve la respuesta es lo que se guarda. */
    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
