package com.ganera.core.shared.auditoria;

import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.springframework.stereotype.Component;

/**
 * Audita el intento de acceso a un recurso de OTRA gestoria (plan 2026-10-21, T5/D3). Lo llaman los controladores
 * EN EL PUNTO 404, DESPUES de no encontrar el recurso en la gestoria propia: comprueba con
 * {@code existsByIdAndGestoriaIdNot(id, gestoriaIdDelJwt)} (consulta con el gestoriaId explicito, devuelve un
 * booleano, NUNCA datos) si el id existe en otra gestoria y, si es asi, deja una fila {@code ACCESO_OTRA_GESTORIA}
 * en la gestoria DEL ACTOR (la del JWT), con la entidad y la operacion como codigos, nunca el gestoria_id de la
 * victima ni dato suyo alguno. Si no es ajeno (no existe en ninguna otra), no deja fila.
 *
 * <p>La respuesta al cliente NO cambia: el 404 de un recurso ajeno es byte a byte igual al de uno inexistente. La
 * fila se escribe con {@link AuditoriaService#registrarAparte} ({@code REQUIRES_NEW}), asi que SOBREVIVE al rollback
 * de una escritura (PATCH/aprobar…) cuyo servicio ya revirtio su transaccion antes de llegar aqui.
 *
 * <p>Ids en el cuerpo: en el PATCH de tramites y en el vinculo de contactos, si el id de RUTA es propio pero aun asi
 * hubo 404, el 404 lo causo el {@code explotacionId} del cuerpo; se comprueba entonces ese id (ver
 * {@link #patchTramite} y {@link #vinculo}). Si el id de ruta es ajeno, se audita ese y punto; si no existe en
 * ninguna parte, no se mira el cuerpo (la peticion murio en la ruta).
 */
@Component
public class AccesoAjeno {

    private final AuditoriaService auditoriaService;
    private final TramiteRepository tramiteRepository;
    private final GanaderoRepository ganaderoRepository;
    private final ExplotacionRepository explotacionRepository;
    private final ContactoRepository contactoRepository;

    public AccesoAjeno(AuditoriaService auditoriaService, TramiteRepository tramiteRepository,
                       GanaderoRepository ganaderoRepository, ExplotacionRepository explotacionRepository,
                       ContactoRepository contactoRepository) {
        this.auditoriaService = auditoriaService;
        this.tramiteRepository = tramiteRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.explotacionRepository = explotacionRepository;
        this.contactoRepository = contactoRepository;
    }

    /** Audita si el Tramite de ese id es de otra gestoria. Devuelve true si era ajeno (dejo fila). */
    public boolean tramite(Long usuarioId, Long gestoriaId, Long id, OperacionAuditada operacion) {
        return auditarSiAjeno(tramiteRepository.existsByIdAndGestoriaIdNot(id, gestoriaId),
                usuarioId, gestoriaId, EntidadAuditada.TRAMITE, id, operacion);
    }

    /** Audita si el Ganadero de ese id es de otra gestoria. */
    public boolean ganadero(Long usuarioId, Long gestoriaId, Long id, OperacionAuditada operacion) {
        return auditarSiAjeno(ganaderoRepository.existsByIdAndGestoriaIdNot(id, gestoriaId),
                usuarioId, gestoriaId, EntidadAuditada.GANADERO, id, operacion);
    }

    /** Audita si la Explotacion de ese id es de otra gestoria. */
    public boolean explotacion(Long usuarioId, Long gestoriaId, Long id, OperacionAuditada operacion) {
        return auditarSiAjeno(explotacionRepository.existsByIdAndGestoriaIdNot(id, gestoriaId),
                usuarioId, gestoriaId, EntidadAuditada.EXPLOTACION, id, operacion);
    }

    /** Audita si el Contacto de ese id es de otra gestoria. */
    public boolean contacto(Long usuarioId, Long gestoriaId, Long id, OperacionAuditada operacion) {
        return auditarSiAjeno(contactoRepository.existsByIdAndGestoriaIdNot(id, gestoriaId),
                usuarioId, gestoriaId, EntidadAuditada.CONTACTO, id, operacion);
    }

    /**
     * PATCH /tramites/{id}: primero el Tramite de la ruta ({@code EDITAR_TRAMITE}); si es ajeno, se audita y nada
     * mas. Si el Tramite de la ruta es PROPIO pero hubo 404, el 404 lo causo el {@code explotacionId} del cuerpo:
     * se audita ese id como EXPLOTACION con la misma operacion {@code EDITAR_TRAMITE}, si es de otra gestoria.
     */
    public void patchTramite(Long usuarioId, Long gestoriaId, Long tramiteId, Long explotacionIdCuerpo) {
        if (tramite(usuarioId, gestoriaId, tramiteId, OperacionAuditada.EDITAR_TRAMITE)) {
            return;
        }
        if (explotacionIdCuerpo != null && tramiteRepository.findByIdAndGestoriaId(tramiteId, gestoriaId).isPresent()) {
            explotacion(usuarioId, gestoriaId, explotacionIdCuerpo, OperacionAuditada.EDITAR_TRAMITE);
        }
    }

    /**
     * POST /contactos/{id}/explotaciones: primero el Contacto de la ruta ({@code VINCULAR_EXPLOTACION}); si es ajeno,
     * se audita y nada mas. Si el Contacto es PROPIO pero hubo 404, el 404 lo causo el {@code explotacionId} del
     * cuerpo: se audita ese id como EXPLOTACION con la misma operacion {@code VINCULAR_EXPLOTACION}, si es de otra
     * gestoria.
     */
    public void vinculo(Long usuarioId, Long gestoriaId, Long contactoId, Long explotacionIdCuerpo) {
        if (contacto(usuarioId, gestoriaId, contactoId, OperacionAuditada.VINCULAR_EXPLOTACION)) {
            return;
        }
        if (explotacionIdCuerpo != null && contactoRepository.findByIdAndGestoriaId(contactoId, gestoriaId).isPresent()) {
            explotacion(usuarioId, gestoriaId, explotacionIdCuerpo, OperacionAuditada.VINCULAR_EXPLOTACION);
        }
    }

    private boolean auditarSiAjeno(boolean ajeno, Long usuarioId, Long gestoriaId, EntidadAuditada tipo, Long id,
                                   OperacionAuditada operacion) {
        if (ajeno) {
            auditoriaService.registrarAparte(AccionAuditoria.ACCESO_OTRA_GESTORIA, gestoriaId,
                    Actor.usuario(usuarioId), tipo, id, null, DatosAuditoria.con(Clave.OPERACION, operacion));
        }
        return ajeno;
    }
}
