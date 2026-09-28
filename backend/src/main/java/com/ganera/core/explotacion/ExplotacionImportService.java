package com.ganera.core.explotacion;

import com.ganera.core.contacto.ContactoService;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.contacto.TelefonoNormalizador;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.tramite.CrotalInvalidoException;
import com.ganera.core.tramite.CrotalNormalizador;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Fallback manual para cargar Explotaciones/Animales cuando la sincronizacion automatica de
 * OVZ.net (Prompt 3a) no esta disponible o falla. No depende del paquete ovz.
 *
 * <p>Hojas, leidas por posicion de columna (fila 0 = cabecera, filas vacias ignoradas):
 * <ul>
 *   <li>"Explotaciones" (obligatoria): codigo_rega, nombre, nif_ganadero, nombre_ganadero.</li>
 *   <li>"Animales" (obligatoria): crotal, especie, codigo_rega_explotacion.</li>
 *   <li>"Contactos" (opcional, se procesa despues de las otras dos): telefono, nombre,
 *       codigo_explotacion (= codigo_rega), rol (TITULAR/EMPLEADO). Si no esta, el resumen de
 *       contactos va a 0/0/0 y el resto se comporta exactamente igual que sin ella.</li>
 * </ul>
 *
 * <p>El trabajo de base de datos de cada fila se delega a {@link ExplotacionImportFilaService},
 * que procesa cada fila en su propia transaccion (REQUIRES_NEW) -- este servicio no abre ninguna
 * transaccion propia, solo parsea el Excel y agrega los resultados/errores fila a fila.
 */
@Service
public class ExplotacionImportService {

    static final String HOJA_EXPLOTACIONES = "Explotaciones";
    static final String HOJA_ANIMALES = "Animales";
    static final String HOJA_CONTACTOS = "Contactos";

    /**
     * Ganera solo gestiona ganado bovino por ahora, pero el Excel puede traer una especie
     * incorrecta (columna libre rellenada a mano) -- se valida y se marca como error de fila
     * en vez de guardarla, ya que Animal no tiene (ni necesita) un campo especie todavia.
     */
    private static final Set<String> ESPECIES_BOVINAS_ACEPTADAS = Set.of("BOVINO", "VACUNO");

    /** contacto.nombre es VARCHAR(255): mas largo fallaria en el flush como si fuera un telefono duplicado. */
    private static final int LONGITUD_MAXIMA_NOMBRE_CONTACTO = 255;

    /** Error inesperado en una fila de Contactos: nunca se muestra el mensaje interno. */
    static final String MOTIVO_CONTACTO_GENERICO = "No se pudo importar el contacto de esta fila";

    private final ExplotacionImportFilaService filaService;

    public ExplotacionImportService(ExplotacionImportFilaService filaService) {
        this.filaService = filaService;
    }

    public ImportResumenResponse importar(MultipartFile archivo, Gestoria gestoria) throws IOException {
        Long gestoriaId = gestoria.getId();
        List<ImportErrorDto> errores = new ArrayList<>();
        try (InputStream inputStream = archivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet hojaExplotaciones = workbook.getSheet(HOJA_EXPLOTACIONES);
            if (hojaExplotaciones == null) {
                throw new IllegalArgumentException("Falta la hoja '" + HOJA_EXPLOTACIONES + "' en el Excel");
            }
            Sheet hojaAnimales = workbook.getSheet(HOJA_ANIMALES);
            if (hojaAnimales == null) {
                throw new IllegalArgumentException("Falta la hoja '" + HOJA_ANIMALES + "' en el Excel");
            }

            ImportHojaResumen resumenExplotaciones = importarExplotaciones(hojaExplotaciones, gestoriaId, errores);
            ImportHojaResumen resumenAnimales = importarAnimales(hojaAnimales, gestoriaId, errores);
            Sheet hojaContactos = workbook.getSheet(HOJA_CONTACTOS);
            ImportHojaResumen resumenContactos = hojaContactos == null
                    ? new ImportHojaResumen(0, 0, 0)
                    : importarContactos(hojaContactos, gestoriaId, errores);

            return new ImportResumenResponse(resumenExplotaciones, resumenAnimales, resumenContactos, errores);
        }
    }

