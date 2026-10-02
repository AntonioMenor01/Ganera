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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Reglas de TramiteRevisionService (PATCH, aprobar, rechazar) sin HTTP. Aqui solo se comprueban
 * las decisiones y los motivos; que un 409/400 no deja nada escrito (rollback real) se comprueba
 * en TramiteRevisionEndToEndTest, porque en @DataJpaTest todo el test es UNA transaccion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({TramiteRevisionService.class, TramiteCrotalService.class})
class TramiteRevisionServiceTest {

    @Autowired
    private TramiteRevisionService servicio;
    @Autowired
    private TramiteCrotalService tramiteCrotalService;
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
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Explotacion explotacionA1;
    private Explotacion explotacionA2;
    private Explotacion explotacionB;
    private Animal animalA1;
    private Contacto contactoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria revision A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria revision B"));
        explotacionA1 = nuevaExplotacion(gestoriaA, "ES970000000001");
        explotacionA2 = nuevaExplotacion(gestoriaA, "ES970000000002");
        explotacionB = nuevaExplotacion(gestoriaB, "ES970000000101");
        animalA1 = nuevoAnimal(gestoriaA, explotacionA1, "ES970000011234");
        nuevoAnimal(gestoriaA, explotacionA1, "ES970000015678");
        nuevoAnimal(gestoriaA, explotacionA1, "ES970000025678");
        nuevoAnimal(gestoriaA, explotacionA2, "ES970000031234");
        nuevoAnimal(gestoriaB, explotacionB, "ES970000049999");
        contactoA = nuevoContacto(gestoriaA, "+34600970001");
    }

    // --- PATCH ---

    @Test
    void actualizarAsignaExplotacionTipoYCrotales() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);

        TramiteDetalleResponse respuesta = servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), version(tramite), explotacionA1.getId(), TipoTramite.BAJA, List.of("1234", "5678", "9999"));

        assertThat(respuesta.explotacionId()).isEqualTo(explotacionA1.getId());
        assertThat(respuesta.explotacionCodigoRega()).isEqualTo("ES970000000001");
        assertThat(respuesta.tipoTramite()).isEqualTo("BAJA");
        assertThat(respuesta.estado()).isEqualTo("PENDIENTE_REVISION");
        assertThat(respuesta.crotales()).containsExactly(
                new TramiteCrotalResponse("1234", "ES970000011234", false, animalA1.getId(), true, "EN_INVENTARIO"),
                new TramiteCrotalResponse("5678", "5678", false, null, false, "AMBIGUO"),
                // ...9999 solo existe en B: nunca se enlaza
                new TramiteCrotalResponse("9999", "9999", false, null, false, "NO_ENCONTRADO"));
    }

    @Test
    void actualizarConCamposNulosNoCambiaNada() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        TramiteDetalleResponse respuesta = servicio.actualizar(gestoriaA.getId(), tramite.getId(), version(tramite), null, null, null);

        assertThat(respuesta.explotacionId()).isEqualTo(explotacionA1.getId());
        assertThat(respuesta.tipoTramite()).isEqualTo("ALTA");
        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::crotal).containsExactly("ES970000011234");
    }

    @Test
    void cambiarSoloLaExplotacionVuelveAResolverLosCrotales() {
        Tramite tramite = nuevoTramite(explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        TramiteDetalleResponse respuesta = servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), version(tramite), explotacionA2.getId(), null, null);

        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::crotal).containsExactly("ES970000031234");
    }

    @Test
    void cambiarExplotacionYCrotalesResuelveContraLaNuevaExplotacion() {
        Tramite tramite = nuevoTramite(explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        TramiteDetalleResponse respuesta = servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), version(tramite), explotacionA2.getId(), null, List.of("1234"));

        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::crotal).containsExactly("ES970000031234");
    }

    @Test
    void actualizarUnTramiteDeOtraGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.actualizar(gestoriaB.getId(), tramite.getId(), version(tramite), null, TipoTramite.ALTA, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(tramite.getTipoTramite()).isNull();
    }

    @Test
    void asignarUnaExplotacionDeOtraGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), version(tramite), explotacionB.getId(), null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(tramite.getExplotacion()).isNull();
    }

    @Test
    void actualizarFueraDePendienteRevisionEsConflicto() {
        for (EstadoTramite estado : List.of(EstadoTramite.APROBADO, EstadoTramite.RECHAZADO,
                EstadoTramite.PENDIENTE_EXTRACCION, EstadoTramite.EN_PROCESO,
                EstadoTramite.EJECUTADO_OVZ, EstadoTramite.ERROR_OVZ)) {
            Tramite tramite = nuevoTramite(null, null, estado);

            assertThatThrownBy(() -> servicio.actualizar(gestoriaA.getId(), tramite.getId(), version(tramite), null, TipoTramite.ALTA, null))
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_EDITAR_SOLO_PENDIENTE);
            assertThat(tramite.getTipoTramite()).isNull();
        }
    }

    @Test
    void actualizarConUnCrotalInvalidoLanzaCrotalInvalido() {
        Tramite tramite = nuevoTramite(explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.actualizar(gestoriaA.getId(), tramite.getId(), version(tramite), null, null, List.of("12_34")))
                .isInstanceOf(CrotalInvalidoException.class);
    }

    @Test
    void dosCrotalesQueResuelvenAlMismoAnimalEsConflictoConMotivo() {
        Tramite tramite = nuevoTramite(explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), version(tramite), null, null, List.of("1234", "ES-9700-0001-1234")))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Los crotales 1234 y ES970000011234 corresponden al mismo animal (ES970000011234).");
    }

    // --- aprobar ---

    @Test
    void aprobarUnTramiteCompletoLoDejaAprobado() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        TramiteResponse respuesta = servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite));

        assertThat(respuesta.estado()).isEqualTo("APROBADO");
        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::resolucion).containsExactly("EN_INVENTARIO");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.APROBADO);
    }

    @Test
    void aprobarSinCrotalesSePermite() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);

        assertThat(servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)).estado()).isEqualTo("APROBADO");
    }

    @Test
    void aprobarConUnCrotalCompletoNoEncontradoSePermite() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("ES970000088888"), gestoriaA.getId());

        TramiteResponse respuesta = servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite));

        assertThat(respuesta.estado()).isEqualTo("APROBADO");
        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::resolucion).containsExactly("NO_ENCONTRADO");
    }

    @Test
    void aprobarSinExplotacionEsConflicto() {
        Tramite tramite = nuevoTramite(null, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Falta asignar la explotación.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarSinTipoEsConflicto() {
        Tramite tramite = nuevoTramite(explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Falta el tipo de trámite.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    @Test
    void aprobarSinExplotacionNiTipoNombraAmbos() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Falta asignar la explotación. Falta el tipo de trámite.");
    }

    @Test
    void aprobarConCrotalSinExplotacionNombraElCrotal() {
        Tramite tramite = nuevoTramite(null, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Falta asignar la explotación. "
                        + "El crotal 1234 no se puede comprobar porque el trámite no tiene explotación.");
    }

    @Test
    void aprobarConCrotalAmbiguoOIncompletoNoEncontradoNombraCadaCrotal() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(
                tramite, List.of("5678", "1234", "4444", "ES970000077777"), gestoriaA.getId());

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("El crotal 5678 es ambiguo (varios animales coinciden). "
                        + "El crotal 4444 no está en el inventario y está incompleto.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    /**
     * Revision 7a I1, opcion (a): el formato de la decision 28 se exige TAMBIEN a un crotal
     * EN_INVENTARIO, sobre el crotal completo resuelto (el del Animal). Un inventario importado
     * sin "ES" resuelve bien "1234", pero OVZ recibiria un crotal incompleto -> 409 acumulable con
     * el resto de motivos, sin tocar la resolucion.
     */
    @Test
    void aprobarUnCrotalEnInventarioConFormatoIncompletoEsConflictoNombrandoloYNoCambiaNada() {
        Explotacion sinPrefijo = nuevaExplotacion(gestoriaA, "ES970000000003");
        Animal incompleto = nuevoAnimal(gestoriaA, sinPrefijo, "010000001234");
        Tramite tramite = nuevoTramite(sinPrefijo, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        long v0 = version(tramite);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), v0))
                .isInstanceOf(TramiteConflictoException.class)
                .isNotInstanceOf(ResolucionCrotalesCambiadaException.class)
                .hasMessage("El crotal 1234 está en el inventario como 010000001234, que no es un crotal completo "
                        + "válido. Vuelve a importar el inventario con el crotal completo.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(filas(tramite)).extracting(TramiteCrotal::getResolucion, TramiteCrotal::getCrotal,
                        f -> f.getAnimal().getId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        ResolucionCrotal.EN_INVENTARIO, "010000001234", incompleto.getId()));
    }

    /** Se acumula con los demas motivos de la decision 25, en el orden de las filas. */
    @Test
    void elCrotalDeInventarioIncompletoSeAcumulaConLosDemasMotivos() {
        Explotacion sinPrefijo = nuevaExplotacion(gestoriaA, "ES970000000004");
        nuevoAnimal(gestoriaA, sinPrefijo, "010000001234");
        Tramite tramite = nuevoTramite(sinPrefijo, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234", "4444"), gestoriaA.getId());

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Falta el tipo de trámite. "
                        + "El crotal 1234 está en el inventario como 010000001234, que no es un crotal completo "
                        + "válido. Vuelve a importar el inventario con el crotal completo. "
                        + "El crotal 4444 no está en el inventario y está incompleto.");
    }

    @Test
    void aprobarUnCrotalEnInventarioConFormatoCompletoSePermite() {
        Explotacion conPrefijo = nuevaExplotacion(gestoriaA, "ES970000000005");
        Animal completo = nuevoAnimal(gestoriaA, conPrefijo, "ES010000001234");
        Tramite tramite = nuevoTramite(conPrefijo, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        TramiteResponse respuesta = servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite));

        assertThat(respuesta.estado()).isEqualTo("APROBADO");
        assertThat(respuesta.crotales()).containsExactly(
                new TramiteCrotalResponse("1234", "ES010000001234", false, completo.getId(), true, "EN_INVENTARIO"));
    }

    /**
     * Decision 28: un NO_ENCONTRADO completo solo se aprueba con formato de crotal completo
     * plausible: ES + 12 digitos, u otro pais (2 letras) + 8 a 12 digitos. La clasificacion de la
     * resolucion no cambia (siguen siendo NO_ENCONTRADO); solo la regla de aprobacion.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ES123456789012", "FR12345678", "DE123456789012",
            "IT1234567890"})
    void aprobarUnNoEncontradoConFormatoCompletoValidoSePermite(String crotal) {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of(crotal), gestoriaA.getId());

        TramiteResponse respuesta = servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite));

        assertThat(respuesta.estado()).isEqualTo("APROBADO");
        assertThat(respuesta.crotales()).extracting(TramiteCrotalResponse::resolucion).containsExactly("NO_ENCONTRADO");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ES12345678901", "ES1234", "ES1234567890123",
            "FR1234567", "DE1234567890123", "A1234", "1234567890123", "12A34", "ESP12345678901",
            "FR12345A78", "ES12345678901A"})
    void aprobarUnNoEncontradoSinFormatoCompletoValidoEsConflictoNombrandoElCrotal(String crotal) {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of(crotal), gestoriaA.getId());
        assertThat(filas(tramite)).extracting(TramiteCrotal::getResolucion).containsExactly(ResolucionCrotal.NO_ENCONTRADO);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("El crotal " + crotal + " no está en el inventario y no tiene un formato completo válido.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    /**
     * Una resolucion guardada no se da por buena: el inventario pudo cambiar desde el PATCH. Si la
     * re-resolucion cambia algo, 409 especifico (no se aprueba lo que el revisor no vio) y la
     * nueva resolucion queda en las filas; el estado no cambia.
     */
    @Test
    void aprobarConUnCrotalQueAhoraEsAmbiguoEsConflictoDeResolucionCambiada() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        // Despues de resolverse, entra otro animal con el mismo sufijo en la explotacion.
        nuevoAnimal(gestoriaA, explotacionA1, "ES970000091234");

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(ResolucionCrotalesCambiadaException.class)
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "1234 antes ES970000011234, ahora ambiguo. Revisa el trámite antes de aprobarlo.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(filas(tramite)).extracting(TramiteCrotal::getResolucion).containsExactly(ResolucionCrotal.AMBIGUO);
    }

    /** Escenario A de la revision: el sufijo pasa a resolver a OTRO animal -> nunca 200 directo. */
    @Test
    void aprobarConUnCrotalQueAhoraResuelveAOtroAnimalEsConflictoYNoAprueba() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        animalA1.setExplotacion(explotacionA2);
        animalRepository.save(animalA1);
        Animal otro = nuevoAnimal(gestoriaA, explotacionA1, "ES970000061234");

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(ResolucionCrotalesCambiadaException.class)
                .hasMessage("La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "1234 antes ES970000011234, ahora ES970000061234. Revisa el trámite antes de aprobarlo.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(filas(tramite).get(0).getAnimal().getId()).isEqualTo(otro.getId());

        // Segundo intento: la resolucion ya esta al dia -> reglas normales -> aprobado.
        assertThat(servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)).estado()).isEqualTo("APROBADO");
    }

    /** Escenario B: EN_INVENTARIO pasa a NO_ENCONTRADO (completo) -> tampoco se aprueba sin revisar. */
    @Test
    void aprobarConUnCrotalQueHaSalidoDelInventarioEsConflictoDeResolucionCambiada() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.BAJA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("ES970000011234"), gestoriaA.getId());
        animalA1.setExplotacion(explotacionA2);
        animalRepository.save(animalA1);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(ResolucionCrotalesCambiadaException.class)
                .hasMessage("La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "ES970000011234 antes ES970000011234, ahora no está en el inventario. "
                        + "Revisa el trámite antes de aprobarlo.");
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    /**
     * Revision M3: aunque el Tramite ya estuviera en el contexto de persistencia (cargado antes en
     * la misma peticion), el servicio trabaja con la fila ACTUAL de la BD tras bloquearla.
     */
    @Test
    void aprobarUsaElEstadoActualDeLaBdAunqueElTramiteYaEstuvieraCargado() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        entityManager.flush();
        jdbcTemplate.update("update tramite set estado = 'APROBADO' where id = ?", tramite.getId());
        assertThat(tramite.getEstado()).as("instancia en cache, obsoleta").isEqualTo(EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_APROBAR_SOLO_PENDIENTE);
        assertThatThrownBy(() -> servicio.rechazar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_RECHAZAR_SOLO_PENDIENTE);
        assertThatThrownBy(() -> servicio.actualizar(gestoriaA.getId(), tramite.getId(), version(tramite), null, TipoTramite.BAJA, null))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_EDITAR_SOLO_PENDIENTE);
    }

    @Test
    void aprobarConDosCrotalesDelMismoAnimalEsConflicto() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        // Sembrado directo (el PATCH ya lo impediria): simula un tramite creado por otra via.
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234", "ES970000011234"), gestoriaA.getId());

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("Los crotales 1234 y ES970000011234 corresponden al mismo animal (ES970000011234).");
    }

    @Test
    void aprobarFueraDePendienteRevisionEsConflicto() {
        for (EstadoTramite estado : List.of(EstadoTramite.APROBADO, EstadoTramite.RECHAZADO, EstadoTramite.ERROR_OVZ)) {
            Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, estado);

            assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_APROBAR_SOLO_PENDIENTE);
            assertThat(tramite.getEstado()).isEqualTo(estado);
        }
    }

    @Test
    void aprobarUnTramiteDeOtraGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaB.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    // --- rechazar ---

    @Test
    void rechazarNoExigeExplotacionNiTipo() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        TramiteResponse respuesta = servicio.rechazar(gestoriaA.getId(), tramite.getId(), version(tramite));

        assertThat(respuesta.estado()).isEqualTo("RECHAZADO");
        assertThat(respuesta.crotales()).hasSize(1);
    }

    @Test
    void rechazarFueraDePendienteRevisionEsConflicto() {
        for (EstadoTramite estado : List.of(EstadoTramite.APROBADO, EstadoTramite.RECHAZADO)) {
            Tramite tramite = nuevoTramite(null, null, estado);

            assertThatThrownBy(() -> servicio.rechazar(gestoriaA.getId(), tramite.getId(), version(tramite)))
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_RECHAZAR_SOLO_PENDIENTE);
            assertThat(tramite.getEstado()).isEqualTo(estado);
        }
    }

    @Test
    void rechazarUnTramiteDeOtraGestoriaEsNoEncontrado() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);

        assertThatThrownBy(() -> servicio.rechazar(gestoriaB.getId(), tramite.getId(), version(tramite)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }

    /** Mini-prompt tras A2 (punto 4): rechazar exige la version que mostraba la pantalla, igual
     * que PATCH y aprobar. Distinta (o null) -> 409 MOTIVO_VERSION_DESFASADA y nada cambia. */
    @Test
    void rechazarConVersionDistintaEsConflictoYNoCambiaNada() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);
        long v0 = version(tramite);

        for (Long otra : java.util.Arrays.asList(v0 + 1, v0 - 1, null)) {
            assertThatThrownBy(() -> servicio.rechazar(gestoriaA.getId(), tramite.getId(), otra))
                    .as("version " + otra)
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        }
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(v0);
    }

    /** El estado se comprueba antes que la version: un Tramite ya no pendiente da su motivo
     * concreto aunque la version tambien este desfasada. */
    @Test
    void rechazarFueraDePendienteConVersionDesfasadaDaElMotivoDeEstado() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.APROBADO);

        assertThatThrownBy(() -> servicio.rechazar(gestoriaA.getId(), tramite.getId(), version(tramite) + 7))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_RECHAZAR_SOLO_PENDIENTE);
    }

    @Test
    void unIdInexistenteEsNoEncontrado() {
        assertThatThrownBy(() -> servicio.rechazar(gestoriaA.getId(), 999999L, 0L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), 999999L, 0L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.actualizar(gestoriaA.getId(), 999999L, 0L, null, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // --- version optimista (decision 27) ---

    @Test
    void cadaPatchAceptadoIncrementaLaVersionExactamenteEnUno() {
        Tramite tramite = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);
        long v0 = version(tramite);

        // Solo tipo (el Tramite queda sucio): +1, no +2.
        TramiteDetalleResponse r1 = servicio.actualizar(gestoriaA.getId(), tramite.getId(), v0, null, TipoTramite.ALTA, null);
        assertThat(version(tramite)).isEqualTo(v0 + 1);
        assertThat(r1.version()).isEqualTo(v0 + 1);

        // Solo crotales (solo cambia tramite_crotal): el incremento se fuerza.
        TramiteDetalleResponse r2 = servicio.actualizar(gestoriaA.getId(), tramite.getId(), v0 + 1, null, null, List.of("1234"));
        assertThat(version(tramite)).isEqualTo(v0 + 2);
        assertThat(r2.version()).isEqualTo(v0 + 2);

        // Explotacion + crotales a la vez: +1.
        TramiteDetalleResponse r3 = servicio.actualizar(
                gestoriaA.getId(), tramite.getId(), v0 + 2, explotacionA1.getId(), null, List.of("1234"));
        assertThat(version(tramite)).isEqualTo(v0 + 3);
        assertThat(r3.version()).isEqualTo(v0 + 3);

        // Un PATCH aceptado sin cambios tambien cuenta como escritura.
        TramiteDetalleResponse r4 = servicio.actualizar(gestoriaA.getId(), tramite.getId(), v0 + 3, null, null, null);
        assertThat(version(tramite)).isEqualTo(v0 + 4);
        assertThat(r4.version()).isEqualTo(v0 + 4);
    }

    @Test
    void actualizarConUnaVersionDistintaEsConflictoSinCambiar() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        long v0 = version(tramite);
        servicio.actualizar(gestoriaA.getId(), tramite.getId(), v0, null, TipoTramite.BAJA, null);

        for (Long otra : java.util.Arrays.asList(v0, v0 + 2, null)) {
            assertThatThrownBy(() -> servicio.actualizar(
                    gestoriaA.getId(), tramite.getId(), otra, explotacionA2.getId(), TipoTramite.ALTA, List.of("5678")))
                    .as("version " + otra)
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        }
        assertThat(version(tramite)).isEqualTo(v0 + 1);
        assertThat(tramite.getTipoTramite()).isEqualTo(TipoTramite.BAJA);
        assertThat(tramite.getExplotacion().getId()).isEqualTo(explotacionA1.getId());
        assertThat(filas(tramite)).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("1234");
    }

    @Test
    void aprobarConUnaVersionDistintaEsConflictoYNoAprueba() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        long v0 = version(tramite);

        for (Long otra : java.util.Arrays.asList(v0 + 1, v0 - 1, null)) {
            assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), otra))
                    .as("version " + otra)
                    .isInstanceOf(TramiteConflictoException.class)
                    .hasMessage(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        }
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(version(tramite)).isEqualTo(v0);
    }

    @Test
    void aprobarYRechazarIncrementanLaVersion() {
        Tramite aprobable = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        Tramite rechazable = nuevoTramite(null, null, EstadoTramite.PENDIENTE_REVISION);
        long va = version(aprobable);
        long vr = version(rechazable);

        TramiteResponse aprobado = servicio.aprobar(gestoriaA.getId(), aprobable.getId(), va);
        TramiteResponse rechazado = servicio.rechazar(gestoriaA.getId(), rechazable.getId(), vr);

        assertThat(version(aprobable)).isEqualTo(va + 1);
        assertThat(aprobado.version()).isEqualTo(va + 1);
        assertThat(version(rechazable)).isEqualTo(vr + 1);
        assertThat(rechazado.version()).isEqualTo(vr + 1);
    }

    /** La re-resolucion al aprobar solo toca tramite_crotal: aun asi incrementa la version. */
    @Test
    void laReResolucionAlAprobarIncrementaLaVersionYLaVersionAntiguaYaNoVale() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        long v0 = version(tramite);
        // Entra otro animal con el mismo sufijo: "1234" pasa a ser ambiguo.
        nuevoAnimal(gestoriaA, explotacionA1, "ES970000081234");

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), v0))
                .isInstanceOf(ResolucionCrotalesCambiadaException.class);
        assertThat(version(tramite)).isEqualTo(v0 + 1);

        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), v0))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        assertThat(version(tramite)).isEqualTo(v0 + 1);

        // Con la version nueva: reglas normales (ahora es ambiguo -> 409 normal, sin incremento
        // aqui porque en @DataJpaTest no hay rollback que comprobar; eso lo cubre el E2E).
        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), tramite.getId(), v0 + 1))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage("El crotal 1234 es ambiguo (varios animales coinciden).");
    }

    /** Orden: 404 antes que la version; estado antes que la version (motivo mas concreto). */
    @Test
    void elOrdenEs404LuegoEstadoLuegoVersion() {
        Tramite tramite = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.PENDIENTE_REVISION);
        long v0 = version(tramite);
        for (Long cualquiera : java.util.Arrays.asList(v0, v0 + 7, null)) {
            assertThatThrownBy(() -> servicio.aprobar(gestoriaB.getId(), tramite.getId(), cualquiera))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> servicio.actualizar(gestoriaB.getId(), tramite.getId(), cualquiera, null, null, null))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        Tramite aprobado = nuevoTramite(explotacionA1, TipoTramite.ALTA, EstadoTramite.APROBADO);
        long va = version(aprobado);
        assertThatThrownBy(() -> servicio.aprobar(gestoriaA.getId(), aprobado.getId(), va + 3))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_APROBAR_SOLO_PENDIENTE);
        assertThatThrownBy(() -> servicio.actualizar(gestoriaA.getId(), aprobado.getId(), va + 3, null, null, null))
                .isInstanceOf(TramiteConflictoException.class)
                .hasMessage(TramiteRevisionService.MOTIVO_EDITAR_SOLO_PENDIENTE);
    }

    // --- utilidades ---

    /** Version actual en BD (tras volcar lo pendiente del contexto de persistencia). */
    private long version(Tramite tramite) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramite.getId());
    }

    private List<TramiteCrotal> filas(Tramite tramite) {
        entityManager.flush();
        return tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaA.getId());
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
        contacto.setNombre("Contacto revision");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Explotacion explotacion, TipoTramite tipo, EstadoTramite estado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoriaA);
        tramite.setContacto(contactoA);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(tipo);
        tramite.setEstado(estado);
        return tramiteRepository.save(tramite);
    }
}
