package com.ganera.core.explotacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.apache.poi.ss.usermodel.Row;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void devuelve400ConElMensajeSiFaltaLaHojaAnimales() throws IOException {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria import controller"));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ExplotacionImportController controller = new ExplotacionImportController(explotacionImportService, gestoriaRepository);
        MockMultipartFile archivoSinHojaAnimales = construirExcelSoloConHojaExplotaciones();

        ResponseEntity<?> respuesta = controller.importar(
                new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"), archivoSinHojaAnimales);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).asString().contains("Animales");
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