    private ImportHojaResumen importarExplotaciones(Sheet hoja, Long gestoriaId, List<ImportErrorDto> errores) {
        DataFormatter formatter = new DataFormatter();
        int procesadas = 0;
        int creadas = 0;
        int actualizadas = 0;

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (esFilaVacia(row, formatter, 4)) {
                continue;
            }
            int numeroFila = row.getRowNum() + 1;
            procesadas++;
            try {
                String codigoRega = valor(row, 0, formatter);
                String nombre = valor(row, 1, formatter);
                String nif = valor(row, 2, formatter);
                String nombreGanadero = valor(row, 3, formatter);

                if (codigoRega.isBlank() || nombre.isBlank() || nif.isBlank() || nombreGanadero.isBlank()) {
                    errores.add(new ImportErrorDto(HOJA_EXPLOTACIONES, numeroFila,
                            "Faltan columnas obligatorias (codigo_rega, nombre, nif_ganadero, nombre_ganadero)"));
                    continue;
                }

                ImportFilaResultado resultado = filaService.procesarExplotacion(gestoriaId, codigoRega, nombre, nif, nombreGanadero);
                if (resultado == ImportFilaResultado.CREADA) {
                    creadas++;
                } else {
                    actualizadas++;
                }
            } catch (RuntimeException e) {
                errores.add(new ImportErrorDto(HOJA_EXPLOTACIONES, numeroFila, motivoDe(e)));
            }
        }
        return new ImportHojaResumen(procesadas, creadas, actualizadas);
    }

    private ImportHojaResumen importarAnimales(Sheet hoja, Long gestoriaId, List<ImportErrorDto> errores) {
        DataFormatter formatter = new DataFormatter();
        int procesadas = 0;
        int creadas = 0;
        int actualizadas = 0;

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (esFilaVacia(row, formatter, 3)) {
                continue;
            }
            int numeroFila = row.getRowNum() + 1;
            procesadas++;
            try {
                String crotal = valor(row, 0, formatter);
                String especie = valor(row, 1, formatter);
                String codigoRegaExplotacion = valor(row, 2, formatter);

                if (crotal.isBlank() || codigoRegaExplotacion.isBlank()) {
                    errores.add(new ImportErrorDto(HOJA_ANIMALES, numeroFila,
                            "Faltan columnas obligatorias (crotal, codigo_rega_explotacion)"));
                    continue;
                }
                if (!especie.isBlank() && !ESPECIES_BOVINAS_ACEPTADAS.contains(especie.trim().toUpperCase(Locale.ROOT))) {
                    errores.add(new ImportErrorDto(HOJA_ANIMALES, numeroFila,
                            "Especie '" + especie + "' no soportada -- Ganera solo gestiona ganado bovino"));
                    continue;
                }

                // Decision 22: misma normalizacion que los crotales de un Tramite (espacios,
                // guiones, puntos, barras, mayusculas). Invalido -> error de fila con el motivo del
                // normalizador (CrotalInvalidoException) y a la siguiente fila.
                String crotalNormalizado = normalizarCrotalDeAnimal(crotal);
                ImportFilaResultado resultado = filaService.procesarAnimal(gestoriaId, crotalNormalizado, codigoRegaExplotacion);
                if (resultado == ImportFilaResultado.CREADA) {
                    creadas++;
                } else {
                    actualizadas++;
                }
            } catch (RuntimeException e) {
                errores.add(new ImportErrorDto(HOJA_ANIMALES, numeroFila, motivoDe(e)));
            }
        }
        return new ImportHojaResumen(procesadas, creadas, actualizadas);
    }

    /**
     * Validacion sin BD primero (error de fila y a la siguiente); el trabajo de BD va en
     * procesarContacto, en su propia transaccion. Los errores de esta hoja usan motivoContactoDe,
     * no motivoDe: el texto generico de motivoDe menciona "gestorias" y aqui revelaria que el
     * telefono ya existe en otra Gestoria (decision 11).
     */
    private ImportHojaResumen importarContactos(Sheet hoja, Long gestoriaId, List<ImportErrorDto> errores) {
        DataFormatter formatter = new DataFormatter();
        int procesadas = 0;
        int creadas = 0;
        int actualizadas = 0;

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (esFilaVacia(row, formatter, 4)) {
                continue;
            }
            int numeroFila = row.getRowNum() + 1;
            procesadas++;
            try {
                Optional<String> telefono = TelefonoNormalizador.normalizar(valor(row, 0, formatter));
                String nombre = valor(row, 1, formatter);
                String codigoRega = valor(row, 2, formatter);
                Optional<RolContacto> rol = parsearRol(valor(row, 3, formatter));

                String motivoInvalida = validarContacto(telefono, nombre, codigoRega, rol);
                if (motivoInvalida != null) {
                    errores.add(new ImportErrorDto(HOJA_CONTACTOS, numeroFila, motivoInvalida));
                    continue;
                }

                ImportFilaResultado resultado = filaService.procesarContacto(
                        gestoriaId, telefono.get(), nombre, codigoRega, rol.get());
                if (resultado == ImportFilaResultado.CREADA) {
                    creadas++;
                } else {
                    actualizadas++;
                }
            } catch (RuntimeException e) {
                errores.add(new ImportErrorDto(HOJA_CONTACTOS, numeroFila, motivoContactoDe(e)));
            }
        }
        return new ImportHojaResumen(procesadas, creadas, actualizadas);
    }

    /** null si la fila es valida; si no, el motivo del error de fila. */
    private static String validarContacto(Optional<String> telefono, String nombre, String codigoRega,
                                          Optional<RolContacto> rol) {
        if (telefono.isEmpty()) {
            return "Teléfono no válido";
        }
        if (nombre.isBlank()) {
            return "Falta el nombre del contacto";
        }
        if (nombre.length() > LONGITUD_MAXIMA_NOMBRE_CONTACTO) {
            return "El nombre del contacto no puede superar los " + LONGITUD_MAXIMA_NOMBRE_CONTACTO + " caracteres";
        }
        if (codigoRega.isBlank()) {
            return "Falta el codigo de la explotacion";
        }
        if (rol.isEmpty()) {
            return "El rol debe ser TITULAR o EMPLEADO";
        }
        return null;
    }

    /** Sin distinguir mayusculas; vacio o fuera de TITULAR/EMPLEADO -> vacio. */
    private static Optional<RolContacto> parsearRol(String rol) {
        try {
            return Optional.of(RolContacto.valueOf(rol.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Unico sitio donde puede aparecer una DataIntegrityViolationException en esta hoja es el
     * UNIQUE global de telefono (el nombre ya se valida antes): el mismo mensaje generico que el
     * 409 de ContactoController, sin decir si el telefono es de esta u otra Gestoria. Solo los
     * errores de fila esperados de procesarContacto (FilaImportacionException) muestran su
     * mensaje; cualquier otra excepcion se queda en un texto generico en vez de exponer un
     * mensaje interno.
     */
    static String motivoContactoDe(RuntimeException e) {
        if (e instanceof DataIntegrityViolationException) {
            return ContactoService.MOTIVO_TELEFONO_EN_USO;
        }
        if (e instanceof FilaImportacionException && e.getMessage() != null) {
            return e.getMessage();
        }
        return MOTIVO_CONTACTO_GENERICO;
    }

    /**
     * Decision 17: una violacion de constraint en Explotaciones/Animales es, en la practica, un
     * codigo_rega, NIF o crotal que ya existe (UNIQUE global) -- normalmente en OTRA Gestoria, porque
     * los de la propia se actualizan. El mensaje es neutro a proposito: no menciona otra Gestoria
     * ni "duplicado". La unicidad global sigue permitiendo deducirlo (limitacion inherente, igual
     * que el telefono de la decision 1).
     */
    static final String MOTIVO_IDENTIFICADOR_NO_DISPONIBLE =
            "No se ha podido guardar la fila: alguno de sus identificadores (código REGA, NIF o crotal) no está disponible.";

    /** animal.crotal es VARCHAR(20); el normalizador admite hasta 30 (tramite_crotal). */
    private static final int LONGITUD_MAXIMA_CROTAL_ANIMAL = 20;
    static final String MOTIVO_CROTAL_ANIMAL_DEMASIADO_LARGO =
            "Crotal no válido: como máximo " + LONGITUD_MAXIMA_CROTAL_ANIMAL + " caracteres";

    /** @throws CrotalInvalidoException con el motivo del normalizador (o de longitud). */
    private static String normalizarCrotalDeAnimal(String crotal) {
        String normalizado = CrotalNormalizador.normalizar(crotal).valor();
        if (normalizado.length() > LONGITUD_MAXIMA_CROTAL_ANIMAL) {
            throw new CrotalInvalidoException(MOTIVO_CROTAL_ANIMAL_DEMASIADO_LARGO);
        }
        return normalizado;
    }

    private static String motivoDe(RuntimeException e) {
        if (e instanceof DataIntegrityViolationException) {
            return MOTIVO_IDENTIFICADOR_NO_DISPONIBLE;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static String valor(Row row, int columnaIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnaIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static boolean esFilaVacia(Row row, DataFormatter formatter, int numeroColumnas) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < numeroColumnas; i++) {
            if (!valor(row, i, formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
