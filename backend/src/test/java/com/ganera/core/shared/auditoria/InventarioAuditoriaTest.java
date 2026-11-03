package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Inventario cerrado de escrituras (plan 2026-10-21, T6). Saca del contexto TODAS las rutas de escritura
 * ({@link RequestMappingHandlerMapping}), TODOS los metodos {@link Scheduled} y TODOS los metodos
 * {@link Modifying} de los repositorios, y los compara con un inventario declarado aqui: cada entrada es
 * <b>auditada</b> (con la clase de test que lo exige) o <b>exenta</b> (con su razon). Una ruta, planificador
 * o {@code @Modifying} nuevo que no este declarado pone el test en ROJO — asi una escritura nueva no puede
 * colarse sin fila de auditoria y sin una decision explicita.
 *
 * <p>El descubrimiento de {@code @Scheduled} y {@code @Modifying} se hace por escaneo de classpath (no por
 * beans), para ver tambien los planificadores desactivados por {@code @ConditionalOnProperty} en tests
 * ({@code ExtraccionTramiteScheduler}, {@code RetencionMensajesScheduler}).
 *
 * <p>Por reflexion comprueba ademas que cada clase de test referenciada existe y tiene algun {@code @Test}
 * (no se puede renombrar/borrar la prueba que sostiene una entrada sin que el inventario se entere).
 */
@SpringBootTest
class InventarioAuditoriaTest {

    private static final Set<RequestMethod> ESCRITURA =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private static final String PKG = "com.ganera.core.";

    /** Una entrada del inventario: auditada (clase de test) o exenta (razon). */
    private record Cobertura(boolean auditada, String claseTest, String razon) {
        static Cobertura auditada(String clase) {
            return new Cobertura(true, PKG + clase, null);
        }

        static Cobertura exenta(String razon) {
            return new Cobertura(false, null, razon);
        }
    }

    // ==================== Inventario declarado ====================

    private static final Map<String, Cobertura> RUTAS = new LinkedHashMap<>();
    private static final Map<String, Cobertura> PLANIFICADORES = new LinkedHashMap<>();
    private static final Map<String, Cobertura> MODIFYING = new LinkedHashMap<>();

