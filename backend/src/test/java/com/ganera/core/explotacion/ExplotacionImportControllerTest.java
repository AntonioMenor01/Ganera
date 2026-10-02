package com.ganera.core.explotacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({ExplotacionImportService.class, ExplotacionImportFilaService.class})
class ExplotacionImportControllerTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private ExplotacionImportService explotacionImportService;

    @AfterEach
    void limpiarDatosComprometidos() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        explotacionRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    static final String MOTIVO_XLS =
            "El fichero es un Excel antiguo (.xls). Ábrelo en Excel y guárdalo como .xlsx antes de importarlo.";
    static final String MOTIVO_NO_XLSX = "El fichero no es un Excel .xlsx válido.";

    @Test
    void devuelve400ConElMotivoSiFaltaLaHojaAnimales() throws IOException {
        ResponseEntity<?> respuesta = importarComo(construirExcelSoloConHojaExplotaciones());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse("Falta la hoja 'Animales' en el Excel"));
    }

    @Test
    void devuelve400ConElMotivoSiFaltaLaHojaExplotaciones() throws IOException {
        ResponseEntity<?> respuesta = importarComo(xlsx("sin-explotaciones.xlsx", "Animales"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse("Falta la hoja 'Explotaciones' en el Excel"));
    }

    @Test
    void unXlsAntiguoDevuelve400ConUnMotivoQueExplicaComoConvertirlo() throws IOException {
        ResponseEntity<?> respuesta = importarComo(xlsAntiguo());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(MOTIVO_XLS));
    }

    /** La deteccion es por contenido: un .xls renombrado a .xlsx (y con el Content-Type de .xlsx)
     * sigue siendo un .xls. */
    @Test
    void unXlsRenombradoAXlsxSeDetectaPorContenido() throws IOException {
        MockMultipartFile renombrado = new MockMultipartFile("archivo", "inventario.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsAntiguo().getBytes());

        ResponseEntity<?> respuesta = importarComo(renombrado);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(MOTIVO_XLS));
    }

    @Test
    void unTextoPlanoDevuelve400ConMotivoGenerico() throws IOException {
        MockMultipartFile texto = new MockMultipartFile("archivo", "notas.txt", "text/plain",
                "codigo_rega;nombre\nES000000000001;Finca\n".getBytes(StandardCharsets.UTF_8));

        assertNoEsXlsxValido(importarComo(texto));
    }

    @Test
    void unFicheroVacioDevuelve400ConMotivoGenerico() throws IOException {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "vacio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertNoEsXlsxValido(importarComo(vacio));
    }

    @Test
    void unFicheroDeBytesAleatoriosDevuelve400ConMotivoGenerico() throws IOException {
        byte[] bytes = new byte[4096];
        new Random(42).nextBytes(bytes);
        MockMultipartFile aleatorio = new MockMultipartFile("archivo", "aleatorio.xlsx",
                "application/octet-stream", bytes);

        assertNoEsXlsxValido(importarComo(aleatorio));
    }

    @Test
    void unZipQueNoEsUnXlsxDevuelve400ConMotivoGenerico() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hola.txt"));
            zip.write("no soy un Excel".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile zip = new MockMultipartFile("archivo", "inventario.xlsx", "application/zip", out.toByteArray());

        assertNoEsXlsxValido(importarComo(zip));
    }

    /** Un .xlsx real cortado por la mitad: ZIP roto. */
    @Test
    void unXlsxTruncadoDevuelve400ConMotivoGenerico() throws IOException {
        byte[] completo = xlsx("completo.xlsx", "Explotaciones", "Animales").getBytes();
        MockMultipartFile roto = new MockMultipartFile("archivo", "roto.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Arrays.copyOf(completo, completo.length / 2));

        assertNoEsXlsxValido(importarComo(roto));
    }

    @Test
    void unXlsxValidoConLasDosHojasSigueDevolviendo200() throws IOException {
        ResponseEntity<?> respuesta = importarComo(xlsx("valido.xlsx", "Explotaciones", "Animales"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isInstanceOf(ImportResumenResponse.class);
    }

    /** D6.3: solo los fallos al abrir el libro (o una hoja que falta) son 400. Un error inesperado
     * del procesado -- aunque sea un IllegalArgumentException -- no se disfraza de "fichero
     * invalido": sube sin capturar (500). */
    @Test
    void unErrorInesperadoDelProcesadoNoSeConvierteEn400() {
        ExplotacionImportService servicioQueFalla = new ExplotacionImportService(null) {
            @Override
            public ImportResumenResponse importar(MultipartFile archivo, Gestoria gestoria) {
                throw new IllegalArgumentException("fallo interno inesperado");
            }
        };
        ExplotacionImportController controller = new ExplotacionImportController(servicioQueFalla, gestoriaRepository);

        assertThatThrownBy(() -> controller.importar(
                new GaneraUserPrincipal(1L, 1L, "empleado@test.com"), construirExcelSoloConHojaExplotaciones()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fallo interno inesperado");
    }

    private ResponseEntity<?> importarComo(MockMultipartFile archivo) throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria import controller"));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ExplotacionImportController controller = new ExplotacionImportController(explotacionImportService, gestoriaRepository);
        return controller.importar(new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), archivo);
    }

    private static void assertNoEsXlsxValido(ResponseEntity<?> respuesta) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(MOTIVO_NO_XLSX));
    }

    static MockMultipartFile xlsAntiguo() throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            workbook.createSheet("Explotaciones").createRow(0).createCell(0).setCellValue("codigo_rega");
            workbook.createSheet("Animales").createRow(0).createCell(0).setCellValue("crotal");
            return escribir(workbook, "inventario.xls", "application/vnd.ms-excel");
        }
    }

    private static MockMultipartFile xlsx(String nombreFichero, String... hojas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (String hoja : hojas) {
                workbook.createSheet(hoja).createRow(0).createCell(0).setCellValue("cabecera");
            }
            return escribir(workbook, nombreFichero,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private static MockMultipartFile escribir(Workbook workbook, String nombreFichero, String contentType)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return new MockMultipartFile("archivo", nombreFichero, contentType, out.toByteArray());
    }

    private static MockMultipartFile construirExcelSoloConHojaExplotaciones() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Row header = workbook.createSheet("Explotaciones").createRow(0);
            String[] cabecera = {"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"};
            for (int c = 0; c < cabecera.length; c++) {
                header.createCell(c).setCellValue(cabecera[c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile(
                    "archivo", "sin-hoja-animales.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
