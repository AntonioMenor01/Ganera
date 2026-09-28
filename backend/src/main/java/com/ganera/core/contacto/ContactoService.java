package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ContactoService(
            ContactoRepository contactoRepository,
            ContactoExplotacionRepository contactoExplotacionRepository,
            ExplotacionRepository explotacionRepository,
            GestoriaRepository gestoriaRepository) {
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.explotacionRepository = explotacionRepository;
        this.gestoriaRepository = gestoriaRepository;
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
    public ContactoResponse crear(Long gestoriaId, String telefonoNormalizado, String nombre) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        contacto.setTelefono(telefonoNormalizado);
        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);
        return ContactoResponse.from(contacto, List.of());
    }

    @Transactional
    public ContactoResponse actualizar(Long gestoriaId, Long contactoId, String telefonoNormalizado, String nombre) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        contacto.setTelefono(telefonoNormalizado);
        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);
        return responder(contacto, gestoriaId);
    }

    /** Borrado logico, idempotente: los enlaces y el historial se conservan. */
    @Transactional
    public void desactivar(Long gestoriaId, Long contactoId) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        contacto.setActivo(false);
        contactoRepository.save(contacto);
    }

    @Transactional
    public ContactoResponse reactivar(Long gestoriaId, Long contactoId) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        contacto.setActivo(true);
        contactoRepository.save(contacto);
        return responder(contacto, gestoriaId);
    }

    /**
     * Crea el enlace Contacto-Explotacion, o actualiza el rol si ya existe (idempotente). Ambos
     * extremos se cargan con finders con gestoriaId explicito y, ademas, comprobarMismaGestoria
     * lo verifica de forma explicita (decision 15) antes de escribir nada.
     */
    @Transactional
    public ContactoResponse enlazar(Long gestoriaId, Long contactoId, Long explotacionId, RolContacto rol) {
        Contacto contacto = buscarContacto(gestoriaId, contactoId);
        Explotacion explotacion = explotacionRepository.findByIdAndGestoriaId(explotacionId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        comprobarMismaGestoria(contacto, explotacion, gestoriaId);
        if (!contacto.isActivo()) {
            throw new ContactoInactivoException();
        }

        ContactoExplotacion enlace = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contactoId, explotacionId, gestoriaId)
                .orElseGet(() -> {
                    ContactoExplotacion nuevo = new ContactoExplotacion();
                    // La Gestoria del enlace sale del JWT, nunca de los extremos (decision 15).
                    nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
                    nuevo.setContacto(contacto);
                    nuevo.setExplotacion(explotacion);
                    return nuevo;
                });
        enlace.setRol(rol);
        contactoExplotacionRepository.save(enlace);
        return responder(contacto, gestoriaId);
    }

    @Transactional
    public void desenlazar(Long gestoriaId, Long contactoId, Long explotacionId) {
        buscarContacto(gestoriaId, contactoId);
        ContactoExplotacion enlace = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contactoId, explotacionId, gestoriaId)
                .orElseThrow(RecursoNoEncontradoException::new);
        contactoExplotacionRepository.delete(enlace);
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
