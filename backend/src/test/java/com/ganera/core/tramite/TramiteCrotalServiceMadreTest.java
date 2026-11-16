package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Resolucion del crotal de la MADRE (V30, plan 2026-11-11) con el MISMO mecanismo que los demas
 * crotales: COMPLETO por igualdad, INCOMPLETO por sufijo, EN_INVENTARIO/AMBIGUO/NO_ENCONTRADO/
 * SIN_EXPLOTACION, SIEMPRE dentro de la Explotacion del Tramite y de su Gestoria (un Animal de otra
 * Explotacion/Gestoria nunca se usa). Mismos datos de dos Gestorias que TramiteCrotalServiceTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TramiteCrotalService.class)
class TramiteCrotalServiceMadreTest {

    @Autowired
    private TramiteCrotalService servicio;
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

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Explotacion explotacionA1;
    private Explotacion explotacionA2;
    private Explotacion explotacionB1;
    private Animal animalA1Unico;
    private Contacto contactoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria madre A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria madre B"));
        explotacionA1 = nuevaExplotacion(gestoriaA, "ES950000000001");
        explotacionA2 = nuevaExplotacion(gestoriaA, "ES950000000002");
        explotacionB1 = nuevaExplotacion(gestoriaB, "ES950000000101");

        // E1: un unico ...1234 y dos ...5678 (ambiguo por sufijo)
        animalA1Unico = nuevoAnimal(gestoriaA, explotacionA1, "ES010000001234");
        nuevoAnimal(gestoriaA, explotacionA1, "ES010000005678");
        nuevoAnimal(gestoriaA, explotacionA1, "ES020000005678");
        // E2 (misma gestoria): otro ...4321 solo en E2
        nuevoAnimal(gestoriaA, explotacionA2, "ES030000004321");
        // B: ...9999 solo en B
        nuevoAnimal(gestoriaB, explotacionB1, "ES040000009999");

        contactoA = nuevoContacto(gestoriaA, "+34600901001");
    }

    @Test
    void madreCompletaEnInventarioSeResuelveConElCrotalDelInventario() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "ES010000001234");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        assertThat(tramite.getCrotalMadre()).isEqualTo(animalA1Unico.getCrotal());
    }

    @Test
    void madreIncompletaConUnaCoincidenciaSeCompleta() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "1234");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("ES010000001234");
    }

    @Test
    void madreIncompletaConVariasCoincidenciasEsAmbiguaYSeQuedaLoIndicado() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "5678");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.AMBIGUO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("5678");
    }

    @Test
    void madreSinCoincidenciasEsNoEncontrada() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "7777");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("7777");
    }

    @Test
    void madreSinExplotacionQuedaSinExplotacion() {
        Tramite tramite = madre(gestoriaA, null, "1234");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.SIN_EXPLOTACION);
        assertThat(tramite.getCrotalMadre()).isEqualTo("1234");
    }

    /** Aislamiento: un Animal con el mismo sufijo en OTRA explotacion de la misma gestoria no completa. */
    @Test
    void unaMadreCuyoUnicoAnimalEstaEnOtraExplotacionNoSeResuelve() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "4321");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("4321");
    }

    /** Aislamiento: un Animal con el mismo sufijo en OTRA gestoria nunca se usa para completar/enlazar. */
    @Test
    void unaMadreCuyoUnicoAnimalEsDeOtraGestoriaNoSeResuelve() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "9999");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("9999");
    }

    @Test
    void sinMadreIndicadaDejaAmbasColumnasNull() {
        Tramite tramite = madre(gestoriaA, explotacionA1, null);
        tramite.setCrotalMadre("ES010000001234");
        tramite.setCrotalMadreResolucion(ResolucionCrotal.EN_INVENTARIO);

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadre()).isNull();
        assertThat(tramite.getCrotalMadreResolucion()).isNull();
    }

    @Test
    void madreEnBlancoDejaAmbasColumnasNull() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "   ");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadre()).isNull();
        assertThat(tramite.getCrotalMadreResolucion()).isNull();
    }

    /** Un crotal de madre invalido no rompe: se queda sin resolver (el PATCH ya valida el formato). */
    @Test
    void madreInvalidaNoRompeYSeQuedaSinResolver() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "ES_12");

        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadre()).isNull();
        assertThat(tramite.getCrotalMadreResolucion()).isNull();
    }

    /** #2: al cambiar la explotacion se re-resuelve desde lo INDICADO, no desde el crotal completado antes. */
    @Test
    void alCambiarDeExplotacionLaMadreSeReResuelveDesdeLoIndicado() {
        Tramite tramite = madre(gestoriaA, explotacionA1, "1234");
        servicio.resolverMadre(tramite, gestoriaA.getId());
        assertThat(tramite.getCrotalMadre()).isEqualTo("ES010000001234");

        // E1 -> E2: "1234" ya no esta (E2 solo tiene ...4321) -> NO_ENCONTRADO, y vuelve a lo indicado.
        tramite.setExplotacion(explotacionA2);
        servicio.resolverMadre(tramite, gestoriaA.getId());

        assertThat(tramite.getCrotalMadreResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(tramite.getCrotalMadre()).isEqualTo("1234");
    }

    // --- utilidades ---

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
        contacto.setNombre("Contacto madre");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite madre(Gestoria gestoria, Explotacion explotacion, String crotalMadreIndicado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contactoA);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setCrotalMadreIndicado(crotalMadreIndicado);
        return tramiteRepository.save(tramite);
    }
}
