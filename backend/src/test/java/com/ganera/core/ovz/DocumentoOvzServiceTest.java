package com.ganera.core.ovz;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guardado del justificante OVZ (T3): persiste metadatos + binario EN POSTGRES y deja una fila
 * {@code OVZ_DOCUMENTO_GUARDADO} SIN el contenido (solo el tamaño). Un contenido que no es PDF se rechaza antes de
 * escribir nada (mutacion: quitar la validacion debe poner en rojo {@code guardarUnNoPdfNoEscribeNada}).
 */
@SpringBootTest
@ExtendWith(com.ganera.core.shared.auditoria.GuardiaDmlExtension.class)
class DocumentoOvzServiceTest {

    @Autowired private DocumentoOvzService documentoOvzService;
    @Autowired private DocumentoOvzRepository documentoOvzRepository;
    @Autowired private DocumentoOvzContenidoRepository contenidoRepository;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Gestoria gestoria;
    private Ganadero ganadero;
    private Explotacion explotacion;
    private Tramite tramite;

    @BeforeEach
    void preparar() {
        gestoria = gestoriaRepository.save(new Gestoria("Documentos OVZ"));
        ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero documentos");
        ganaderoRepository.save(ganadero);
        explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES340000000001");
        explotacion.setNombre("Finca documentos");
        explotacionRepository.save(explotacion);
        tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.APROBADO);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(tramite);
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from documento_ovz_contenido");
        jdbcTemplate.update("delete from documento_ovz");
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void guardarPersisteMetadatosYContenidoYAuditaSinContenido() {
        byte[] pdf = PdfDePrueba.conLineas("Justificante de baja", "Crotal: ES340000009999");

        DocumentoOvz guardado = documentoOvzService.guardar(
                tramite, ganadero, explotacion, TipoDocumentoOvz.BAJA_ANIMAL, "Baja Animal.pdf", pdf, Actor.sistema());

        DocumentoOvz doc = documentoOvzRepository.findByIdAndGestoriaId(guardado.getId(), gestoria.getId()).orElseThrow();
        assertThat(doc.getTipo()).isEqualTo(TipoDocumentoOvz.BAJA_ANIMAL);
        assertThat(doc.getNombre()).isEqualTo("Baja Animal.pdf");
        assertThat(doc.getTamano()).isEqualTo(pdf.length);
        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getSha256()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(doc.getGanadero().getId()).isEqualTo(ganadero.getId());
        assertThat(doc.getTramite().getId()).isEqualTo(tramite.getId());
        assertThat(doc.getExplotacion().getId()).isEqualTo(explotacion.getId());

        // El binario se guarda intacto en documento_ovz_contenido (1:1).
        assertThat(contenidoRepository.findById(doc.getId()).orElseThrow().getContenido()).isEqualTo(pdf);

        // Una fila OVZ_DOCUMENTO_GUARDADO con el id del documento, el tamaño en 'despues' y NUNCA el contenido.
        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "select accion, entidad_tipo, entidad_id, antes, despues from auditoria where gestoria_id = ?",
                gestoria.getId());
        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("accion")).isEqualTo("OVZ_DOCUMENTO_GUARDADO");
        assertThat(fila.get("entidad_tipo")).isEqualTo("DOCUMENTO_OVZ");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(doc.getId());
        assertThat(fila.get("antes")).isNull();
        String despues = (String) fila.get("despues");
        assertThat(despues).contains("tamano").contains(String.valueOf(pdf.length));
        // Jamas aparece el contenido ni el crotal en la auditoria.
        assertThat(despues).doesNotContain("ES340000009999");
    }

    @Test
    void guardarUnNoPdfNoEscribeNada() {
        long documentosAntes = documentoOvzRepository.count();

        assertThatThrownBy(() -> documentoOvzService.guardar(tramite, ganadero, explotacion,
                TipoDocumentoOvz.BAJA_ANIMAL, "falso.pdf", "esto no es un pdf".getBytes(StandardCharsets.UTF_8),
                Actor.sistema()))
                .isInstanceOf(DocumentoOvzInvalidoException.class);

        // No se creo ningun documento ni fila de auditoria.
        assertThat(documentoOvzRepository.count()).isEqualTo(documentosAntes);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auditoria where gestoria_id = ?", Integer.class, gestoria.getId())).isZero();
    }
}
