package com.ganera.core.ovz;

import com.ganera.core.contacto.TelefonoNormalizador;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Lee y guarda las preferencias OVZ de la gestoría del JWT. Si aún no hay fila, el GET responde
 * vacío y el PUT la crea. gestoriaId siempre del JWT.
 */
@Service
public class PreferenciaOvzService {

    static final String MOTIVO_TELEFONO_INVALIDO = "El teléfono de la solicitud de guía no es válido.";
    static final int MAX = 80;
    static final int MAX_MATRICULA = 40;
    static final int MAX_TELEFONO = 30;

    private final PreferenciaOvzRepository repository;
    private final GestoriaRepository gestoriaRepository;
    private final AuditoriaService auditoriaService;

    public PreferenciaOvzService(PreferenciaOvzRepository repository, GestoriaRepository gestoriaRepository,
                                 AuditoriaService auditoriaService) {
        this.repository = repository;
        this.gestoriaRepository = gestoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public PreferenciaOvzResponse obtener(Long gestoriaId) {
        return repository.findByGestoriaId(gestoriaId)
                .map(PreferenciaOvzResponse::from)
                .orElseGet(PreferenciaOvzResponse::vacia);
    }

    @Transactional
    public PreferenciaOvzResponse guardar(Long gestoriaId, Long usuarioId, PreferenciaOvzRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String telefono = normalizarTelefonoOpcional(body.telefonoMovimiento());
        PreferenciaOvz preferencia = repository.findByGestoriaId(gestoriaId).orElseGet(() -> {
            PreferenciaOvz nueva = new PreferenciaOvz();
            Gestoria gestoria = gestoriaRepository.findById(gestoriaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            nueva.setGestoria(gestoria);
            return nueva;
        });
        preferencia.setOficinaDi(recortar(body.oficinaDi(), MAX));
        preferencia.setOficinaMer(recortar(body.oficinaMer(), MAX));
        preferencia.setEmisionDi(recortar(body.emisionDi(), MAX));
        preferencia.setTipoIdentificador(recortar(body.tipoIdentificador(), MAX));
        preferencia.setTelefonoMovimiento(telefono);
        preferencia.setMedioTransporte(recortar(body.medioTransporte(), MAX_MATRICULA));
        preferencia.setMatricula(recortar(body.matricula(), MAX_MATRICULA));
        preferencia.setTransportistaSirentra(recortar(body.transportistaSirentra(), MAX_MATRICULA));
        preferencia.setTipoResponsable(recortar(body.tipoResponsable(), MAX));
        PreferenciaOvz guardada = repository.save(preferencia);
        auditoriaService.registrar(AccionAuditoria.PREFERENCIA_OVZ_GUARDADA, gestoriaId,
                Actor.usuario(usuarioId), EntidadAuditada.PREFERENCIA_OVZ, guardada.getId(), null, null);
        return PreferenciaOvzResponse.from(guardada);
    }

    private static String normalizarTelefonoOpcional(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return null;
        }
        Optional<String> normalizado = TelefonoNormalizador.normalizar(crudo);
        if (normalizado.isEmpty() || normalizado.get().length() > MAX_TELEFONO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MOTIVO_TELEFONO_INVALIDO);
        }
        return normalizado.get();
    }

    private static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un campo de preferencias supera la longitud máxima (" + max + ").");
        }
        return t;
    }
}
