package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gestion de Contactos desde endpoints autenticados. Todas las consultas llevan el gestoriaId
 * del JWT como parametro explicito (findByIdAndGestoriaId, findByGestoriaId...), nunca el filtro
 * ambiente ni findById(id) a secas. findByTelefono (sin scope) NO se usa aqui: es exclusivo del
 * webhook de 3b.
 *
 * El telefono llega ya normalizado (TelefonoNormalizador, en el controlador). Un telefono
 * duplicado -- en esta u otra Gestoria, UNIQUE global por decision A -- se detecta solo por la
 * violacion del constraint en saveAndFlush, sin consulta previa: la DataIntegrityViolationException
 * NO se captura aqui, propaga fuera del @Transactional (rollback limpio) y la captura
 * ContactoController, igual que RegistroGestoriaService/RegistroGestoriaController.
 *
 * <p>Auditoria (plan 2026-10-21): cada escritura aceptada deja su fila ({@code registrar}, dentro de la
 * transaccion del servicio). Un cambio que no cambia nada (DELETE de uno ya inactivo, reactivar uno ya
 * activo, enlazar con el mismo rol) NO deja fila. El actor es el usuario del JWT (lo pasa el controlador).
 */
@Service
public class ContactoService {

    /** Unico mensaje para un telefono duplicado, sea de esta u otra Gestoria: no dice cual. Lo
     * usan ContactoController (409) y el importador Excel (error de fila de la hoja Contactos). */
    public static final String MOTIVO_TELEFONO_EN_USO = "No se puede usar ese teléfono para un contacto.";

    private final ContactoRepository contactoRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;
    private final ExplotacionRepository explotacionRepository;
    private final GestoriaRepository gestoriaRepository;
    private final AuditoriaService auditoriaService;

