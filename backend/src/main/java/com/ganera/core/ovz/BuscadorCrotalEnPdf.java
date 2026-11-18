package com.ganera.core.ovz;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.Locale;

/**
 * Comprueba si un PDF contiene un crotal (escalon 2, §9/D5): es el metodo con el que el worker (T4) verificará que el
 * justificante descargado es el de LA baja que presentó (la tabla de Documentos de OVZ no trae el crotal). Logica
 * pura (PDFBox): extrae el texto y busca el crotal normalizando ambos lados (sin espacios, en mayusculas), para que
 * un crotal partido por saltos de linea o espacios del maquetado del PDF igualmente case.
 *
 * <p><b>Nunca imprime ni registra el contenido del PDF ni el crotal</b> (plan, §9). Ante un PDF ilegible devuelve
 * {@code false} (no se puede confirmar la presencia: el worker lo trata como «no es este»), nunca una excepcion.
 */
public final class BuscadorCrotalEnPdf {

    private BuscadorCrotalEnPdf() {
    }

    /** true si el texto del PDF contiene {@code crotal} (comparando sin espacios y en mayusculas). */
    public static boolean contiene(byte[] pdf, String crotal) {
        if (pdf == null || crotal == null || crotal.isBlank()) {
            return false;
        }
        String objetivo = normalizar(crotal);
        if (objetivo.isEmpty()) {
            return false;
        }
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            return normalizar(texto).contains(objetivo);
        } catch (IOException | RuntimeException e) {
            // PDF ilegible/corrupto: no se puede confirmar. Nunca se registra el contenido ni la causa con texto.
            return false;
        }
    }

    private static String normalizar(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
