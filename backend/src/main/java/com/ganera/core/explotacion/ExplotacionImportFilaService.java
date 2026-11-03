package com.ganera.core.explotacion;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.ContactoService;
import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Procesa una fila del importador Excel (Explotacion, Animal o Contacto) en su PROPIA transaccion
 * (REQUIRES_NEW), en un bean separado de ExplotacionImportService a proposito: el proxy de
 * @Transactional de Spring solo intercepta llamadas que entran desde fuera del bean -- una
 * auto-invocacion (this.metodo()) lo ignora en silencio. Sin este aislamiento, una violacion real
 * de constraint en saveAndFlush() (p.ej. un codigo_rega que ya existe en OTRA Gestoria, invisible
 * para el filtro de tenant) deja la sesion de Hibernate en un estado invalido -- "don't flush the
 * Session after an exception occurs" -- que rompe el procesado de las filas SIGUIENTES del mismo
 * fichero aunque esas filas no tengan ningun problema propio. Con REQUIRES_NEW, esa fila hace
 * rollback de su propia transaccion (con su propia conexion) sin tocar la del resto del import.
 *
 * <p><b>Que EntityManager usa cada fila (decision 19, corregido en Task 7a).</b> Sobre HTTP no hay
 * transaccion exterior (ExplotacionImportService no es @Transactional): REQUIRES_NEW no tiene nada
 * que suspender y abre la transaccion sobre el EntityManager de open-in-view, el mismo en el que
 * {@code TenantFilterActivationInterceptor} ya activo {@code gestoriaFilter}. Solo cuando SI hay
 * una transaccion exterior (un llamador @Transactional, @DataJpaTest, un scheduler) REQUIRES_NEW
 * la suspende y ata un EntityManager NUEVO, sin ningun filtro activo. Por eso cada metodo reactiva
 * el filtro con el {@code gestoriaId} recibido -- y, ademas, todas las consultas llevan ese
 * gestoriaId como parametro explicito (decision 17), sin depender del filtro en ningun caso.
 */
@Service
class ExplotacionImportFilaService {

    private final ExplotacionRepository explotacionRepository;
    private final AnimalRepository animalRepository;
    private final GanaderoRepository ganaderoRepository;
    private final GestoriaRepository gestoriaRepository;
    private final ContactoRepository contactoRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;
    private final AuditoriaService auditoriaService;

    static final String MOTIVO_CONTACTO_INACTIVO = "El contacto con ese teléfono está dado de baja";

    @PersistenceContext
    private EntityManager entityManager;

    ExplotacionImportFilaService(
            ExplotacionRepository explotacionRepository,
            AnimalRepository animalRepository,
            GanaderoRepository ganaderoRepository,
            GestoriaRepository gestoriaRepository,
            ContactoRepository contactoRepository,
            ContactoExplotacionRepository contactoExplotacionRepository,
            AuditoriaService auditoriaService) {
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Fila de auditoria de una entidad creada o cambiada por el importador (D9), dentro de la transaccion
     * REQUIRES_NEW de la fila: si la fila del Excel hace rollback, su auditoria se va con ella. {@code usuarioId}
     * null (la sobrecarga de importar sin usuario, solo en tests) -> sin fila. {@code despues} lleva solo el
     * codigo ORIGEN=IMPORTACION, para distinguir la misma accion hecha desde un endpoint.
     */
    private void auditar(Long usuarioId, AccionAuditoria accion, Long gestoriaId, EntidadAuditada tipo,
                         Long entidadId, DatosAuditoria despues) {
        if (usuarioId == null) {
            return;
        }
        auditoriaService.registrar(accion, gestoriaId, Actor.usuario(usuarioId), tipo, entidadId, null, despues);
    }

    private static DatosAuditoria origenImportacion() {
        return DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.IMPORTACION);
    }

