package com.ganera.core.ovz;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Genera PDFs de verdad (PDFBox) en los tests, con el texto que se le pida. Asi los tests del validador, del buscador
 * de crotal y del guardado trabajan sobre PDFs inventados en el propio test, nunca sobre un fichero del repo.
 */
final class PdfDePrueba {

    private PdfDePrueba() {
    }

    /** Un PDF de una pagina con cada cadena en su propia linea. */
    static byte[] conLineas(String... lineas) {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.setLeading(16);
                contenido.newLineAtOffset(72, 720);
                for (String linea : lineas) {
                    contenido.showText(linea);
                    contenido.newLine();
                }
                contenido.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
