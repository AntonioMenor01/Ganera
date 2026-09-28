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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    static final String MOTIVO_CONTACTO_INACTIVO = "El contacto con ese teléfono está dado de baja";

    @PersistenceContext
    private EntityManager entityManager;

    ExplotacionImportFilaService(
            ExplotacionRepository explotacionRepository,
            AnimalRepository animalRepository,
            GanaderoRepository ganaderoRepository,
            GestoriaRepository gestoriaRepository,
            ContactoRepository contactoRepository,
            ContactoExplotacionRepository contactoExplotacionRepository) {
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
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
    ImportFilaResultado procesarExplotacion(Long gestoriaId, String codigoRega, String nombre, String nif, String nombreGanadero) {
        activarFiltroDeTenant(gestoriaId);
        Gestoria gestoria = gestoriaRepository.getReferenceById(gestoriaId);
        Ganadero ganadero = ganaderoRepository.findByNifAndGestoriaId(nif, gestoriaId)
                .orElseGet(() -> crearGanadero(gestoria, nif, nombreGanadero));

        Optional<Explotacion> existente = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRega, gestoriaId);
        if (existente.isPresent()) {
            Explotacion explotacion = existente.get();
            explotacion.setNombre(nombre);
            explotacion.setGanadero(ganadero);
            explotacionRepository.saveAndFlush(explotacion);
            return ImportFilaResultado.ACTUALIZADA;
        }

        Explotacion nueva = new Explotacion();
        nueva.setGestoria(gestoria);
        nueva.setCodigoRega(codigoRega);
        nueva.setNombre(nombre);
        nueva.setGanadero(ganadero);
        explotacionRepository.saveAndFlush(nueva);
        return ImportFilaResultado.CREADA;
    }

    /**
     * {@code crotal} llega YA normalizado con CrotalNormalizador (decision 22, lo hace
     * ExplotacionImportService antes de llamar aqui), asi que crotal_ultimos_digitos se deriva del
     * crotal normalizado. Explotacion y Animal se buscan con el gestoriaId explicito (decision 17):
     * un crotal de OTRA Gestoria no se ve, choca con el UNIQUE global y acaba en error neutro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarAnimal(Long gestoriaId, String crotal, String codigoRegaExplotacion) {
        activarFiltroDeTenant(gestoriaId);
        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRegaExplotacion, gestoriaId)
                .orElseThrow(() -> new IllegalStateException("La explotacion '" + codigoRegaExplotacion + "' no existe"));

        String ultimosDigitos = ultimosDigitos(crotal);
        Optional<Animal> existente = animalRepository.findByCrotalAndGestoriaId(crotal, gestoriaId);
        if (existente.isPresent()) {
            Animal animal = existente.get();
            animal.setExplotacion(explotacion);
            animal.setCrotalUltimosDigitos(ultimosDigitos);
            animalRepository.saveAndFlush(animal);
            return ImportFilaResultado.ACTUALIZADA;
        }

        Animal nuevo = new Animal();
        nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
        nuevo.setExplotacion(explotacion);
        nuevo.setCrotal(crotal);
        nuevo.setCrotalUltimosDigitos(ultimosDigitos);
        animalRepository.saveAndFlush(nuevo);
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
    ImportFilaResultado procesarContacto(Long gestoriaId, String telefonoNormalizado, String nombre,
                                         String codigoRega, RolContacto rol) {
        activarFiltroDeTenant(gestoriaId);
        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId(codigoRega, gestoriaId)
                .orElseThrow(() -> new FilaImportacionException("La explotacion '" + codigoRega + "' no existe"));

        Optional<Contacto> existente = contactoRepository.findByGestoriaIdAndTelefono(gestoriaId, telefonoNormalizado);
        ImportFilaResultado resultado;
        Contacto contacto;
        if (existente.isPresent()) {
            contacto = existente.get();
            if (!contacto.isActivo()) {
                throw new FilaImportacionException(MOTIVO_CONTACTO_INACTIVO);
            }
            resultado = ImportFilaResultado.ACTUALIZADA;
        } else {
            contacto = new Contacto();
            contacto.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
            contacto.setTelefono(telefonoNormalizado);
            contacto.setActivo(true);
            resultado = ImportFilaResultado.CREADA;
        }

        contacto.setNombre(nombre);
        contactoRepository.saveAndFlush(contacto);

        // Decision 15, antes de crear/actualizar el enlace: Contacto, Explotacion y usuario de la
        // misma Gestoria. Si falla, la excepcion revierte la fila entera (tambien el nombre).
        try {
            ContactoService.comprobarMismaGestoria(contacto, explotacion, gestoriaId);
        } catch (RecursoNoEncontradoException e) {
            throw new FilaImportacionException("La explotacion '" + codigoRega + "' no existe");
        }

        ContactoExplotacion enlace = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contacto.getId(), explotacion.getId(), gestoriaId)
                .orElseGet(() -> {
                    ContactoExplotacion nuevo = new ContactoExplotacion();
                    // La Gestoria del enlace sale del gestoriaId del JWT, nunca de los extremos.
                    nuevo.setGestoria(gestoriaRepository.getReferenceById(gestoriaId));
                    nuevo.setContacto(contacto);
                    nuevo.setExplotacion(explotacion);
                    return nuevo;
                });
        enlace.setRol(rol);
        contactoExplotacionRepository.saveAndFlush(enlace);
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
