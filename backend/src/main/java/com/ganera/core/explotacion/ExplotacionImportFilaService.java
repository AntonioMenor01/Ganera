package com.ganera.core.explotacion;

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
 * Procesa una fila del importador Excel (Explotacion o Animal) en su PROPIA transaccion
 * (REQUIRES_NEW), en un bean separado de ExplotacionImportService a proposito: el proxy de
 * @Transactional de Spring solo intercepta llamadas que entran desde fuera del bean -- una
 * auto-invocacion (this.metodo()) lo ignora en silencio. Sin este aislamiento, una violacion real
 * de constraint en saveAndFlush() (p.ej. un codigo_rega que ya existe en OTRA Gestoria, invisible
 * para el filtro de tenant) deja la sesion de Hibernate en un estado invalido -- "don't flush the
 * Session after an exception occurs" -- que rompe el procesado de las filas SIGUIENTES del mismo
 * fichero aunque esas filas no tengan ningun problema propio. Con REQUIRES_NEW, esa fila hace
 * rollback de su propia transaccion (con su propia conexion) sin tocar la del resto del import.
 *
 * <p><b>REQUIRES_NEW suspende el EntityManager de la request</b> (el de open-in-view, donde
 * {@code TenantFilterActivationInterceptor} activo {@code gestoriaFilter} al principio de la
 * request) y ata uno NUEVO, sin ningun filtro activo, a esta transaccion. Sin volver a activar el
 * filtro aqui con el {@code gestoriaId} recibido, cada fila se procesaria SIN aislamiento de
 * tenant -- silenciosamente. Por eso cada metodo empieza reactivando el filtro sobre su propio
 * EntityManager antes de tocar los repositorios.
 */
@Service
class ExplotacionImportFilaService {

    private final ExplotacionRepository explotacionRepository;
    private final AnimalRepository animalRepository;
    private final GanaderoRepository ganaderoRepository;
    private final GestoriaRepository gestoriaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    ExplotacionImportFilaService(
            ExplotacionRepository explotacionRepository,
            AnimalRepository animalRepository,
            GanaderoRepository ganaderoRepository,
            GestoriaRepository gestoriaRepository) {
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.gestoriaRepository = gestoriaRepository;
    }

    private void activarFiltroDeTenant(Long gestoriaId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarExplotacion(Long gestoriaId, String codigoRega, String nombre, String nif, String nombreGanadero) {
        activarFiltroDeTenant(gestoriaId);
        Gestoria gestoria = gestoriaRepository.getReferenceById(gestoriaId);
        Ganadero ganadero = ganaderoRepository.findByNif(nif)
                .orElseGet(() -> crearGanadero(gestoria, nif, nombreGanadero));

        Optional<Explotacion> existente = explotacionRepository.findByCodigoRega(codigoRega);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportFilaResultado procesarAnimal(Long gestoriaId, String crotal, String codigoRegaExplotacion) {
        activarFiltroDeTenant(gestoriaId);
        Explotacion explotacion = explotacionRepository.findByCodigoRega(codigoRegaExplotacion)
                .orElseThrow(() -> new IllegalStateException("La explotacion '" + codigoRegaExplotacion + "' no existe"));

        String ultimosDigitos = ultimosDigitos(crotal);
        Optional<Animal> existente = animalRepository.findByCrotal(crotal);
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
