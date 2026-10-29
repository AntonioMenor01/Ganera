package com.ganera.core.shared.auditoria;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Logica pura de la guardia DML (plan 2026-10-21, T6). Separada de {@link GuardiaDmlExtension} para poder
 * testarla sin arrancar nada y para que los conjuntos vivan en un unico sitio reutilizable (no copiados por
 * escenario).
 *
 * <p>Regla: si en un escenario se escribio en alguna {@link #TABLAS_NEGOCIO tabla de negocio} que no esta
 * {@link #TABLAS_EXENTAS exenta} y NO hubo ningun {@code insert into auditoria}, el escenario es invalido
 * (salvo que el test se declare {@code @SinGuardiaDml}).
 *
 * <p>{@code alta_pendiente} esta en AMBOS conjuntos a proposito: es una tabla observada (negocio) pero
 * exenta porque es pre-tenant (existe antes que la Gestoria, sin {@code gestoria_id}; ver CLAUDE.md y
 * progress.md). La exencion queda VIGILADA: quitar {@code alta_pendiente} de {@link #TABLAS_EXENTAS} pone
 * en rojo el escenario del checkout de la landing sin {@code client_reference_id}.
 */
public final class GuardiaDml {

    /** Tablas de negocio cuyas escrituras deben ir acompañadas de una fila de auditoria. */
    public static final Set<String> TABLAS_NEGOCIO = Set.of(
            "tramite", "tramite_crotal", "tramite_evento", "respuesta_whatsapp",
            "contacto", "contacto_explotacion", "ganadero", "explotacion", "animal",
            "gestoria", "usuario", "suscripcion", "mensaje_campo", "alta_pendiente");

    /**
     * Tablas que escriben SIN auditar a proposito (pre-tenant / sin gestoria). {@code alta_pendiente}:
     * creada por el webhook de la landing antes de que exista la Gestoria; su transicion a una Gestoria real
     * si queda auditada en el onboarding. Los mensajes sin gestoria (numero desconocido, ERROR_RECEPCION) se
     * escriben en {@code mensaje_campo} pero esos escenarios se declaran {@code @SinGuardiaDml} alli donde
     * viven, porque {@code mensaje_campo} SI se audita cuando hay gestoria.
     */
    public static final Set<String> TABLAS_EXENTAS = Set.of("alta_pendiente");

    private GuardiaDml() {
    }

    /**
     * Comprueba el escenario. Devuelve {@code null} si es valido, o un mensaje de error si una escritura de
     * negocio no exenta quedo sin auditar.
     *
     * @param tablasEscritas    tablas en las que se escribio (de {@link InspectorDml#tablasEscritas()})
     * @param huboAuditoria     si hubo al menos un {@code insert into auditoria}
     * @param exentasAdicionales exenciones extra por anotacion (vacio salvo {@code @SinGuardiaDml})
     */
    public static String verificar(Set<String> tablasEscritas, boolean huboAuditoria, Set<String> exentasAdicionales) {
        Set<String> negocioSinAuditar = tablasEscritas.stream()
                .filter(TABLAS_NEGOCIO::contains)
                .filter(t -> !TABLAS_EXENTAS.contains(t))
                .filter(t -> !exentasAdicionales.contains(t))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!negocioSinAuditar.isEmpty() && !huboAuditoria) {
            return "Guardia DML: se escribio en " + negocioSinAuditar + " sin ninguna fila de auditoria en la "
                    + "misma peticion. Toda escritura nueva de negocio lleva su fila (plan 2026-10-21, inventario "
                    + "cerrado). Si es deliberado (escritura sin gestoria), anota el test con @SinGuardiaDml(razon).";
        }
        return null;
    }
}