    public ContactoService(
            ContactoRepository contactoRepository,
            ContactoExplotacionRepository contactoExplotacionRepository,
            ExplotacionRepository explotacionRepository,
            GestoriaRepository gestoriaRepository,
            AuditoriaService auditoriaService) {
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.explotacionRepository = explotacionRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Los enlaces de toda la pagina se cargan en UNA consulta (con la Explotacion en fetch join). */
    @Transactional(readOnly = true)
    public Page<ContactoResponse> listar(Long gestoriaId, boolean incluirInactivos, Pageable pageable) {
        Page<Contacto> pagina = incluirInactivos
                ? contactoRepository.findByGestoriaId(gestoriaId, pageable)
                : contactoRepository.findByGestoriaIdAndActivoTrue(gestoriaId, pageable);
        List<Long> ids = pagina.getContent().stream().map(Contacto::getId).toList();
        Map<Long, List<ContactoExplotacionResponse>> enlaces = cargarEnlaces(ids, gestoriaId);
        return pagina.map(c -> ContactoResponse.from(c, enlaces.getOrDefault(c.getId(), List.of())));
    }

    @Transactional
    public ContactoResponse crear(Long gestoriaId, Long usuarioId, String telefonoNormalizado, String nombre) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        contacto.setTelefono(telefonoNormalizado);
        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);
        auditoriaService.registrar(AccionAuditoria.CONTACTO_CREADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.CONTACTO, contacto.getId(), null, DatosAuditoria.con(Clave.ACTIVO, true));
        return ContactoResponse.from(contacto, List.of());
    }

    @Transactional
    public ContactoResponse actualizar(Long gestoriaId, Long usuarioId, Long contactoId,
                                       String telefonoNormalizado, String nombre) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        List<CampoContacto> cambiados = new ArrayList<>();
        if (!Objects.equals(contacto.getTelefono(), telefonoNormalizado)) {
            cambiados.add(CampoContacto.TELEFONO);
        }
        if (!Objects.equals(contacto.getNombre(), nombre)) {
            cambiados.add(CampoContacto.NOMBRE);
        }
        contacto.setTelefono(telefonoNormalizado);
        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);
        // Un PUT siempre deja fila CONTACTO_MODIFICADO; CAMPOS_CAMBIADOS dice cuales cambiaron (puede ir
        // vacia si el PUT reenvia los mismos datos). Nunca el valor nuevo: solo el codigo del campo.
        auditoriaService.registrar(AccionAuditoria.CONTACTO_MODIFICADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.CONTACTO, contacto.getId(), null,
                DatosAuditoria.conCodigos(Clave.CAMPOS_CAMBIADOS, cambiados));
        return responder(contacto, gestoriaId);
    }

    /** Borrado logico, idempotente: los enlaces y el historial se conservan. Un DELETE de uno ya
     * inactivo no cambia nada -> sin fila de auditoria. */
    @Transactional
    public void desactivar(Long gestoriaId, Long usuarioId, Long contactoId) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        if (!contacto.isActivo()) {
            return;
        }
        contacto.setActivo(false);
        contactoRepository.save(contacto);
        auditoriaService.registrar(AccionAuditoria.CONTACTO_DESACTIVADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.CONTACTO, contacto.getId(),
                DatosAuditoria.con(Clave.ACTIVO, true), DatosAuditoria.con(Clave.ACTIVO, false));
    }

    /** Idempotente: reactivar uno ya activo no cambia nada -> sin fila. */
    @Transactional
    public ContactoResponse reactivar(Long gestoriaId, Long usuarioId, Long contactoId) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        if (!contacto.isActivo()) {
            contacto.setActivo(true);
            contactoRepository.save(contacto);
            auditoriaService.registrar(AccionAuditoria.CONTACTO_REACTIVADO, gestoriaId, Actor.usuario(usuarioId),
                    EntidadAuditada.CONTACTO, contacto.getId(),
                    DatosAuditoria.con(Clave.ACTIVO, false), DatosAuditoria.con(Clave.ACTIVO, true));
        }
        return responder(contacto, gestoriaId);
    }

    /**
     * Crea el enlace Contacto-Explotacion, o actualiza el rol si ya existe (idempotente). Ambos
     * extremos se cargan con finders con gestoriaId explicito y, ademas, comprobarMismaGestoria
     * lo verifica de forma explicita (decision 15) antes de escribir nada.
     *
     * <p>Fila de auditoria: CONTACTO_VINCULADO si el enlace es nuevo; CONTACTO_ROL_CAMBIADO si existia y
     * cambia el rol; si existia con el mismo rol, no cambia nada -> sin fila.
     */
    @Transactional
    public ContactoResponse enlazar(Long gestoriaId, Long usuarioId, Long contactoId, Long explotacionId,
                                    RolContacto rol) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        Explotacion explotacion = explotacionRepository.findByIdAndGestoriaId(explotacionId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        comprobarMismaGestoria(contacto, explotacion, gestoriaId);
        if (!contacto.isActivo()) {
            throw new ContactoInactivoException();
        }

        ContactoExplotacion existente = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contactoId, explotacionId, gestoriaId)
                .orElse(null);
        if (existente == null) {
            ContactoExplotacion nuevo = new ContactoExplotacion();
            // La Gestoria del enlace sale del JWT, nunca de los extremos (decision 15).
            nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
            nuevo.setContacto(contacto);
            nuevo.setExplotacion(explotacion);
            nuevo.setRol(rol);
            contactoExplotacionRepository.save(nuevo);
            auditoriaService.registrar(AccionAuditoria.CONTACTO_VINCULADO, gestoriaId, Actor.usuario(usuarioId),
                    EntidadAuditada.CONTACTO, contactoId, null,
                    DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacionId).y(Clave.ROL, rol));
        } else {
            RolContacto rolAntes = existente.getRol();
            existente.setRol(rol);
            contactoExplotacionRepository.save(existente);
            if (rolAntes != rol) {
                auditoriaService.registrar(AccionAuditoria.CONTACTO_ROL_CAMBIADO, gestoriaId, Actor.usuario(usuarioId),
                        EntidadAuditada.CONTACTO, contactoId,
                        DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacionId).y(Clave.ROL, rolAntes),
                        DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacionId).y(Clave.ROL, rol));
            }
        }
        return responder(contacto, gestoriaId);
    }

    @Transactional
    public void desenlazar(Long gestoriaId, Long usuarioId, Long contactoId, Long explotacionId) {
        buscarContacto(gestoriaId, contactoId);
        ContactoExplotacion enlace = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contactoId, explotacionId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        RolContacto rol = enlace.getRol();
        contactoExplotacionRepository.delete(enlace);
        auditoriaService.registrar(AccionAuditoria.CONTACTO_DESVINCULADO, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.CONTACTO, contactoId,
                DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacionId).y(Clave.ROL, rol), null);
    }

    /**
     * Decision 15: comprobacion defensiva, redundante con los finders a proposito. Los finders de
     * ContactoExplotacion solo miran el gestoria_id del propio enlace y nada en BD impide un
     * enlace que cruce Gestorias, asi que si algun dia un finder dejara de filtrar por Gestoria,
     * esto es lo que para el enlace. Se traduce a 404 como el resto (no revela existencia).
     * Publico porque tambien lo usa el importador Excel (ExplotacionImportFilaService.procesarContacto),
     * que lo convierte en un error de fila.
     */
    public static void comprobarMismaGestoria(Contacto contacto, Explotacion explotacion, Long gestoriaId) {
        Long gestoriaContacto = contacto.getGestoria() != null ? contacto.getGestoria().getId() : null;
        Long gestoriaExplotacion = explotacion.getGestoria() != null ? explotacion.getGestoria().getId() : null;
        if (gestoriaId == null
                || !Objects.equals(gestoriaContacto, gestoriaId)
                || !Objects.equals(gestoriaExplotacion, gestoriaId)) {
            throw new RecursoNoEncontradoException();
        }
    }

    private Contacto buscarContacto(Long gestoriaId, Long contactoId) {
        return contactoRepository.findByIdAndGestoriaId(contactoId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    private ContactoResponse responder(Contacto contacto, Long gestoriaId) {
        List<ContactoExplotacionResponse> enlaces =
                cargarEnlaces(List.of(contacto.getId()), gestoriaId).getOrDefault(contacto.getId(), List.of());
        return ContactoResponse.from(contacto, enlaces);
    }

    private Map<Long, List<ContactoExplotacionResponse>> cargarEnlaces(Collection<Long> contactoIds, Long gestoriaId) {
        if (contactoIds.isEmpty()) {
            return Map.of();
        }
        return contactoExplotacionRepository.findByContactoIdInAndGestoriaId(contactoIds, gestoriaId).stream()
                .sorted(Comparator.comparing(e -> e.getExplotacion().getCodigoRega()))
                .collect(Collectors.groupingBy(
                        e -> e.getContacto().getId(),
                        Collectors.mapping(ContactoExplotacionResponse::from, Collectors.toList())));
    }
}