    static {
        // --- Rutas de escritura ---
        RUTAS.put("POST /auth/login", Cobertura.auditada("shared.auditoria.AuditoriaLoginEndToEndTest"));
        RUTAS.put("POST /contactos", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("PUT /contactos/{id}", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("DELETE /contactos/{id}", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /contactos/{id}/reactivar", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /contactos/{id}/explotaciones", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("DELETE /contactos/{id}/explotaciones/{explotacionId}", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("PATCH /tramites/{id}", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/aprobar", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/rechazar", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/marcar-hecho-ovz", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/desmarcar-hecho-ovz", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/descartar", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/deshacer-descarte", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /tramites/{id}/respuestas-whatsapp", Cobertura.auditada("tramite.RespuestaWhatsappEndToEndTest"));
        RUTAS.put("POST /explotaciones/importar", Cobertura.auditada("shared.auditoria.AuditoriaUsuarioEndToEndTest"));
        RUTAS.put("POST /gestorias/registro", Cobertura.auditada("registro.RegistroGestoriaEndToEndTest"));
        RUTAS.put("POST /internal/onboarding/gestoria", Cobertura.auditada("onboarding.OnboardingControllerTest"));
        RUTAS.put("POST /webhooks/stripe", Cobertura.auditada("facturacion.StripeWebhookEndToEndTest"));
        RUTAS.put("POST /webhooks/twilio/whatsapp", Cobertura.auditada("whatsapp.WhatsAppRecepcionEndToEndTest"));
        RUTAS.put("POST /webhooks/twilio/whatsapp/estado", Cobertura.auditada("whatsapp.TwilioEstadoWebhookEndToEndTest"));

        // --- Planificadores ---
        PLANIFICADORES.put("SuscripcionSyncScheduler.reconciliarCantidadesConStripe",
                Cobertura.auditada("facturacion.SuscripcionSyncSchedulerAuditoriaTest"));
        PLANIFICADORES.put("ExtraccionTramiteScheduler.procesarPendientes",
                Cobertura.auditada("tramite.ExtraccionTramiteServiceTest"));
        PLANIFICADORES.put("RetencionMensajesScheduler.aplicarRetencion",
                Cobertura.auditada("whatsapp.RetencionMensajesServiceTest"));

        // --- @Modifying ---
        MODIFYING.put("MensajeCampoRepository.borrarSinTramiteDeGestoriaAntesDe",
                Cobertura.auditada("whatsapp.RetencionMensajesServiceTest"));
        MODIFYING.put("MensajeCampoRepository.vaciarTextoConTramiteDeGestoriaAntesDe",
                Cobertura.auditada("whatsapp.RetencionMensajesServiceTest"));
        MODIFYING.put("RespuestaWhatsappRepository.vaciarTextoYTelefonoDeGestoriaAntesDe",
                Cobertura.auditada("whatsapp.RetencionMensajesServiceTest"));
        MODIFYING.put("RespuestaWhatsappRepository.actualizarEstadoPorSid",
                Cobertura.auditada("whatsapp.TwilioEstadoWebhookEndToEndTest"));
        MODIFYING.put("TramiteRepository.dejarDeReintentarExtraccion",
                Cobertura.auditada("tramite.ExtraccionTramiteServiceTest"));
        MODIFYING.put("TramiteRepository.registrarIntentoFallidoSinCambiarVersion",
                Cobertura.auditada("tramite.ExtraccionTramiteServiceTest"));
        MODIFYING.put("MensajeCampoRepository.borrarSinTramiteSinGestoriaAntesDe",
                Cobertura.exenta("Mensajes sin gestoria (numero desconocido, ERROR_RECEPCION): sin tenant, fuera de "
                        + "auditoria por decision (plan D6)."));
        MODIFYING.put("MensajeCampoRepository.borrarSinTramiteRecibidosAntesDe",
                Cobertura.exenta("Variante global pre-D6, sin llamadas en produccion; solo la usa "
                        + "InstantTimestampPostgresTest para el round-trip de Instant."));
        MODIFYING.put("MensajeCampoRepository.vaciarTextoConTramiteRecibidosAntesDe",
                Cobertura.exenta("Variante global pre-D6, sin llamadas en produccion; solo la usa "
                        + "InstantTimestampPostgresTest."));
        MODIFYING.put("RespuestaWhatsappRepository.vaciarTextoYTelefonoEnviadasAntesDe",
                Cobertura.exenta("Variante global pre-D6, sin llamadas (ni en produccion ni en tests)."));
    }

    @Autowired
    private RequestMappingHandlerMapping mapping;
    @Autowired
    private ApplicationContext ctx;

    @Test
    void todaRutaDeEscrituraEstaDeclaradaEnElInventario() {
        comprobarCobertura(descubrirRutas(), RUTAS, "ruta de escritura");
    }

    @Test
    void todoPlanificadorEstaDeclaradoEnElInventario() {
        comprobarCobertura(descubrirPlanificadores(), PLANIFICADORES, "@Scheduled");
    }

    @Test
    void todoMetodoModifyingEstaDeclaradoEnElInventario() {
        comprobarCobertura(descubrirModifying(), MODIFYING, "@Modifying");
    }

    @Test
    void ningunaEntradaDeclaradaSobraRespectoAlCodigoReal() {
        // La direccion inversa: una entrada declarada cuya escritura ya no existe (ruta borrada, @Modifying
        // renombrado) tambien debe salir, para que el inventario no acumule entradas muertas.
        sobrantes(RUTAS, descubrirRutas(), "ruta");
        sobrantes(PLANIFICADORES, descubrirPlanificadores(), "@Scheduled");
        sobrantes(MODIFYING, descubrirModifying(), "@Modifying");
    }

    @Test
    void cadaClaseDeTestReferenciadaExisteYTieneTests() {
        Set<String> clases = new TreeSet<>();
        for (Map<String, Cobertura> inventario : java.util.List.of(RUTAS, PLANIFICADORES, MODIFYING)) {
            inventario.values().stream().filter(Cobertura::auditada).map(Cobertura::claseTest).forEach(clases::add);
        }
        for (String clase : clases) {
            Class<?> c;
            try {
                c = Class.forName(clase);
            } catch (ClassNotFoundException e) {
                fail("El inventario referencia una clase de test que no existe: " + clase);
                return;
            }
            boolean tieneTest = false;
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Test.class)) {
                    tieneTest = true;
                    break;
                }
            }
            assertThat(tieneTest).as("La clase de test %s no tiene ningun @Test", clase).isTrue();
        }
    }

    // ==================== Descubrimiento ====================

    private Set<String> descubrirRutas() {
        Set<String> rutas = new TreeSet<>();
        mapping.getHandlerMethods().forEach((info, metodo) -> {
            for (RequestMethod rm : info.getMethodsCondition().getMethods()) {
                if (ESCRITURA.contains(rm)) {
                    for (String patron : patrones(info)) {
                        rutas.add(rm + " " + patron);
                    }
                }
            }
        });
        return rutas;
    }

    private Set<String> descubrirPlanificadores() {
        Set<String> planificadores = new TreeSet<>();
        for (Class<?> c : escanear(Object.class)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Scheduled.class)) {
                    planificadores.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        // Tambien los que ya esten instanciados como beans (por si alguno no lo pilla el escaneo).
        for (String nombre : ctx.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = ctx.getBean(nombre);
            } catch (Exception e) {
                continue;
            }
            Class<?> objetivo = AopUtils.getTargetClass(bean);
            if (objetivo.getName().startsWith(PKG)) {
                for (Method m : objetivo.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Scheduled.class)) {
                        planificadores.add(objetivo.getSimpleName() + "." + m.getName());
                    }
                }
            }
        }
        return planificadores;
    }

    private Set<String> descubrirModifying() {
        Set<String> modifying = new TreeSet<>();
        for (Class<?> repo : escanear(Repository.class)) {
            for (Method m : repo.getMethods()) {
                if (m.isAnnotationPresent(Modifying.class)) {
                    modifying.add(repo.getSimpleName() + "." + m.getName());
                }
            }
        }
        return modifying;
    }

    /** Escanea com.ganera.core por clases/interfaces asignables a {@code tipo} (incluye interfaces). */
    private static Set<Class<?>> escanear(Class<?> tipo) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition bd) {
                        return bd.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(tipo));
        Set<Class<?>> clases = new java.util.HashSet<>();
        for (var bd : scanner.findCandidateComponents("com.ganera.core")) {
            try {
                clases.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("No se pudo cargar " + bd.getBeanClassName(), e);
            }
        }
        return clases;
    }

    private static Set<String> patrones(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition().getPatterns();
    }

    // ==================== Comprobaciones ====================

    private static void comprobarCobertura(Set<String> descubiertas, Map<String, Cobertura> inventario, String que) {
        Set<String> sinDeclarar = new TreeSet<>();
        for (String item : descubiertas) {
            if (!inventario.containsKey(item)) {
                sinDeclarar.add(item);
            }
        }
        if (!sinDeclarar.isEmpty()) {
            fail("Hay " + que + " sin declarar en el inventario de auditoria (plan 2026-10-21, T6). "
                    + "Declara cada una como auditada (con su test) o exenta (con su razon): " + sinDeclarar);
        }
    }

    private static void sobrantes(Map<String, Cobertura> inventario, Set<String> descubiertas, String que) {
        Set<String> muertas = new TreeSet<>();
        for (String declarada : inventario.keySet()) {
            if (!descubiertas.contains(declarada)) {
                muertas.add(declarada);
            }
        }
        if (!muertas.isEmpty()) {
            fail("El inventario declara " + que + " que ya no existe(n) en el codigo; quitalas: " + muertas);
        }
    }
}
