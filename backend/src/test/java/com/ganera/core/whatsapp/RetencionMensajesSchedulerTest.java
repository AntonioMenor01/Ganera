package com.ganera.core.whatsapp;

import com.ganera.core.shared.auditoria.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cableado del job de retencion (B1, T3; retencion POR GESTORIA desde D6 de auditoria): propiedades y sus valores por
 * defecto, activacion con ganera.retencion.activo, cron y zona, y que un fallo de la BD se registra sin datos y no se
 * propaga. La logica real de borrado/vaciado y las filas de auditoria se prueban sobre H2 en RetencionMensajesServiceTest
 * y sobre PostgreSQL embebido en RetencionPorGestoriaPostgresTest; aqui los repositorios son dobles y la transaccion y la
 * auditoria son no-op, para quedarnos solo en el cableado y la redaccion de logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class RetencionMensajesSchedulerTest {

    private static final String DATO_PERSONAL = "+34600123456 baja del 1234";

    /** PlatformTransactionManager no-op: TransactionTemplate ejecuta el callback y hace commit/rollback sin BD. */
    private static PlatformTransactionManager transaccionesNoOp() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definicion) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op
            }
        };
    }

    private static AuditoriaService auditoriaNoOp() {
        return Mockito.mock(AuditoriaService.class);
    }

    /** Repositorio de mensajes falso (retencion POR GESTORIA): una gestoria con filas; las sentencias lanzan (con datos
     * personales en el mensaje, para comprobar que no llegan al log) o devuelven filas, segun se pida. */
    private static MensajeCampoRepository repositorio(boolean fallaBorrado, boolean fallaVaciado, AtomicInteger llamadas) {
        return (MensajeCampoRepository) Proxy.newProxyInstance(
                MensajeCampoRepository.class.getClassLoader(), new Class<?>[]{MensajeCampoRepository.class},
                (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "gestoriasConMensajesSinTramiteAntesDe", "gestoriasConTextoPorVaciarAntesDe" -> {
                            return List.of(1L);
                        }
                        case "borrarSinTramiteDeGestoriaAntesDe" -> {
                            llamadas.incrementAndGet();
                            if (fallaBorrado) {
                                throw new IllegalStateException(DATO_PERSONAL);
                            }
                            return 3;
                        }
                        case "borrarSinTramiteSinGestoriaAntesDe" -> {
                            return 0;
                        }
                        case "vaciarTextoConTramiteDeGestoriaAntesDe" -> {
                            llamadas.incrementAndGet();
                            if (fallaVaciado) {
                                throw new IllegalArgumentException(DATO_PERSONAL);
                            }
                            return 2;
                        }
                        case "toString" -> {
                            return "repositorio de prueba";
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        default -> throw new UnsupportedOperationException(metodo.getName());
                    }
                });
    }

    /** Repositorio falso de respuestas (B2 reducido): su sentencia de retencion por gestoria devuelve 4 filas. */
    private static RespuestaWhatsappRepository respuestas() {
        return (RespuestaWhatsappRepository) Proxy.newProxyInstance(
                RespuestaWhatsappRepository.class.getClassLoader(), new Class<?>[]{RespuestaWhatsappRepository.class},
                (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "gestoriasConRespuestasPorVaciarAntesDe" -> List.of(1L);
                    case "vaciarTextoYTelefonoDeGestoriaAntesDe" -> 4;
                    case "toString" -> "respuestas de prueba";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private static RetencionMensajesService servicio(MensajeCampoRepository mensajes, RespuestaWhatsappRepository resp,
                                                     Clock reloj, Period sinTramite, Period conTramite) {
        return new RetencionMensajesService(mensajes, resp, auditoriaNoOp(), transaccionesNoOp(), reloj,
                sinTramite, conTramite);
    }

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                // Lo mismo que hace SpringApplication: convierte "P30D"/"P12M" a Period en @Value.
                .withInitializer(ctx -> ctx.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(MensajeCampoRepository.class, () -> repositorio(false, false, new AtomicInteger()))
                .withBean(RespuestaWhatsappRepository.class, RetencionMensajesSchedulerTest::respuestas)
                .withBean(AuditoriaService.class, RetencionMensajesSchedulerTest::auditoriaNoOp)
                // AuditoriaService lleva un @PersistenceContext EntityManager: en este contexto minimo sin JPA hay que
                // dar un EntityManagerFactory (mock) para que se resuelva; nunca se usa (estos tests no llaman a aplicar).
                .withBean(jakarta.persistence.EntityManagerFactory.class,
                        () -> Mockito.mock(jakarta.persistence.EntityManagerFactory.class))
                .withBean(PlatformTransactionManager.class, RetencionMensajesSchedulerTest::transaccionesNoOp)
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2031-03-10T09:00:00Z"), ZoneOffset.UTC))
                .withUserConfiguration(RetencionMensajesService.class, RetencionMensajesScheduler.class);
    }

    @Test
    void porDefectoElJobEstaActivoConTreintaDiasYDoceMeses() {
        contexto().run(ctx -> {
            assertThat(ctx).hasSingleBean(RetencionMensajesScheduler.class);
            RetencionMensajesService servicio = ctx.getBean(RetencionMensajesService.class);
            assertThat(servicio.plazoSinTramite()).isEqualTo(Period.ofDays(30));
            assertThat(servicio.plazoTextoConTramite()).isEqualTo(Period.ofMonths(12));
        });
    }

    @Test
    void losPlazosSeLeenDeLasPropiedades() {
        contexto().withPropertyValues(
                        "ganera.retencion.mensajes-sin-tramite=P10D",
                        "ganera.retencion.texto-con-tramite=P2M")
                .run(ctx -> {
                    RetencionMensajesService servicio = ctx.getBean(RetencionMensajesService.class);
                    assertThat(servicio.plazoSinTramite()).isEqualTo(Period.ofDays(10));
                    assertThat(servicio.plazoTextoConTramite()).isEqualTo(Period.ofMonths(2));
                });
    }

    @Test
    void conActivoAFalseNoHayJob() {
        contexto().withPropertyValues("ganera.retencion.activo=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(RetencionMensajesScheduler.class);
                    assertThat(ctx).hasSingleBean(RetencionMensajesService.class);
                });
    }

    @Test
    void elCronPorDefectoEsALasTresYMediaDeMadrid() throws NoSuchMethodException {
        Scheduled programado = RetencionMensajesScheduler.class.getDeclaredMethod("aplicarRetencion")
                .getAnnotation(Scheduled.class);

        assertThat(programado.cron()).isEqualTo("${ganera.retencion.cron:0 30 3 * * *}");
        assertThat(programado.zone()).isEqualTo("Europe/Madrid");
    }

    @Test
    void elApplicationYmlDeProduccionDocumentaLosPlazosPorDefecto() throws Exception {
        // En los tests el application.yml de src/test/resources tapa al de produccion en el classpath:
        // se lee del disco, con la ruta resuelta desde el modulo (no desde el directorio de trabajo).
        Path modulo = raizDelModulo();
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(modulo.resolve("src/main/resources/application.yml")));
        Properties produccion = yaml.getObject();

        assertThat(produccion.getProperty("ganera.retencion.activo")).isEqualTo("true");
        assertThat(produccion.getProperty("ganera.retencion.cron")).isEqualTo("0 30 3 * * *");
        assertThat(produccion.getProperty("ganera.retencion.mensajes-sin-tramite")).isEqualTo("P30D");
        assertThat(produccion.getProperty("ganera.retencion.texto-con-tramite")).isEqualTo("P12M");

        YamlPropertiesFactoryBean yamlTest = new YamlPropertiesFactoryBean();
        yamlTest.setResources(new FileSystemResource(modulo.resolve("src/test/resources/application.yml")));
        assertThat(yamlTest.getObject().getProperty("ganera.retencion.activo")).isEqualTo("false");
    }

    /** backend/: dos niveles por encima de target/test-classes, de donde se cargo esta clase. */
    private static Path raizDelModulo() throws Exception {
        Path clases = Path.of(RetencionMensajesSchedulerTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        Path modulo = clases.getParent().getParent();
        assertThat(modulo.resolve("pom.xml")).exists();
        return modulo;
    }

    @Test
    void unFalloEnUnaSentenciaSeRegistraSinDatosYLaOtraSeEjecutaIgual(CapturedOutput salida) {
        AtomicInteger llamadas = new AtomicInteger();
        RetencionMensajesService servicio = servicio(repositorio(true, false, llamadas), respuestas(),
                Clock.systemUTC(), Period.ofDays(30), Period.ofMonths(12));

        RetencionMensajesService.Resultado resultado = servicio.aplicar();

        assertThat(llamadas.get()).isEqualTo(2);
        assertThat(resultado).isEqualTo(new RetencionMensajesService.Resultado(-1, 2, 4));
        assertThat(salida.getAll()).contains("java.lang.IllegalStateException")
                .contains("borrados=-1, vaciados=2")
                .doesNotContain("600123456").doesNotContain("baja del");
    }

    @Test
    void siFallanLasDosElJobNoPropagaNiRegistraDatos(CapturedOutput salida) {
        AtomicInteger llamadas = new AtomicInteger();
        RetencionMensajesService servicio = servicio(repositorio(true, true, llamadas), respuestas(),
                Clock.systemUTC(), Period.ofDays(30), Period.ofMonths(12));
        RetencionMensajesScheduler job = new RetencionMensajesScheduler(servicio);

        job.aplicarRetencion();

        assertThat(llamadas.get()).isEqualTo(2);
        assertThat(salida.getAll()).contains("java.lang.IllegalStateException")
                .contains("java.lang.IllegalArgumentException")
                .doesNotContain("600123456").doesNotContain("baja del");
    }

    @Test
    void unaPasadaCorrectaRegistraLasFilasBorradasYVaciadas(CapturedOutput salida) {
        RetencionMensajesService servicio = servicio(
                repositorio(false, false, new AtomicInteger()), respuestas(), Clock.systemUTC(),
                Period.ofDays(30), Period.ofMonths(12));

        assertThat(servicio.aplicar()).isEqualTo(new RetencionMensajesService.Resultado(3, 2, 4));
        assertThat(salida.getAll()).contains("Retencion de mensajes: borrados=3, vaciados=2, respuestasVaciadas=4");
    }
}
