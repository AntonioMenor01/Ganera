package com.ganera.core.ovz;

/**
 * Validacion pura del contenido de un justificante OVZ antes de guardarlo (T3): no vacio, no mayor de 10 MB y PDF
 * (cabecera {@code %PDF}). Sin Spring, sin IO: solo mira los bytes. No valida la estructura interna del PDF (de eso
 * se ocupa {@link BuscadorCrotalEnPdf} cuando hace falta leer el texto); aqui basta la cabecera para rechazar lo que
 * claramente no es un PDF. Nunca registra ni expone el contenido.
 */
public final class ValidadorPdf {

    /** Tamaño maximo de un justificante (D6): 10 MB. */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    /** Cabecera de todo PDF: los bytes ASCII «%PDF». */
    private static final byte[] CABECERA = {0x25, 0x50, 0x44, 0x46};

    private ValidadorPdf() {
    }

    /** true si {@code contenido} empieza por la cabecera «%PDF». */
    public static boolean esPdf(byte[] contenido) {
        if (contenido == null || contenido.length < CABECERA.length) {
            return false;
        }
        for (int i = 0; i < CABECERA.length; i++) {
            if (contenido[i] != CABECERA[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Exige que {@code contenido} sea un PDF valido para guardar; si no, lanza {@link DocumentoOvzInvalidoException}
     * (sin incluir el contenido en el mensaje). Orden: vacio -> demasiado grande -> no es PDF.
     */
    public static void exigirPdfValido(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new DocumentoOvzInvalidoException("El justificante está vacío.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new DocumentoOvzInvalidoException("El justificante supera el tamaño máximo (10 MB).");
        }
        if (!esPdf(contenido)) {
            throw new DocumentoOvzInvalidoException("El justificante no es un PDF.");
        }
    }
}
