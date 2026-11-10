package com.ganera.core.ganadero;

import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta MANUAL de Ganadero desde la UI (Antonio la abre; antes el único camino era el Excel). El
 * gestoriaId viene SIEMPRE del JWT, nunca del cuerpo. Devuelve un DTO explícito (nunca la entidad,
 * que lleva las credenciales OVZ). La validación de formato del NIF se hace en el controlador; aquí
 * solo se decide la unicidad y se guarda.
 */
@Service
public class GanaderoAltaService {

    /** Ya hay un ganadero con ese NIF en ESTA gestoría: se puede decir con claridad. */
    static final String MOTIVO_NIF_DUPLICADO_PROPIO = "Ya tienes un ganadero con ese NIF.";
    /** El NIF choca con el UNIQUE global (normalmente de otra gestoría): no se revela de quién. Mismo
     * criterio que el importador Excel («identificador no disponible»), para no permitir enumerar
     * NIF de otras gestorías. */
    static final String MOTIVO_NIF_NO_DISPONIBLE = "Ese NIF no está disponible.";

    private final GanaderoRepository ganaderoRepository;
    private final GestoriaRepository gestoriaRepository;
    private final AuditoriaService auditoriaService;

    public GanaderoAltaService(GanaderoRepository ganaderoRepository, GestoriaRepository gestoriaRepository,
                               AuditoriaService auditoriaService) {
        this.ganaderoRepository = ganaderoRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea el Ganadero y devuelve su fila de listado (sin explotaciones todavía: numeroExplotaciones
     * = 0). `nombre` y `nif` ya vienen validados por el controlador; aquí el nif se normaliza para
     * guardar y comparar.
     *
     * <p>Auditoría: una fila {@code GANADERO_CREADO} (MANDATORY, dentro de esta misma transacción)
     * con el actor USUARIO del JWT, la gestoría del JWT y entidadId = id creado, marcada con
     * {@code ORIGEN: MANUAL} para distinguirla del importador de Excel. Si el NIF está duplicado se
     * lanza antes o durante el {@code saveAndFlush}: la transacción se deshace y NO queda fila de
     * auditoría.
     *
     * @throws GanaderoDuplicadoException si el NIF ya existe (en esta gestoría o, por el UNIQUE
     *         global, en otra).
     */
    @Transactional
    public GanaderoResumenResponse crear(Long gestoriaId, Long usuarioId, String nombre, String nif,
                                         String telefonoNormalizado) {
        String nifNormalizado = NifValidador.normalizar(nif);
        if (ganaderoRepository.findByNifAndGestoriaId(nifNormalizado, gestoriaId).isPresent()) {
            throw new GanaderoDuplicadoException(MOTIVO_NIF_DUPLICADO_PROPIO);
        }
        Ganadero ganadero = new Ganadero();
        // getReferenceById: proxy perezoso, no hace SELECT. El JWT garantiza que la gestoría existe.
        ganadero.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        ganadero.setNif(nifNormalizado);
        ganadero.setNombre(nombre.strip());
        // telefonoNormalizado ya viene validado por el controlador (null = sin telefono). No sale en la respuesta
        // (GanaderoResumenResponse) ni en la auditoria: la fila GANADERO_CREADO no deja constancia de si se informo (D5).
        ganadero.setTelefono(telefonoNormalizado);
        Ganadero guardado;
        try {
            guardado = ganaderoRepository.saveAndFlush(ganadero);
        } catch (DataIntegrityViolationException e) {
            // Una comprobación previa pasó pero el UNIQUE global saltó al guardar (NIF de otra
            // gestoría, o un alta simultánea). No se revela nada de la otra gestoría.
            throw new GanaderoDuplicadoException(MOTIVO_NIF_NO_DISPONIBLE);
        }
        auditoriaService.registrar(AccionAuditoria.GANADERO_CREADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.GANADERO, guardado.getId(), null,
                DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.MANUAL));
        return new GanaderoResumenResponse(guardado.getId(), guardado.getNombre(), guardado.getNif(), 0L);
    }
}
