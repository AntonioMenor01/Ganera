package com.ganera.core.shared.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara que un escenario de un test con {@link GuardiaDmlExtension} escribe en una tabla de negocio SIN
 * dejar fila de auditoria a proposito (p. ej. un mensaje de un numero desconocido o un ERROR_RECEPCION, que
 * no tienen gestoria). La razon queda escrita para que la exencion sea una decision explicita, no un olvido.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SinGuardiaDml {

    /** Por que este escenario escribe sin auditar (queda documentado en el codigo del test). */
    String value();

    /**
     * Tablas de negocio que este escenario puede escribir sin auditar. Vacio = todas (para casos donde solo
     * se escriben tablas sin gestoria); normalmente se dejan explicitas, p. ej. {@code "mensaje_campo"}.
     */
    String[] tablas() default {};
}