    /** Fila resumen IMPORTACION_EXCEL (D9), en su propia transaccion REQUIRES_NEW; los recuentos los arma
     * ExplotacionImportService, que tiene las tres hojas y los errores. Sin fila si no hay usuario. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void registrarResumenImportacion(Long gestoriaId, Long usuarioId, DatosAuditoria recuentos) {
        if (usuarioId == null) {
            return;
        }
        auditoriaService.registrar(AccionAuditoria.IMPORTACION_EXCEL, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.IMPORTACION, null, null, recuentos);
    }

    private void activarFiltroDeTenant(Long gestoriaId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaId);
    }

    /**
     * Decision 17: Ganadero y Explotacion se buscan con el gestoriaId explicito (nunca
     * findByNif/findByCodigoRega a secas, que dependerian solo del filtro reactivado). Un NIF o
     * codigo_rega que ya es de OTRA Gestoria no se ve aqui: se intenta crear, choca con el UNIQUE
     * global en saveAndFlush y ExplotacionImportService lo convierte en un error de fila NEUTRO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarExplotacion(Long gestoriaId, Long usuarioId, String codigoRega, String nombre,
                                            String nif, String nombreGanadero) {
        activarFiltroDeTenant(gestoriaId);
        Gestoria gestoria = gestoriaRepository.getReferenceById(gestoriaId);
        Optional<Ganadero> ganaderoExistente = ganaderoRepository.findByNifAndGestoriaId(nif, gestoriaId);
        Ganadero ganadero = ganaderoExistente.orElseGet(() -> crearGanadero(gestoria, nif, nombreGanadero));
        if (ganaderoExistente.isEmpty()) {
            auditar(usuarioId, AccionAuditoria.GANADERO_CREADO, gestoriaId, EntidadAuditada.GANADERO,
                    ganadero.getId(), origenImportacion());
        }

        Optional<Explotacion> existente = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRega, gestoriaId);
        if (existente.isPresent()) {
            Explotacion explotacion = existente.get();
            Long ganaderoAntes = explotacion.getGanadero() != null ? explotacion.getGanadero().getId() : null;
            boolean cambia = !Objects.equals(explotacion.getNombre(), nombre)
                    || !Objects.equals(ganaderoAntes, ganadero.getId());
            explotacion.setNombre(nombre);
            explotacion.setGanadero(ganadero);
            explotacionRepository.saveAndFlush(explotacion);
            // D9: una reimportacion que no cambia nada no deja fila por entidad (solo el resumen).
            if (cambia) {
                auditar(usuarioId, AccionAuditoria.EXPLOTACION_MODIFICADA, gestoriaId, EntidadAuditada.EXPLOTACION,
                        explotacion.getId(), origenImportacion());
            }
            return ImportFilaResultado.ACTUALIZADA;
        }

        Explotacion nueva = new Explotacion();
        nueva.setGestoria(gestoria);
        nueva.setCodigoRega(codigoRega);
        nueva.setNombre(nombre);
        nueva.setGanadero(ganadero);
        explotacionRepository.saveAndFlush(nueva);
        auditar(usuarioId, AccionAuditoria.EXPLOTACION_CREADA, gestoriaId, EntidadAuditada.EXPLOTACION,
                nueva.getId(), origenImportacion());
        return ImportFilaResultado.CREADA;
    }

    /**
     * {@code crotal} llega YA normalizado con CrotalNormalizador (decision 22, lo hace
     * ExplotacionImportService antes de llamar aqui), asi que crotal_ultimos_digitos se deriva del
     * crotal normalizado. Explotacion y Animal se buscan con el gestoriaId explicito (decision 17):
     * un crotal de OTRA Gestoria no se ve, choca con el UNIQUE global y acaba en error neutro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarAnimal(Long gestoriaId, Long usuarioId, String crotal, String codigoRegaExplotacion) {
        activarFiltroDeTenant(gestoriaId);
        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRegaExplotacion, gestoriaId)
                .orElseThrow(() -> new IllegalStateException("La explotacion '" + codigoRegaExplotacion + "' no existe"));

        String ultimosDigitos = ultimosDigitos(crotal);
        Optional<Animal> existente = animalRepository.findByCrotalAndGestoriaId(crotal, gestoriaId);
        if (existente.isPresent()) {
            Animal animal = existente.get();
            Long explotacionAntes = animal.getExplotacion() != null ? animal.getExplotacion().getId() : null;
            boolean cambia = !Objects.equals(explotacionAntes, explotacion.getId())
                    || !Objects.equals(animal.getCrotalUltimosDigitos(), ultimosDigitos);
            animal.setExplotacion(explotacion);
            animal.setCrotalUltimosDigitos(ultimosDigitos);
            animalRepository.saveAndFlush(animal);
            // D9: sin cambios reales, sin fila por entidad (solo el resumen de la reimportacion).
            if (cambia) {
                auditar(usuarioId, AccionAuditoria.ANIMAL_MODIFICADO, gestoriaId, EntidadAuditada.ANIMAL,
                        animal.getId(), origenImportacion());
            }
            return ImportFilaResultado.ACTUALIZADA;
        }

        Animal nuevo = new Animal();
        nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        nuevo.setExplotacion(explotacion);
        nuevo.setCrotal(crotal);
        nuevo.setCrotalUltimosDigitos(ultimosDigitos);
        animalRepository.saveAndFlush(nuevo);
        auditar(usuarioId, AccionAuditoria.ANIMAL_CREADO, gestoriaId, EntidadAuditada.ANIMAL,
                nuevo.getId(), origenImportacion());
        return ImportFilaResultado.CREADA;
    }

    /**
     * Fila de la hoja "Contactos". Todas las consultas llevan el gestoriaId explicito, sin
     * depender del filtro reactivado: la Explotacion por findByCodigoRegaAndGestoriaId y el
     * Contacto por findByGestoriaIdAndTelefono -- NUNCA findByTelefono, que es exclusivo del
     * webhook de 3b (decision 11). Un telefono que ya es de OTRA Gestoria no se ve aqui, asi que
     * se intenta crear y choca con el UNIQUE global en saveAndFlush: la
     * DataIntegrityViolationException sale de este metodo, esta transaccion REQUIRES_NEW hace
     * rollback de la fila entera y ExplotacionImportService la convierte en un error de fila
     * generico que no menciona la otra Gestoria.
     *
     * <p>Contacto existente y activo: se sobrescribe el nombre (como hacen las filas de
     * Explotacion/Ganadero) y se crea el enlace o se actualiza su rol -> ACTUALIZADA. Existente e
     * inactivo: error de fila, ni se enlaza ni se reactiva. No existe: se crea -> CREADA.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarContacto(Long gestoriaId, Long usuarioId, String telefonoNormalizado, String nombre,
                                         String codigoRega, RolContacto rol) {
        activarFiltroDeTenant(gestoriaId);
        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRega, gestoriaId)
                .orElseThrow(() -> new FilaImportacionException("La explotacion '" + codigoRega + "' no existe"));

        Optional<Contacto> existente = contactoRepository.findByGestoriaIdAndTelefono(gestoriaId, telefonoNormalizado);
        ImportFilaResultado resultado;
        Contacto contacto;
        boolean contactoCreado;
        if (existente.isPresent()) {
            contacto = existente.get();
            if (!contacto.isActivo()) {
                throw new FilaImportacionException(MOTIVO_CONTACTO_INACTIVO);
            }
            resultado = ImportFilaResultado.ACTUALIZADA;
            contactoCreado = false;
        } else {
            contacto = new Contacto();
            contacto.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
            contacto.setTelefono(telefonoNormalizado);
            contacto.setActivo(true);
            resultado = ImportFilaResultado.CREADA;
            contactoCreado = true;
        }

        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);
        if (contactoCreado) {
            auditar(usuarioId, AccionAuditoria.CONTACTO_CREADO, gestoriaId, EntidadAuditada.CONTACTO,
                    contacto.getId(), DatosAuditoria.con(Clave.ACTIVO, true).y(Clave.ORIGEN, OrigenAuditoria.IMPORTACION));
        }

        // Decision 15, antes de crear/actualizar el enlace: Contacto, Explotacion y usuario de la
        // misma Gestoria. Si falla, la excepcion revierte la fila entera (tambien el nombre).
        try {
            ContactoService.comprobarMismaGestoria(contacto, explotacion, gestoriaId);
        } catch (RecursoNoEncontradoException e) {
            throw new FilaImportacionException("La explotacion '" + codigoRega + "' no existe");
        }

        Optional<ContactoExplotacion> enlaceExistente = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contacto.getId(), explotacion.getId(), gestoriaId);
        if (enlaceExistente.isEmpty()) {
            ContactoExplotacion nuevo = new ContactoExplotacion();
            // La Gestoria del enlace sale del gestoriaId del JWT, nunca de los extremos.
            nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
            nuevo.setContacto(contacto);
            nuevo.setExplotacion(explotacion);
            nuevo.setRol(rol);
            contactoExplotacionRepository.saveAndFlush(nuevo);
            auditar(usuarioId, AccionAuditoria.CONTACTO_VINCULADO, gestoriaId, EntidadAuditada.CONTACTO,
                    contacto.getId(), DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacion.getId()).y(Clave.ROL, rol)
                            .y(Clave.ORIGEN, OrigenAuditoria.IMPORTACION));
        } else {
            ContactoExplotacion enlace = enlaceExistente.get();
            RolContacto rolAntes = enlace.getRol();
            enlace.setRol(rol);
            contactoExplotacionRepository.saveAndFlush(enlace);
            if (rolAntes != rol) {
                auditar(usuarioId, AccionAuditoria.CONTACTO_ROL_CAMBIADO, gestoriaId, EntidadAuditada.CONTACTO,
                        contacto.getId(), DatosAuditoria.con(Clave.EXPLOTACION_ID, explotacion.getId()).y(Clave.ROL, rol)
                                .y(Clave.ORIGEN, OrigenAuditoria.IMPORTACION));
            }
        }
        return resultado;
    }

    private Ganadero crearGanadero(Gestoria gestoria, String nif, String nombre) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNif(nif);
        ganadero.setNombre(nombre);
        return ganaderoRepository.saveAndFlush(ganadero);
    }

    /** Ultimos 6 digitos del crotal -- lo que un Contacto escribe por WhatsApp (ver Prompt 3b, aun sin implementar). */
    private static String ultimosDigitos(String crotal) {
        int longitud = Math.min(6, crotal.length());
        return crotal.substring(crotal.length() - longitud);
    }
}
