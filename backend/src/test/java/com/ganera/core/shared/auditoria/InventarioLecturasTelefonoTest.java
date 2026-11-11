package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Field;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardia estructural del hueco de auditoria de lecturas (2026-11-11). Ningun DTO de respuesta puede exponer el
 * telefono de un contacto o del ganadero sin estar DECLARADO aqui: o <b>auditado</b> (ruta + test de lectura que lo
 * exige) o <b>exento</b> (con su motivo). Escanea por classpath todos los tipos de respuesta de produccion (la clase
 * o su envolvente de nivel superior acaba en {@code Response}/{@code Resultado}) y, en cada uno, detecta cualquier
 * campo {@code String} cuyo nombre contenga {@code telefono}, {@code movil} o {@code phone} (sin distinguir
 * mayusculas). Un campo nuevo sin declarar pone el test en ROJO — asi una respuesta nueva con telefonos no puede
 * colarse sin auditar y sin una decision explicita.
 *
 * <p><b>Limitacion (asumida):</b> la guardia vigila <b>tipos</b>, no <b>rutas</b>. Un endpoint GET nuevo que reutilice
 * un DTO ya declarado NO la dispara (el DTO ya esta en la lista); para ese caso valen la decision D4 y los tests E2E
 * de lecturas sensibles ({@link LecturasSensiblesAuditadasEndToEndTest}). Tampoco cubre un campo de telefono con otro
 * nombre distinto a telefono/movil/phone.
 */
class InventarioLecturasTelefonoTest {

    private static final String PAQUETE_BASE = "com.ganera.core";
    private static final Pattern TELEFONO = Pattern.compile("telefono|movil|phone", Pattern.CASE_INSENSITIVE);

    private record Cobertura(boolean auditada, String detalle) {
        static Cobertura auditada(String ruta, String test) {
            return new Cobertura(true, ruta + " (" + test + ")");
        }

        static Cobertura exenta(String razon) {
            return new Cobertura(false, razon);
        }
    }

    /** Clave: "&lt;Clase o Envolvente$Anidada&gt;.&lt;campo&gt;" sin paquete. */
    private static final Map<String, Cobertura> DECLARADOS = new LinkedHashMap<>();

    static {
        DECLARADOS.put("ExplotacionDetalleResponse$ContactoDeExplotacion.telefono",
                Cobertura.auditada("GET /explotaciones/{id} -> LECTURA_EXPLOTACION", "LecturasSensiblesAuditadasEndToEndTest"));
        DECLARADOS.put("GanaderoDetalleResponse$ContactoDeExplotacion.telefono",
                Cobertura.auditada("GET /ganaderos/{id} -> LECTURA_GANADERO", "LecturasSensiblesAuditadasEndToEndTest"));
        DECLARADOS.put("GanaderoDetalleResponse.telefono",
                Cobertura.auditada("GET /ganaderos/{id} -> LECTURA_GANADERO", "LecturasSensiblesAuditadasEndToEndTest"));
        DECLARADOS.put("ContactoResponse.telefono",
                Cobertura.auditada("GET /contactos -> LECTURA_CONTACTOS", "LecturasSensiblesAuditadasEndToEndTest"));
        DECLARADOS.put("WhatsappTramiteResponse.telefonoEnmascarado",
                Cobertura.exenta("Va enmascarado (EnmascaradorTelefono); nunca el numero entero."));
        DECLARADOS.put("PreferenciaOvzResponse.telefonoMovimiento",
                Cobertura.exenta("Telefono de la guia: configuracion de la gestoria, no PII de un contacto/ganadero."));
    }

    @Test
    void ningunCampoTelefonoDeRespuestaSeColaSinDeclarar() throws Exception {
        Set<String> encontrados = escanearCamposTelefonoEnRespuestas();

        Set<String> sinDeclarar = new TreeSet<>(encontrados);
        sinDeclarar.removeAll(DECLARADOS.keySet());
        assertThat(sinDeclarar)
                .as("Campos String de telefono en respuestas de produccion SIN declarar en "
                        + "InventarioLecturasTelefonoTest. Declara cada uno como auditado (ruta + test de lectura) "
                        + "o exento (razon): %s", sinDeclarar)
                .isEmpty();

        Set<String> declaradosDeMas = new TreeSet<>(DECLARADOS.keySet());
        declaradosDeMas.removeAll(encontrados);
        assertThat(declaradosDeMas)
                .as("Entradas declaradas que ya no existen en el codigo (limpialas): %s", declaradosDeMas)
                .isEmpty();
    }

    private Set<String> escanearCamposTelefonoEnRespuestas() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter((metadata, factory) -> true);

        Set<String> hallados = new TreeSet<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents(PAQUETE_BASE)) {
            Class<?> clazz = Class.forName(definicion.getBeanClassName());
            if (!esRespuestaDeProduccion(clazz)) {
                continue;
            }
            for (Field campo : clazz.getDeclaredFields()) {
                if (campo.getType() == String.class && TELEFONO.matcher(campo.getName()).find()) {
                    hallados.add(claveDe(clazz, campo));
                }
            }
        }
        return hallados;
    }

    /** Solo clases de src/main (no de test) cuya clase o envolvente de nivel superior es un *Response/*Resultado. */
    private boolean esRespuestaDeProduccion(Class<?> clazz) {
        CodeSource origen = clazz.getProtectionDomain().getCodeSource();
        if (origen != null && origen.getLocation().getPath().replace('\\', '/').contains("/test-classes/")) {
            return false;
        }
        Class<?> envolvente = clazz;
        while (envolvente.getEnclosingClass() != null) {
            envolvente = envolvente.getEnclosingClass();
        }
        return esNombreDeRespuesta(clazz.getSimpleName()) || esNombreDeRespuesta(envolvente.getSimpleName());
    }

    private static boolean esNombreDeRespuesta(String nombre) {
        return nombre.endsWith("Response") || nombre.endsWith("Resultado");
    }

    private String claveDe(Class<?> clazz, Field campo) {
        String sinPaquete = clazz.getName().substring(clazz.getPackageName().length() + 1);
        return sinPaquete + "." + campo.getName();
    }
}
