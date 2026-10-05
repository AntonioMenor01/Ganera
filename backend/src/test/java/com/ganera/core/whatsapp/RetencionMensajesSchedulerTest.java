package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cableado del job de retencion (B1, T3): propiedades y sus valores por defecto, activacion con
 * ganera.retencion.activo, cron y zona, y que un fallo de la BD se registra sin datos y no se
 * propaga. La logica de borrado/vaciado se prueba sobre H2 en RetencionMensajesServiceTest.
 */
@ExtendWith(OutputCaptureExtension.class)
class RetencionMensajesSchedulerTest {

    private static final String DATO_PERSONAL = "+34600123456 baja del 1234";

    /** Repositorio falso: las dos sentencias de retencion lanzan (con datos personales en el
     * mensaje, para comprobar que no llegan al log) o devuelven filas, segun se pida. */
    private static MensajeCampoRepository repositorio(boolean fallaBorrado, boolean fallaVaciado, AtomicInteger llamadas) {
        return (MensajeCampoRepository) Proxy.newProxyInstance(
                MensajeCampoRepository.class.getClassLoader(), new Class<?>[]{MensajeCampoRepository.class},
                (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "borrarSinTramiteRecibidosAntesDe" -> {
                            llamadas.incrementAndGet();
                            if (fallaBorrado) {
                                throw new IllegalStateException(DATO_PERSONAL);
                            }
                            return 3;
                        }
                        case "vaciarTextoConTramiteRecibidosAntesDe" -> {
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

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                // Lo mismo que hace SpringApplication: convierte "P30D"/"P12M" a Period en @Value.
                .withInitializer(ctx -> ctx.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(MensajeCampoRepository.class, () -> repositorio(false, false, new AtomicInteger()))
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
        RetencionMensajesService servicio = new RetencionMensajesService(repositorio(true, false, llamadas),
                Clock.systemUTC(), Period.ofDays(30), Period.ofMonths(12));

        RetencionMensajesService.Resultado resultado = servicio.aplicar();

        assertThat(llamadas.get()).isEqualTo(2);
        assertThat(resultado).isEqualTo(new RetencionMensajesService.Resultado(-1, 2));
        assertThat(salida.getAll()).contains("java.lang.IllegalStateException")
                .contains("borrados=-1, vaciados=2")
                .doesNotContain("600123456").doesNotContain("baja del");
    }

    @Test
    void siFallanLasDosElJobNoPropagaNiRegistraDatos(CapturedOutput salida) {
        AtomicInteger llamadas = new AtomicInteger();
        RetencionMensajesService servicio = new RetencionMensajesService(repositorio(true, true, llamadas),
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
        RetencionMensajesService servicio = new RetencionMensajesService(
                repositorio(false, false, new AtomicInteger()), Clock.systemUTC(), Period.ofDays(30), Period.ofMonths(12));

        assertThat(servicio.aplicar()).isEqualTo(new RetencionMensajesService.Resultado(3, 2));
        assertThat(salida.getAll()).contains("Retencion de mensajes: borrados=3, vaciados=2");
    }
}
