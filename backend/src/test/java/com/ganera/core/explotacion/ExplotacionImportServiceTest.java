package com.ganera.core.explotacion;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * ExplotacionImportFilaService procesa cada fila en su propia transaccion REQUIRES_NEW -- una
 * transaccion real, en su propia conexion, independiente de la transaccion de rollback que
 * @DataJpaTest envuelve alrededor de cada metodo de test. Eso significa que los datos de setup
 * (Gestoria, etc.) deben comprometerse DE VERDAD antes de llamar a importar(), o la transaccion
 * REQUIRES_NEW no los vera (no estan comprometidos todavia desde su punto de vista) y toda fila
 * fallara por violacion de FK. Se usa TestTransaction.flagForCommit()+end() para eso, y
 * TestTransaction.start() despues para volver a tener una transaccion activa (con lazy-loading
 * disponible) para las aserciones -- esa ultima transaccion si hace rollback automatico al
 * terminar el test, pero los datos ya comprometidos por REQUIRES_NEW no se deshacen con ella, asi
 * que el @AfterEach limpia las tablas explicitamente para no contaminar el resto de la suite
 * (todas las clases de test comparten la misma base H2 en memoria durante toda la ejecucion).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({ExplotacionImportService.class, ExplotacionImportFilaService.class,
        com.ganera.core.tramite.TramiteCrotalService.class})
class ExplotacionImportServiceTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    /** Spy (no mock): se comporta como el repositorio real, pero permite verificar que el
     * importador nunca llama a findByTelefono (decision 11). */
    @MockitoSpyBean
    private ContactoRepository contactoRepository;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private ExplotacionImportService explotacionImportService;
    @Autowired
    private com.ganera.core.tramite.TramiteRepository tramiteRepository;
    @Autowired
    private com.ganera.core.tramite.TramiteCrotalService tramiteCrotalService;

    @AfterEach
    void limpiarDatosComprometidosPorLasTransaccionesRequiresNew() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        contactoExplotacionRepository.deleteAll();
        contactoRepository.deleteAll();
        animalRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void importaExplotacionesYAnimalesCreandoGanaderosYMarcandoErroresDeFila() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba import"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(archivoDePrueba(), gestoria);

        TestTransaction.start();

        assertThat(resumen.explotaciones().filasProcesadas()).isEqualTo(2);
        assertThat(resumen.explotaciones().creadas()).isEqualTo(2);
        assertThat(resumen.explotaciones().actualizadas()).isEqualTo(0);

        assertThat(resumen.animales().filasProcesadas()).isEqualTo(4);
        assertThat(resumen.animales().creadas()).isEqualTo(2);
        assertThat(resumen.animales().actualizadas()).isEqualTo(0);

        assertThat(resumen.errores()).hasSize(2);
        assertThat(resumen.errores()).anySatisfy(error -> {
            assertThat(error.hoja()).isEqualTo(ExplotacionImportService.HOJA_ANIMALES);
            assertThat(error.motivo()).contains("no soportada");
        });
        assertThat(resumen.errores()).anySatisfy(error -> {
            assertThat(error.hoja()).isEqualTo(ExplotacionImportService.HOJA_ANIMALES);
            assertThat(error.motivo()).contains("no existe");
        });

        assertThat(ganaderoRepository.count()).isEqualTo(2);
        assertThat(explotacionRepository.count()).isEqualTo(2);
        assertThat(animalRepository.count()).isEqualTo(2);

        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId("ES120000000001", gestoria.getId()).orElseThrow();
        assertThat(explotacion.getGanadero().getNif()).isEqualTo("12345678A");
        assertThat(explotacion.getGanadero().getNombre()).isEqualTo("Juan Perez Gonzalez");

        Animal animal = animalRepository.findByCrotalAndGestoriaId("ES123456789012", gestoria.getId()).orElseThrow();
        assertThat(animal.getCrotalUltimosDigitos()).isEqualTo("789012");
        assertThat(animal.getExplotacion().getCodigoRega()).isEqualTo("ES120000000001");
    }

    @Test
    void reimportarElMismoFicheroActualizaEnVezDeDuplicar() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba reimport"));
        comprometerSetup();

        explotacionImportService.importar(archivoDePrueba(), gestoria);
        ImportResumenResponse segundoResumen = explotacionImportService.importar(archivoDePrueba(), gestoria);

        TestTransaction.start();

        assertThat(segundoResumen.explotaciones().creadas()).isEqualTo(0);
        assertThat(segundoResumen.explotaciones().actualizadas()).isEqualTo(2);
        assertThat(segundoResumen.animales().creadas()).isEqualTo(0);
        assertThat(segundoResumen.animales().actualizadas()).isEqualTo(2);
        assertThat(segundoResumen.errores()).hasSize(2);

        assertThat(ganaderoRepository.count()).isEqualTo(2);
        assertThat(explotacionRepository.count()).isEqualTo(2);
        assertThat(animalRepository.count()).isEqualTo(2);
    }

    @Test
    void filaDeAnimalConExplotacionInexistenteSeMarcaComoErrorSinAbortarElResto() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba error"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(archivoDePrueba(), gestoria);

        TestTransaction.start();

        assertThat(resumen.errores())
                .anySatisfy(error -> assertThat(error.motivo()).contains("ES999999999999"));
        // El resto de filas de la hoja Animales se procesaron pese al error de esta fila.
        assertThat(resumen.animales().creadas()).isEqualTo(2);
    }

    /**
     * Una Explotacion de OTRA Gestoria ya tiene el codigo_rega que trae la fila 2 del fichero.
     * ExplotacionImportFilaService reactiva el filtro de tenant dentro de su propia transaccion
     * REQUIRES_NEW usando el gestoriaId recibido, asi que no la ve (esta "oculta" por el filtro)
     * y el import intenta crearla -> viola la UNIQUE global real de codigo_rega en saveAndFlush.
     * Esto comprueba que esa violacion real, dentro de su propia transaccion aislada, no deja
     * inutilizable la sesion usada para procesar las filas POSTERIORES del mismo fichero.
     */
    @Test
    void unaColisionRealDeConstraintAMitadDelFicheroNoImpideProcesarLasFilasPosteriores() throws IOException {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena"));
        Ganadero ganaderoAjeno = new Ganadero();
        ganaderoAjeno.setGestoria(gestoriaAjena);
        ganaderoAjeno.setNombre("Ganadero ajeno");
        ganaderoRepository.save(ganaderoAjeno);
        Explotacion explotacionAjena = new Explotacion();
        explotacionAjena.setGestoria(gestoriaAjena);
        explotacionAjena.setGanadero(ganaderoAjeno);
        explotacionAjena.setCodigoRega("ES500000000001");
        explotacionAjena.setNombre("Finca ajena");
        explotacionRepository.save(explotacionAjena);

        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria propia colision"));
        comprometerSetup();

        MockMultipartFile archivo = construirExcel(
                new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"},
                List.<String[]>of(
                        new String[]{"ES500000000010", "Finca antes", "10000001A", "Ganadero Antes"},
                        new String[]{"ES500000000001", "Finca colisionando", "10000002B", "Ganadero Colision"},
                        new String[]{"ES500000000011", "Finca despues", "10000003C", "Ganadero Despues"}),
                new String[]{"crotal", "especie", "codigo_rega_explotacion"},
                List.of());

        ImportResumenResponse resumen = explotacionImportService.importar(archivo, gestoria);

        TestTransaction.start();

        assertThat(resumen.errores()).hasSize(1);
        assertThat(resumen.errores().get(0).fila()).isEqualTo(3);

        assertThat(explotacionRepository.findByCodigoRegaAndGestoriaId("ES500000000010", gestoria.getId()))
                .as("la fila anterior a la colision debe haberse creado")
                .isPresent();
        assertThat(explotacionRepository.findByCodigoRegaAndGestoriaId("ES500000000011", gestoria.getId()))
                .as("la fila POSTERIOR a la colision debe seguir procesandose con normalidad")
                .isPresent();
        assertThat(resumen.explotaciones().creadas()).isEqualTo(2);
        assertThat(resumen.explotaciones().filasProcesadas()).isEqualTo(3);
    }

    /**
     * Caso distinto del anterior: aqui NO hay otra Gestoria de por medio, es la MISMA gestoria con
     * una fila duplicada por error humano en el propio Excel (mismo codigo_rega dos veces). Cada
     * fila corre en su propia transaccion REQUIRES_NEW que hace COMMIT real al terminar (no solo
     * flush) -- para cuando la fila 2 arranca su propia transaccion (conexion distinta), el commit
     * de la fila 1 ya es visible bajo aislamiento read-committed, asi que findByCodigoRegaAndGestoriaId(X, gestoriaId) SI
     * encuentra la fila 1 y la fila 2 se procesa como actualizacion, no como intento de INSERT
     * duplicado. No debe haber ninguna violacion de constraint en este caso (a diferencia del test
     * cross-tenant de arriba, donde el filtro de tenant oculta la fila ya existente).
     */
    @Test
    void dosFilasDelMismoFicheroYLaMismaGestoriaConElMismoCodigoRegaSeTratanComoCreacionYActualizacion() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria duplicado interno"));
        comprometerSetup();

        MockMultipartFile archivo = construirExcel(
                new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"},
                List.<String[]>of(
                        new String[]{"ES600000000001", "Finca version 1", "20000001A", "Ganadero Duplicado"},
                        new String[]{"ES600000000001", "Finca version 2", "20000001A", "Ganadero Duplicado"}),
                new String[]{"crotal", "especie", "codigo_rega_explotacion"},
                List.of());

        ImportResumenResponse resumen = explotacionImportService.importar(archivo, gestoria);

        TestTransaction.start();

        assertThat(resumen.errores()).isEmpty();
        assertThat(resumen.explotaciones().filasProcesadas()).isEqualTo(2);
        assertThat(resumen.explotaciones().creadas()).isEqualTo(1);
        assertThat(resumen.explotaciones().actualizadas()).isEqualTo(1);

        assertThat(explotacionRepository.count()).isEqualTo(1);
        assertThat(ganaderoRepository.count()).isEqualTo(1);

        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId("ES600000000001", gestoria.getId()).orElseThrow();
        assertThat(explotacion.getNombre()).isEqualTo("Finca version 2");
    }

    // --- Decision 17 (I-pre1): identificadores de OTRA Gestoria -> error de fila neutro ---

    private static final String MOTIVO_IDENTIFICADOR_NO_DISPONIBLE =
            "No se ha podido guardar la fila: alguno de sus identificadores (código REGA, NIF o crotal) no está disponible.";

    /**
     * Otra Gestoria ya tiene un codigo_rega, un NIF y un crotal que trae este fichero. Cada una de
     * esas filas es un error con un mensaje NEUTRO (no dice nada de otra Gestoria) y las filas
     * siguientes se procesan con normalidad. Nada de la otra Gestoria se toca.
     */
    @Test
    void identificadoresDeOtraGestoriaSonErrorDeFilaNeutroYLasSiguientesSeProcesan() throws IOException {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena identificadores"));
        Ganadero ganaderoAjeno = new Ganadero();
        ganaderoAjeno.setGestoria(gestoriaAjena);
        ganaderoAjeno.setNombre("Ganadero ajeno");
        ganaderoAjeno.setNif("51000001X");
        ganaderoRepository.save(ganaderoAjeno);
        Explotacion explotacionAjena = new Explotacion();
        explotacionAjena.setGestoria(gestoriaAjena);
        explotacionAjena.setGanadero(ganaderoAjeno);
        explotacionAjena.setCodigoRega("ES510000000001");
        explotacionAjena.setNombre("Finca ajena");
        explotacionRepository.save(explotacionAjena);
        Animal animalAjeno = new Animal();
        animalAjeno.setGestoria(gestoriaAjena);
        animalAjeno.setExplotacion(explotacionAjena);
        animalAjeno.setCrotal("ES510000000099");
        animalAjeno.setCrotalUltimosDigitos("000099");
        animalRepository.save(animalAjeno);
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria propia identificadores"));
        comprometerSetup();

        MockMultipartFile archivo = construirExcel(
                CABECERA_EXPLOTACIONES,
                List.<String[]>of(
                        new String[]{"ES510000000001", "Finca con REGA ajeno", "51000002Y", "Ganadero Propio 1"},
                        new String[]{"ES510000000002", "Finca con NIF ajeno", "51000001X", "Ganadero Con NIF Ajeno"},
                        new String[]{"ES510000000003", "Finca propia", "51000003Z", "Ganadero Propio 3"}),
                CABECERA_ANIMALES,
                List.<String[]>of(
                        new String[]{"ES510000000099", "", "ES510000000003"},
                        // El mismo crotal ajeno escrito con separadores: tras normalizar (decision
                        // 22) choca igual y debe dar el mismo error neutro.
                        new String[]{"es-5100 0000.0099", "", "ES510000000003"},
                        new String[]{"ES510000000100", "", "ES510000000003"}));

        ImportResumenResponse resumen = explotacionImportService.importar(archivo, gestoria);

        TestTransaction.start();

        assertThat(resumen.errores()).extracting(ImportErrorDto::hoja, ImportErrorDto::fila).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ExplotacionImportService.HOJA_EXPLOTACIONES, 2),
                org.assertj.core.groups.Tuple.tuple(ExplotacionImportService.HOJA_EXPLOTACIONES, 3),
                org.assertj.core.groups.Tuple.tuple(ExplotacionImportService.HOJA_ANIMALES, 2),
                org.assertj.core.groups.Tuple.tuple(ExplotacionImportService.HOJA_ANIMALES, 3));
        assertThat(resumen.errores()).allSatisfy(e -> {
            assertThat(e.motivo()).isEqualTo(MOTIVO_IDENTIFICADOR_NO_DISPONIBLE);
            assertThat(e.motivo().toLowerCase()).doesNotContain("gestori").doesNotContain("duplicad");
        });
        // El Ganadero ajeno (mismo NIF) no se ha tocado ni ha ganado Explotaciones de esta Gestoria.
        Ganadero ganaderoAjenoDespues = ganaderoRepository.findByNifAndGestoriaId("51000001X", gestoriaAjena.getId())
                .orElseThrow();
        assertThat(ganaderoAjenoDespues.getNombre()).isEqualTo("Ganadero ajeno");
        assertThat(explotacionRepository.findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc(
                ganaderoAjenoDespues.getId(), gestoria.getId())).isEmpty();
        assertThat(animalRepository.count()).isEqualTo(2);
        assertThat(resumen.explotaciones().creadas()).isEqualTo(1);
        assertThat(resumen.animales().creadas()).isEqualTo(1);
        assertThat(explotacionRepository.findByCodigoRegaAndGestoriaId("ES510000000003", gestoria.getId())).isPresent();
        assertThat(animalRepository.findByCrotalAndGestoriaId("ES510000000100", gestoria.getId())).isPresent();
        // Lo de la otra Gestoria sigue intacto y en su sitio.
        Explotacion ajena = explotacionRepository.findByCodigoRegaAndGestoriaId("ES510000000001", gestoriaAjena.getId())
                .orElseThrow();
        assertThat(ajena.getNombre()).isEqualTo("Finca ajena");
        assertThat(animalRepository.findByCrotalAndGestoriaId("ES510000000099", gestoriaAjena.getId()).orElseThrow()
                .getExplotacion().getId()).isEqualTo(ajena.getId());
        assertThat(explotacionRepository.findByCodigoRegaAndGestoriaId("ES510000000001", gestoria.getId())).isEmpty();
    }

    // --- Decision 22: el crotal del Animal se normaliza con CrotalNormalizador ---

    @Test
    void elCrotalDelAnimalSeGuardaNormalizadoYReimportarloNoDuplica() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria crotal normalizado"));
        comprometerSetup();
        List<String[]> explotacion = List.<String[]>of(
                new String[]{"ES520000000001", "Finca normalizada", "52000001A", "Ganadero Normalizado"});

        ImportResumenResponse primero = explotacionImportService.importar(construirExcel(
                CABECERA_EXPLOTACIONES, explotacion, CABECERA_ANIMALES,
                List.<String[]>of(new String[]{"es 0100-0000.1234", "", "ES520000000001"})), gestoria);
        ImportResumenResponse segundo = explotacionImportService.importar(construirExcel(
                CABECERA_EXPLOTACIONES, explotacion, CABECERA_ANIMALES,
                List.<String[]>of(new String[]{"ES010000001234", "", "ES520000000001"})), gestoria);

        TestTransaction.start();

        assertThat(primero.errores()).isEmpty();
        assertThat(primero.animales().creadas()).isEqualTo(1);
        assertThat(segundo.errores()).isEmpty();
        assertThat(segundo.animales().creadas()).isZero();
        assertThat(segundo.animales().actualizadas()).isEqualTo(1);
        assertThat(animalRepository.count()).isEqualTo(1);
        Animal animal = animalRepository.findByCrotalAndGestoriaId("ES010000001234", gestoria.getId()).orElseThrow();
        assertThat(animal.getCrotalUltimosDigitos()).isEqualTo("001234");
    }

    @Test
    void unCrotalInvalidoEsErrorDeFilaConElMotivoDelNormalizadorYNoImpideLasSiguientes() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria crotal invalido"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(construirExcel(
                CABECERA_EXPLOTACIONES,
                List.<String[]>of(new String[]{"ES530000000001", "Finca crotales", "53000001A", "Ganadero Crotales"}),
                CABECERA_ANIMALES,
                List.<String[]>of(
                        new String[]{"12_34", "", "ES530000000001"},
                        new String[]{"123", "", "ES530000000001"},
                        new String[]{"ES1234567890123456789", "", "ES530000000001"},
                        new String[]{"ES530000001234", "", "ES530000000001"})), gestoria);

        TestTransaction.start();

        assertThat(resumen.errores()).extracting(ImportErrorDto::fila, ImportErrorDto::motivo).containsExactly(
                org.assertj.core.groups.Tuple.tuple(2, "Crotal no válido: solo letras y números, hasta 30 caracteres"),
                org.assertj.core.groups.Tuple.tuple(3, "Crotal demasiado corto: indica al menos los últimos 4 dígitos"),
                org.assertj.core.groups.Tuple.tuple(4, "Crotal no válido: como máximo 20 caracteres"));
        assertThat(resumen.animales().creadas()).isEqualTo(1);
        assertThat(animalRepository.count()).isEqualTo(1);
    }

    /** Un trámite con "010000001234" o "1234" resuelve contra el crotal importado con separadores. */
    @Test
    void unTramiteResuelveContraElCrotalImportadoYaNormalizado() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria crotal tramite"));
        comprometerSetup();
        explotacionImportService.importar(construirExcel(
                CABECERA_EXPLOTACIONES,
                List.<String[]>of(new String[]{"ES540000000001", "Finca tramite", "54000001A", "Ganadero Tramite"}),
                CABECERA_ANIMALES,
                List.<String[]>of(new String[]{"es 0100 0000 1234", "", "ES540000000001"})), gestoria);

        TestTransaction.start();

        Explotacion explotacion = explotacionRepository.findByCodigoRegaAndGestoriaId("ES540000000001", gestoria.getId())
                .orElseThrow();
        Contacto contacto = contactoDe(gestoria, "+34600540001", "Contacto tramite", true);
        com.ganera.core.tramite.Tramite tramite = new com.ganera.core.tramite.Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(com.ganera.core.tramite.EstadoTramite.PENDIENTE_REVISION);
        tramiteRepository.save(tramite);

        List<com.ganera.core.tramite.TramiteCrotal> filas = tramiteCrotalService.reemplazarCrotales(
                tramite, List.of("010000001234", "1234"), gestoria.getId());

        assertThat(filas).extracting(f -> f.getCrotal()).containsExactly("ES010000001234", "ES010000001234");
        assertThat(filas).extracting(f -> f.getResolucion())
                .containsOnly(com.ganera.core.tramite.ResolucionCrotal.EN_INVENTARIO);
    }

    // --- Hoja "Contactos" (opcional) ---

    private static final String[] CABECERA_EXPLOTACIONES = {"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"};
    private static final String[] CABECERA_ANIMALES = {"crotal", "especie", "codigo_rega_explotacion"};
    private static final String[] CABECERA_CONTACTOS = {"telefono", "nombre", "codigo_explotacion", "rol"};
    private static final List<String[]> DOS_EXPLOTACIONES = List.<String[]>of(
            new String[]{"ES710000000001", "Finca Contactos 1", "71000001A", "Ganadero Contactos"},
            new String[]{"ES710000000002", "Finca Contactos 2", "71000001A", "Ganadero Contactos"});
    private static final String MOTIVO_TELEFONO_GENERICO = "No se puede usar ese teléfono para un contacto.";

    @Test
    void sinHojaContactosElResultadoEsElDeSiempreYElResumenDeContactosVaACero() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin hoja contactos"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(archivoDePrueba(), gestoria);

        TestTransaction.start();
        assertThat(resumen.explotaciones()).isEqualTo(new ImportHojaResumen(2, 2, 0));
        assertThat(resumen.animales()).isEqualTo(new ImportHojaResumen(4, 2, 0));
        assertThat(resumen.errores()).hasSize(2)
                .noneSatisfy(e -> assertThat(e.hoja()).isEqualTo(ExplotacionImportService.HOJA_CONTACTOS));
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(0, 0, 0));
        assertThat(contactoRepository.count()).isZero();
    }

    @Test
    void creaElContactoNormalizandoElTelefonoYLoEnlazaConSuRol() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria contacto nuevo"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612 345 678", "Juan Titular", "ES710000000001", "titular"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.errores()).isEmpty();
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(1, 1, 0));

        Contacto contacto = contactoRepository.findByGestoriaIdAndTelefono(gestoria.getId(), "+34612345678").orElseThrow();
        assertThat(contacto.getNombre()).isEqualTo("Juan Titular");
        assertThat(contacto.isActivo()).isTrue();
        assertThat(contacto.getGestoria().getId()).isEqualTo(gestoria.getId());

        List<ContactoExplotacion> enlaces = enlacesDe(contacto.getId(), gestoria.getId());
        assertThat(enlaces).hasSize(1);
        assertThat(enlaces.get(0).getExplotacion().getCodigoRega()).isEqualTo("ES710000000001");
        assertThat(enlaces.get(0).getRol()).isEqualTo(RolContacto.TITULAR);
        assertThat(enlaces.get(0).getGestoria().getId()).isEqualTo(gestoria.getId());
    }

    @Test
    void mismoTelefonoEnDosFilasParaDosExplotacionesEsUnContactoConDosEnlaces() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria telefono repetido"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612345678", "Maria", "ES710000000001", "TITULAR"},
                new String[]{"+34 612 345 678", "Maria", "ES710000000002", "empleado"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.errores()).isEmpty();
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(2, 1, 1));
        assertThat(contactoRepository.count()).isEqualTo(1);

        Contacto contacto = contactoRepository.findByGestoriaIdAndTelefono(gestoria.getId(), "+34612345678").orElseThrow();
        List<ContactoExplotacion> enlaces = enlacesDe(contacto.getId(), gestoria.getId());
        assertThat(enlaces).extracting(e -> e.getExplotacion().getCodigoRega() + ":" + e.getRol())
                .containsExactlyInAnyOrder("ES710000000001:TITULAR", "ES710000000002:EMPLEADO");
    }

    @Test
    void reimportarActualizaNombreYRolSinDuplicar() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria reimport contactos"));
        comprometerSetup();

        explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612345678", "Pedro", "ES710000000001", "TITULAR"})), gestoria);
        ImportResumenResponse segundo = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612345678", "Pedro Corregido", "ES710000000001", "EMPLEADO"})), gestoria);

        TestTransaction.start();
        assertThat(segundo.errores()).isEmpty();
        assertThat(segundo.contactos()).isEqualTo(new ImportHojaResumen(1, 0, 1));
        assertThat(contactoRepository.count()).isEqualTo(1);
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);

        Contacto contacto = contactoRepository.findByGestoriaIdAndTelefono(gestoria.getId(), "+34612345678").orElseThrow();
        assertThat(contacto.getNombre()).isEqualTo("Pedro Corregido");
        assertThat(enlacesDe(contacto.getId(), gestoria.getId()))
                .singleElement().extracting(ContactoExplotacion::getRol).isEqualTo(RolContacto.EMPLEADO);
    }

    @Test
    void filasInvalidasSonErroresDeFilaYNoImpidenLasSiguientes() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria filas invalidas"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"12345", "Telefono malo", "ES710000000001", "TITULAR"},
                new String[]{"612000001", "Rol malo", "ES710000000001", "JEFE"},
                new String[]{"612000002", "", "ES710000000001", "TITULAR"},
                new String[]{"612000003", "x".repeat(256), "ES710000000001", "TITULAR"},
                new String[]{"612000004", "Sin explotacion", "", "TITULAR"},
                new String[]{"612000005", "Explotacion inexistente", "ES799999999999", "TITULAR"},
                new String[]{"612000006", "Valido", "ES710000000002", "EMPLEADO"})), gestoria);

        TestTransaction.start();
        List<ImportErrorDto> errores = resumen.errores();
        assertThat(errores).hasSize(6)
                .allSatisfy(e -> assertThat(e.hoja()).isEqualTo(ExplotacionImportService.HOJA_CONTACTOS));
        assertThat(errores).extracting(ImportErrorDto::fila).containsExactly(2, 3, 4, 5, 6, 7);
        assertThat(errores.get(0).motivo()).contains("Teléfono no válido");
        assertThat(errores.get(1).motivo()).contains("TITULAR");
        assertThat(errores.get(5).motivo()).contains("ES799999999999");
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(7, 1, 0));

        // Solo la fila valida ha creado algo: las invalidas no dejan contactos a medias.
        assertThat(contactoRepository.count()).isEqualTo(1);
        assertThat(contactoRepository.findByGestoriaIdAndTelefono(gestoria.getId(), "+34612000006")).isPresent();
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);
    }

    @Test
    void explotacionDeOtraGestoriaEsErrorDeFilaYNoCreaNiContactoNiEnlace() throws IOException {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena explotacion"));
        explotacionDe(gestoriaAjena, "ES720000000001");
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria propia explotacion ajena"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612345678", "Juan", "ES720000000001", "TITULAR"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.errores()).singleElement().satisfies(e -> {
            assertThat(e.hoja()).isEqualTo(ExplotacionImportService.HOJA_CONTACTOS);
            assertThat(e.fila()).isEqualTo(2);
            assertThat(e.motivo().toLowerCase()).doesNotContain("gestori");
        });
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(1, 0, 0));
        assertThat(contactoRepository.count()).isZero();
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    /**
     * Decision 11: el telefono de la fila 3 ya es de un Contacto de OTRA Gestoria. El importador
     * lo busca solo dentro de su Gestoria (no lo ve), intenta crearlo y choca con el UNIQUE global
     * -> error de fila generico (sin mencionar otra Gestoria), rollback solo de esa fila, y las
     * filas siguientes se procesan con normalidad. El Contacto ajeno queda intacto.
     */
    @Test
    void telefonoDeOtraGestoriaAMitadDeLaHojaEsErrorGenericoYLasSiguientesFilasSeProcesan() throws IOException {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena telefono"));
        Explotacion explotacionAjena = explotacionDe(gestoriaAjena, "ES730000000001");
        Contacto contactoAjeno = contactoDe(gestoriaAjena, "+34611111111", "Contacto ajeno", true);
        ContactoExplotacion enlaceAjeno = new ContactoExplotacion();
        enlaceAjeno.setGestoria(gestoriaAjena);
        enlaceAjeno.setContacto(contactoAjeno);
        enlaceAjeno.setExplotacion(explotacionAjena);
        enlaceAjeno.setRol(RolContacto.TITULAR);
        contactoExplotacionRepository.save(enlaceAjeno);
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria propia telefono ajeno"));
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612000010", "Antes", "ES710000000001", "TITULAR"},
                new String[]{"611 111 111", "Intruso", "ES710000000001", "TITULAR"},
                new String[]{"612000011", "Despues", "ES710000000002", "EMPLEADO"},
                new String[]{"612000010", "Antes", "ES710000000002", "EMPLEADO"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.errores()).singleElement().satisfies(e -> {
            assertThat(e.hoja()).isEqualTo(ExplotacionImportService.HOJA_CONTACTOS);
            assertThat(e.fila()).isEqualTo(3);
            assertThat(e.motivo()).isEqualTo(MOTIVO_TELEFONO_GENERICO);
            assertThat(e.motivo().toLowerCase()).doesNotContain("gestori");
        });
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(4, 2, 1));

        Long idPropia = gestoria.getId();
        assertThat(contactoRepository.findByGestoriaIdAndTelefono(idPropia, "+34611111111")).isEmpty();
        Contacto antes = contactoRepository.findByGestoriaIdAndTelefono(idPropia, "+34612000010").orElseThrow();
        Contacto despues = contactoRepository.findByGestoriaIdAndTelefono(idPropia, "+34612000011").orElseThrow();
        assertThat(enlacesDe(antes.getId(), idPropia)).hasSize(2);
        assertThat(enlacesDe(despues.getId(), idPropia)).hasSize(1);

        Contacto ajeno = contactoRepository.findByGestoriaIdAndTelefono(gestoriaAjena.getId(), "+34611111111").orElseThrow();
        assertThat(ajeno.getNombre()).isEqualTo("Contacto ajeno");
        assertThat(contactoExplotacionRepository.findAll())
                .filteredOn(e -> e.getContacto().getId().equals(ajeno.getId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getExplotacion().getId()).isEqualTo(explotacionAjena.getId()));
    }

    /**
     * Guarda directa de la decision 11. Ningun test de caja negra distingue findByTelefono de
     * findByGestoriaIdAndTelefono dentro de procesarContacto: el gestoriaFilter que se reactiva al
     * principio del metodo (y, por HTTP, tambien el del interceptor) oculta igualmente el Contacto
     * ajeno. Por eso se verifica la llamada en si: findByTelefono es exclusivo del webhook de 3b.
     */
    @Test
    void elImportadorNuncaUsaFindByTelefonoSinScope() throws IOException {
        Gestoria gestoriaAjena = gestoriaRepository.save(new Gestoria("Gestoria ajena spy"));
        contactoDe(gestoriaAjena, "+34611111112", "Contacto ajeno spy", true);
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria propia spy"));
        contactoDe(gestoria, "+34612000020", "Existente", true);
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612000020", "Existente", "ES710000000001", "TITULAR"},
                new String[]{"611111112", "Intruso", "ES710000000001", "TITULAR"},
                new String[]{"612000021", "Nuevo", "ES710000000002", "EMPLEADO"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(3, 1, 1));
        verify(contactoRepository, never()).findByTelefono(any());
        verify(contactoRepository, atLeastOnce()).findByGestoriaIdAndTelefono(eq(gestoria.getId()), anyString());
    }

    @Test
    void contactoInactivoDeLaMismaGestoriaEsErrorDeFilaSigueInactivoYNoGanaEnlace() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria contacto inactivo"));
        contactoDe(gestoria, "+34612345678", "Dado de baja", false);
        comprometerSetup();

        ImportResumenResponse resumen = explotacionImportService.importar(excelConContactos(DOS_EXPLOTACIONES, List.<String[]>of(
                new String[]{"612345678", "Nombre nuevo", "ES710000000001", "TITULAR"})), gestoria);

        TestTransaction.start();
        assertThat(resumen.errores()).singleElement().satisfies(e -> {
            assertThat(e.hoja()).isEqualTo(ExplotacionImportService.HOJA_CONTACTOS);
            assertThat(e.fila()).isEqualTo(2);
            assertThat(e.motivo()).isEqualTo("El contacto con ese teléfono está dado de baja");
        });
        assertThat(resumen.contactos()).isEqualTo(new ImportHojaResumen(1, 0, 0));
        Contacto contacto = contactoRepository.findByGestoriaIdAndTelefono(gestoria.getId(), "+34612345678").orElseThrow();
        assertThat(contacto.isActivo()).isFalse();
        assertThat(contacto.getNombre()).isEqualTo("Dado de baja");
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    private List<ContactoExplotacion> enlacesDe(Long contactoId, Long gestoriaId) {
        return contactoExplotacionRepository.findByContactoIdInAndGestoriaId(List.of(contactoId), gestoriaId);
    }

    private Explotacion explotacionDe(Gestoria gestoria, String codigoRega) {
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

    private Contacto contactoDe(Gestoria gestoria, String telefono, String nombre, boolean activo) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre(nombre);
        contacto.setActivo(activo);
        return contactoRepository.save(contacto);
    }

    private static MockMultipartFile excelConContactos(List<String[]> filasExplotaciones, List<String[]> filasContactos)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook.createSheet("Explotaciones"), CABECERA_EXPLOTACIONES, filasExplotaciones);
            escribirHoja(workbook.createSheet("Animales"), CABECERA_ANIMALES, List.of());
            escribirHoja(workbook.createSheet("Contactos"), CABECERA_CONTACTOS, filasContactos);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile(
                    "archivo", "prueba-contactos.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    /** Comprometido de verdad (no solo flush) -- necesario para que las transacciones REQUIRES_NEW de
     * ExplotacionImportFilaService, que corren en su propia conexion, vean estos datos de setup. */
    private static void comprometerSetup() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private MockMultipartFile archivoDePrueba() throws IOException {
        try (InputStream inputStream = new ClassPathResource("inventario-prueba.xlsx").getInputStream()) {
            return new MockMultipartFile(
                    "archivo",
                    "inventario-prueba.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream);
        }
    }

    static MockMultipartFile construirExcel(
            String[] cabeceraExplotaciones, List<String[]> filasExplotaciones,
            String[] cabeceraAnimales, List<String[]> filasAnimales) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook.createSheet("Explotaciones"), cabeceraExplotaciones, filasExplotaciones);
            escribirHoja(workbook.createSheet("Animales"), cabeceraAnimales, filasAnimales);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile(
                    "archivo", "prueba-generada.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private static void escribirHoja(Sheet hoja, String[] cabecera, List<String[]> filas) {
        Row header = hoja.createRow(0);
        for (int c = 0; c < cabecera.length; c++) {
            header.createCell(c).setCellValue(cabecera[c]);
        }
        int numeroFila = 1;
        for (String[] fila : filas) {
            Row row = hoja.createRow(numeroFila++);
            for (int c = 0; c < fila.length; c++) {
                row.createCell(c).setCellValue(fila[c]);
            }
        }
    }
}
