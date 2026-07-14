package com.ganera.core.explotacion;

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
import org.springframework.test.context.transaction.TestTransaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
 * que el @AfterEach limpia las 4 tablas explicitamente para no contaminar el resto de la suite
 * (todas las clases de test comparten la misma base H2 en memoria durante toda la ejecucion).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({ExplotacionImportService.class, ExplotacionImportFilaService.class})
class ExplotacionImportServiceTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionImportService explotacionImportService;

    @AfterEach
    void limpiarDatosComprometidosPorLasTransaccionesRequiresNew() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
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

        Explotacion explotacion = explotacionRepository.findByCodigoRega("ES120000000001").orElseThrow();
        assertThat(explotacion.getGanadero().getNif()).isEqualTo("12345678A");
        assertThat(explotacion.getGanadero().getNombre()).isEqualTo("Juan Perez Gonzalez");

        Animal animal = animalRepository.findByCrotal("ES123456789012").orElseThrow();
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
                List.of(
                        new String[]{"ES500000000010", "Finca antes", "10000001A", "Ganadero Antes"},
                        new String[]{"ES500000000001", "Finca colisionando", "10000002B", "Ganadero Colision"},
                        new String[]{"ES500000000011", "Finca despues", "10000003C", "Ganadero Despues"}),
                new String[]{"crotal", "especie", "codigo_rega_explotacion"},
                List.of());

        ImportResumenResponse resumen = explotacionImportService.importar(archivo, gestoria);

        TestTransaction.start();

        assertThat(resumen.errores()).hasSize(1);
        assertThat(resumen.errores().get(0).fila()).isEqualTo(3);

        assertThat(explotacionRepository.findByCodigoRega("ES500000000010"))
                .as("la fila anterior a la colision debe haberse creado")
                .isPresent();
        assertThat(explotacionRepository.findByCodigoRega("ES500000000011"))
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
     * de la fila 1 ya es visible bajo aislamiento read-committed, asi que findByCodigoRega(X) SI
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
                List.of(
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

        Explotacion explotacion = explotacionRepository.findByCodigoRega("ES600000000001").orElseThrow();
        assertThat(explotacion.getNombre()).isEqualTo("Finca version 2");
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
