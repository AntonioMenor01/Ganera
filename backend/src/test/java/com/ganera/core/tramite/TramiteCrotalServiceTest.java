package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Resolucion de crotales de un Tramite (decision 20) con DOS Gestorias con datos parecidos: los
 * Animales de B (y los de otras Explotaciones de A) comparten sufijo con los de la Explotacion del
 * Tramite para que un finder sin explotacionId/gestoriaId los encontrara.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TramiteCrotalService.class)
class TramiteCrotalServiceTest {

    @Autowired
    private TramiteCrotalService servicio;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private EntityManager entityManager;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Explotacion explotacionA1;
    private Explotacion explotacionA2;
    private Explotacion explotacionA3;
    private Explotacion explotacionB1;
    private Animal animalA1Unico;
    private Animal animalA1Doble1;
    private Animal animalA1Doble2;
    private Animal animalA2Mismo1234;
    private Animal animalA2Solo;
    private Animal animalB1;
    private Contacto contactoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria crotales A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria crotales B"));
        explotacionA1 = nuevaExplotacion(gestoriaA, "ES950000000001");
        explotacionA2 = nuevaExplotacion(gestoriaA, "ES950000000002");
        explotacionA3 = nuevaExplotacion(gestoriaA, "ES950000000003");
        explotacionB1 = nuevaExplotacion(gestoriaB, "ES950000000101");

        // E1: un unico ...1234, dos ...5678
        animalA1Unico = nuevoAnimal(gestoriaA, explotacionA1, "ES010000001234");
        animalA1Doble1 = nuevoAnimal(gestoriaA, explotacionA1, "ES010000005678");
        animalA1Doble2 = nuevoAnimal(gestoriaA, explotacionA1, "ES020000005678");
        // E2: otro ...1234 (distinto Animal) y uno ...4321 que solo esta en E2
        animalA2Mismo1234 = nuevoAnimal(gestoriaA, explotacionA2, "ES030000001234");
        animalA2Solo = nuevoAnimal(gestoriaA, explotacionA2, "ES030000004321");
        // B: ...9999 solo en B, y ...1234 tambien en B
        animalB1 = nuevoAnimal(gestoriaB, explotacionB1, "ES040000009999");
        nuevoAnimal(gestoriaB, explotacionB1, "ES040000001234");

