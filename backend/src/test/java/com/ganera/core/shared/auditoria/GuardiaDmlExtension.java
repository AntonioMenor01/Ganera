package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.fail;

/**
 * Extension JUnit reutilizable que aplica la {@link GuardiaDml} a cada escenario de un test (plan
 * 2026-10-21, T6). Se activa con {@code @ExtendWith(GuardiaDmlExtension.class)} en la clase de test; la
 * logica no se copia por escenario, vive en {@link GuardiaDml} y {@link InspectorDml}.
 *
 * <p>Usa {@link InvocationInterceptor} (no {@code BeforeEachCallback}) a proposito: arma {@link InspectorDml}
 * justo ANTES del cuerpo del test, DESPUES de los {@code @BeforeEach}, de modo que las escrituras de setup
 * NO cuentan — solo las del escenario. Si el cuerpo no falla por otra cosa, exige que toda escritura de
 * negocio no exenta haya ido acompañada de una fila de auditoria. Un escenario que escribe sin auditar a
 * proposito se declara con {@link SinGuardiaDml}.
 */
public class GuardiaDmlExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        vigilar(invocation, extensionContext.getRequiredTestMethod(), extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        // Cubre @ParameterizedTest / @RepeatedTest (p. ej. el parametrizado de accesos a otra gestoria).
        vigilar(invocation, extensionContext.getRequiredTestMethod(), extensionContext);
    }

    private void vigilar(Invocation<Void> invocation, Method metodo, ExtensionContext ctx) throws Throwable {
        InspectorDml.armar();
        boolean cuerpoFallo = false;
        try {
            invocation.proceed();
        } catch (Throwable t) {
            cuerpoFallo = true;
            throw t;
        } finally {
            try {
                // Solo exigimos la guardia si el cuerpo del test paso: no enmascarar su fallo con el nuestro.
                if (!cuerpoFallo) {
                    String error = GuardiaDml.verificar(InspectorDml.tablasEscritas(),
                            InspectorDml.huboInsertEnAuditoria(), exencionesDe(metodo));
                    if (error != null) {
                        fail(error);
                    }
                }
            } finally {
                InspectorDml.desarmar();
            }
        }
    }

    private static Set<String> exencionesDe(Method metodo) {
        SinGuardiaDml anotacion = metodo.getAnnotation(SinGuardiaDml.class);
        if (anotacion == null) {
            return Set.of();
        }
        // tablas() vacio = exime todas las de negocio para este escenario (solo escribe tablas sin gestoria).
        return anotacion.tablas().length == 0 ? GuardiaDml.TABLAS_NEGOCIO : Set.of(anotacion.tablas());
    }
}
