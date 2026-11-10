package com.ganera.core.ganadero;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edicion del telefono de la ficha del Ganadero (PUT /ganaderos/{id}/telefono, plan 2026-11-10). gestoriaId y
 * usuarioId SIEMPRE del JWT. Carga con {@code findByIdAndGestoriaId} (nunca findById a secas): vacio si el
 * Ganadero no existe o es de otra Gestoria, y el controlador responde 404 (y audita el acceso ajeno fuera de esta
 * transaccion, como hace el detalle).
 *
 * <p>Gana la ultima escritura (sin version). El valor del telefono NUNCA va a la auditoria: la fila
 * {@code GANADERO_MODIFICADO} solo lleva el ORIGEN (MANUAL) y el CODIGO del campo cambiado (TELEFONO), igual que
 * {@code CONTACTO_MODIFICADO}. Siempre deja fila; CAMPOS_CAMBIADOS va vacia si el valor no cambia.
 */
@Service
public class GanaderoEdicionService {

    private final GanaderoRepository ganaderoRepository;
    private final GanaderoConsultaService ganaderoConsultaService;
    private final AuditoriaService auditoriaService;

    public GanaderoEdicionService(GanaderoRepository ganaderoRepository,
                                  GanaderoConsultaService ganaderoConsultaService,
                                  AuditoriaService auditoriaService) {
        this.ganaderoRepository = ganaderoRepository;
        this.ganaderoConsultaService = ganaderoConsultaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Cambia (o borra, con {@code telefonoNormalizado == null}) el telefono del Ganadero. El valor ya viene
     * validado y normalizado por el controlador. Devuelve el detalle actualizado, o vacio si el Ganadero no es de
     * esta Gestoria (el controlador responde 404 sin cuerpo y audita el acceso ajeno).
     */
    @Transactional
    public Optional<GanaderoDetalleResponse> cambiarTelefono(Long gestoriaId, Long usuarioId, Long ganaderoId,
                                                             String telefonoNormalizado) {
        Optional<Ganadero> encontrado = ganaderoRepository.findByIdAndGestoriaId(ganaderoId, gestoriaId);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        Ganadero ganadero = encontrado.get();
        List<CampoGanadero> cambiados = new ArrayList<>();
        if (!Objects.equals(ganadero.getTelefono(), telefonoNormalizado)) {
            cambiados.add(CampoGanadero.TELEFONO);
        }
        ganadero.setTelefono(telefonoNormalizado);
        ganaderoRepository.saveAndFlush(ganadero);
        // Siempre deja fila GANADERO_MODIFICADO; CAMPOS_CAMBIADOS dice cuales cambiaron (vacia si se reenvia el
        // mismo valor). Nunca el valor nuevo: solo ORIGEN y el codigo del campo.
        auditoriaService.registrar(AccionAuditoria.GANADERO_MODIFICADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.GANADERO, ganaderoId, null,
                DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.MANUAL).yCodigos(Clave.CAMPOS_CAMBIADOS, cambiados));
        // Detalle reconstruido reutilizando la consulta (sin LECTURA_GANADERO, A1): ve el telefono recien guardado.
        return ganaderoConsultaService.detalle(gestoriaId, ganaderoId);
    }
}