        contactoA = nuevoContacto(gestoriaA, "+34600900001");
    }

    @Test
    void crotalCompletoEnInventarioSeEnlaza() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        List<TramiteCrotal> filas = servicio.reemplazarCrotales(tramite, List.of("es 0100 0000 1234"), gestoriaA.getId());

        assertThat(filas).hasSize(1);
        TramiteCrotal fila = filas.get(0);
        assertThat(fila.getCrotalIndicado()).isEqualTo("ES010000001234");
        assertThat(fila.getCrotal()).isEqualTo("ES010000001234");
        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        assertThat(fila.getAnimal().getId()).isEqualTo(animalA1Unico.getId());
        assertThat(fila.getGestoria().getId()).isEqualTo(gestoriaA.getId());
    }

    @Test
    void crotalCompletoQueNoEstaEnLaExplotacionEsNoEncontrado() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("ES999999999999"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getCrotal()).isEqualTo("ES999999999999");
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void crotalCompletoDeOtraExplotacionDeLaMismaGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("ES030000004321"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void crotalCompletoDeUnAnimalDeOtraGestoriaNuncaSeEnlaza() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of(animalB1.getCrotal()), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void incompletoConUnUnicoAnimalSeCompletaYSeEnlaza() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId()));

        // E2 y B tambien tienen un ...1234: solo cuenta el de E1.
        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        assertThat(fila.getCrotalIndicado()).isEqualTo("1234");
        assertThat(fila.getCrotal()).isEqualTo("ES010000001234");
        assertThat(fila.getAnimal().getId()).isEqualTo(animalA1Unico.getId());
    }

    @Test
    void incompletoDeSeisDigitosYDeDoceDigitosTambienSeResuelvenPorSufijo() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        List<TramiteCrotal> filas = servicio.reemplazarCrotales(
                tramite, List.of("001234", "010000001234"), gestoriaA.getId());

        assertThat(filas).extracting(TramiteCrotal::getResolucion)
                .containsExactly(ResolucionCrotal.EN_INVENTARIO, ResolucionCrotal.EN_INVENTARIO);
        assertThat(filas).extracting(TramiteCrotal::getCrotal)
                .containsExactly("ES010000001234", "ES010000001234");
    }

    @Test
    void incompletoConVariosAnimalesEsAmbiguoSinEnlace() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("5678"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.AMBIGUO);
        assertThat(fila.getCrotal()).isEqualTo("5678");
        assertThat(fila.getCrotalIndicado()).isEqualTo("5678");
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void incompletoSinAnimalesEsNoEncontrado() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("7777"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getCrotal()).isEqualTo("7777");
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void incompletoCuyoUnicoAnimalEsDeOtraExplotacionDeLaMismaGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("4321"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getCrotal()).isEqualTo("4321");
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void incompletoCuyoUnicoAnimalEsDeOtraGestoriaNuncaSeEnlaza() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);

        TramiteCrotal fila = unico(servicio.reemplazarCrotales(tramite, List.of("9999"), gestoriaA.getId()));

        assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(fila.getCrotal()).isEqualTo("9999");
        assertThat(fila.getAnimal()).isNull();
    }

    @Test
    void tramiteSinExplotacionGuardaTalCualComoSinExplotacion() {
        Tramite tramite = nuevoTramite(gestoriaA, null);

        List<TramiteCrotal> filas = servicio.reemplazarCrotales(
                tramite, List.of("1234", "ES010000001234"), gestoriaA.getId());

        assertThat(filas).extracting(TramiteCrotal::getResolucion)
                .containsOnly(ResolucionCrotal.SIN_EXPLOTACION);
        assertThat(filas).extracting(TramiteCrotal::getCrotal).containsExactly("1234", "ES010000001234");
        assertThat(filas).extracting(TramiteCrotal::getAnimal).containsOnlyNulls();
    }

    @Test
    void reemplazarSustituyeLaListaAnteriorYColapsaDuplicadosManteniendoElOrden() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);
        servicio.reemplazarCrotales(tramite, List.of("1234", "5678"), gestoriaA.getId());

        List<TramiteCrotal> filas = servicio.reemplazarCrotales(
                tramite, List.of("7777", "1234", " 7 777 ", "1234"), gestoriaA.getId());

        assertThat(filas).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("7777", "1234");
        entityManager.flush();
        entityManager.clear();
        assertThat(tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaA.getId()))
                .extracting(TramiteCrotal::getCrotalIndicado).containsExactly("7777", "1234");
    }

    @Test
    void reemplazarConListaVaciaBorraTodos() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);
        servicio.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        assertThat(servicio.reemplazarCrotales(tramite, List.of(), gestoriaA.getId())).isEmpty();
        entityManager.flush();
        assertThat(tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaA.getId()))
                .isEmpty();
    }

    @Test
    void reemplazarConUnCrotalInvalidoNoCambiaNada() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);
        servicio.reemplazarCrotales(tramite, List.of("1234", "5678"), gestoriaA.getId());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> servicio.reemplazarCrotales(
                tramite, List.of("7777", "ES_12", "4321"), gestoriaA.getId()))
                .isInstanceOf(CrotalInvalidoException.class);
        assertThatThrownBy(() -> servicio.reemplazarCrotales(
                tramite, List.of("7777", "123"), gestoriaA.getId()))
                .isInstanceOf(CrotalInvalidoException.class)
                .hasMessage("Crotal demasiado corto: indica al menos los últimos 4 dígitos");
        assertThatThrownBy(() -> servicio.reemplazarCrotales(
                tramite, Arrays.asList("7777", null), gestoriaA.getId()))
                .isInstanceOf(CrotalInvalidoException.class);

        entityManager.flush();
        entityManager.clear();
        assertThat(tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaA.getId()))
                .extracting(TramiteCrotal::getCrotalIndicado).containsExactly("1234", "5678");
    }

    @Test
    void recalcularEnlacesReResuelveDesdeElCrotalIndicadoAlCambiarDeExplotacion() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);
        servicio.reemplazarCrotales(tramite, List.of("1234", "4321", "ES010000005678"), gestoriaA.getId());

        // E1 -> E2: "1234" pasa a ser el Animal de E2 (otro), "4321" aparece, el completo de E1 ya no.
        tramite.setExplotacion(explotacionA2);
        tramiteRepository.save(tramite);
        servicio.recalcularEnlaces(tramite, gestoriaA.getId());
        List<TramiteCrotal> enE2 = releer(tramite);
        assertThat(enE2).extracting(TramiteCrotal::getCrotalIndicado)
                .containsExactly("1234", "4321", "ES010000005678");
        assertThat(enE2).extracting(TramiteCrotal::getResolucion).containsExactly(
                ResolucionCrotal.EN_INVENTARIO, ResolucionCrotal.EN_INVENTARIO, ResolucionCrotal.NO_ENCONTRADO);
        assertThat(enE2).extracting(TramiteCrotal::getCrotal)
                .containsExactly("ES030000001234", "ES030000004321", "ES010000005678");
        assertThat(enE2.get(0).getAnimal().getId()).isEqualTo(animalA2Mismo1234.getId());
        assertThat(enE2.get(1).getAnimal().getId()).isEqualTo(animalA2Solo.getId());
        assertThat(enE2.get(2).getAnimal()).isNull();

        // E2 -> E3 (sin Animales): todo NO_ENCONTRADO y el crotal vuelve a lo indicado.
        Tramite enE3 = tramiteRepository.findByIdAndGestoriaId(tramite.getId(), gestoriaA.getId()).orElseThrow();
        enE3.setExplotacion(explotacionA3);
        servicio.recalcularEnlaces(enE3, gestoriaA.getId());
        List<TramiteCrotal> filasE3 = releer(tramite);
        assertThat(filasE3).extracting(TramiteCrotal::getResolucion).containsOnly(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(filasE3).extracting(TramiteCrotal::getCrotal)
                .containsExactly("1234", "4321", "ES010000005678");
        assertThat(filasE3).extracting(TramiteCrotal::getAnimal).containsOnlyNulls();

        // E3 -> sin explotacion: SIN_EXPLOTACION. Y de vuelta a E1: el "1234" original otra vez.
        Tramite sinExplotacion = tramiteRepository.findByIdAndGestoriaId(tramite.getId(), gestoriaA.getId()).orElseThrow();
        sinExplotacion.setExplotacion(null);
        servicio.recalcularEnlaces(sinExplotacion, gestoriaA.getId());
        assertThat(releer(tramite)).extracting(TramiteCrotal::getResolucion)
                .containsOnly(ResolucionCrotal.SIN_EXPLOTACION);

        Tramite deVuelta = tramiteRepository.findByIdAndGestoriaId(tramite.getId(), gestoriaA.getId()).orElseThrow();
        deVuelta.setExplotacion(explotacionRepository.findByIdAndGestoriaId(explotacionA1.getId(), gestoriaA.getId()).orElseThrow());
        servicio.recalcularEnlaces(deVuelta, gestoriaA.getId());
        List<TramiteCrotal> enE1 = releer(tramite);
        assertThat(enE1).extracting(TramiteCrotal::getResolucion).containsExactly(
                ResolucionCrotal.EN_INVENTARIO, ResolucionCrotal.NO_ENCONTRADO, ResolucionCrotal.EN_INVENTARIO);
        assertThat(enE1).extracting(TramiteCrotal::getCrotal)
                .containsExactly("ES010000001234", "4321", "ES010000005678");
        assertThat(enE1.get(0).getAnimal().getId()).isEqualTo(animalA1Unico.getId());
        assertThat(enE1.get(2).getAnimal().getId()).isEqualTo(animalA1Doble1.getId());
    }

    @Test
    void conGestoriaDistintaDeLaDelTramiteSeRechazaSinTocarNada() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionA1);
        servicio.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        assertThatThrownBy(() -> servicio.reemplazarCrotales(tramite, List.of("5678"), gestoriaB.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.recalcularEnlaces(tramite, gestoriaB.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(releer(tramite)).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("1234");
    }

    /** Defensa (espiritu de la decision 15): un Tramite de A apuntando a una Explotacion de B no
     * deberia existir nunca (Task 6 lo impide), pero si llegara aqui no se resuelve contra B. */
    @Test
    void tramiteConExplotacionDeOtraGestoriaSeRechazaYNoEnlazaAnimalesDeB() {
        Tramite tramite = nuevoTramite(gestoriaA, explotacionB1);

        assertThatThrownBy(() -> servicio.reemplazarCrotales(tramite, List.of("9999"), gestoriaA.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        entityManager.flush();
        assertThat(tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaA.getId()))
                .isEmpty();
    }

    @Test
    void crotalesPorTramiteAgrupaEnUnaConsultaYSoloDevuelveLosDeLaGestoria() {
        Tramite t1 = nuevoTramite(gestoriaA, explotacionA1);
        Tramite t2 = nuevoTramite(gestoriaA, null);
        Tramite t3 = nuevoTramite(gestoriaA, explotacionA1);
        Contacto contactoB = nuevoContacto(gestoriaB, "+34600900002");
        Tramite tB = new Tramite();
        tB.setGestoria(gestoriaB);
        tB.setContacto(contactoB);
        tB.setExplotacion(explotacionB1);
        tB.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramiteRepository.save(tB);

        servicio.reemplazarCrotales(t1, List.of("1234", "5678"), gestoriaA.getId());
        servicio.reemplazarCrotales(t2, List.of("4321"), gestoriaA.getId());
        servicio.reemplazarCrotales(tB, List.of("9999"), gestoriaB.getId());
        entityManager.flush();
        entityManager.clear();

        Map<Long, List<TramiteCrotalResponse>> porTramite = servicio.crotalesPorTramite(
                List.of(t1.getId(), t2.getId(), t3.getId(), tB.getId()), gestoriaA.getId());

        assertThat(porTramite).containsOnlyKeys(t1.getId(), t2.getId());
        assertThat(porTramite.get(t1.getId())).containsExactly(
                new TramiteCrotalResponse("1234", "ES010000001234", animalA1Unico.getId(), true, "EN_INVENTARIO"),
                new TramiteCrotalResponse("5678", "5678", null, false, "AMBIGUO"));
        assertThat(porTramite.get(t2.getId())).containsExactly(
                new TramiteCrotalResponse("4321", "4321", null, false, "SIN_EXPLOTACION"));
        assertThat(servicio.crotalesPorTramite(List.of(), gestoriaA.getId())).isEmpty();
    }

    // --- utilidades ---

    private List<TramiteCrotal> releer(Tramite tramite) {
        entityManager.flush();
        entityManager.clear();
        return tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(
                tramite.getId(), tramite.getGestoria().getId());
    }

    private static TramiteCrotal unico(List<TramiteCrotal> filas) {
        assertThat(filas).hasSize(1);
        return filas.get(0);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Animal nuevoAnimal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        return animalRepository.save(animal);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto crotales");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Explotacion explotacion) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contactoA);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }
}
