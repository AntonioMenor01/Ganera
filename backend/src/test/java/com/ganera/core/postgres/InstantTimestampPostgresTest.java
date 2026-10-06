package com.ganera.core.postgres;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.tramite.EstadoExtraccion;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramitePendienteExtraccion;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * Columnas TIMESTAMP (sin zona) mapeadas a java.time.Instant contra PostgreSQL 16 real (Prompt C,
 * T1; hipotesis del plan, seccion 7, y nota n4 de la revision de B1 T3).
 *
 * <p>Que comprueba, con la zona por defecto de la JVM en UTC y en Europe/Madrid: que la columna
 * guarda la hora de pared UTC, que un Instant guardado por Hibernate se relee igual con un
 * EntityManager limpio, que la cola de extraccion (findPendientesDeExtraccion) corta exactamente en
 * el instante (tambien en la hora repetida del cambio de hora de octubre) y que las dos consultas de
 * retencion cortan estrictamente antes del limite. La zona de sesion efectiva se comprueba con
 * {@code show timezone} (en ambos casos coincide con la de la JVM).
 *
 * <p>Como se aisla la zona: cada caso cambia la zona por defecto de la JVM ANTES de arrancar un
 * contexto de Spring propio (pool de conexiones nuevo, contra una base de datos nueva), hace todo
 * con ese contexto, lo cierra y restaura la zona original en un finally. Ninguna conexion de ese
 * pool sobrevive al caso, y el resto de la suite sigue con su zona de siempre.
 *
 * <p>Observado (2026-10-06, Hibernate 6.6 + pgjdbc 42.7.4): ningun desfase en ninguna de las dos
 * zonas; la hipotesis del plan no se cumple con estas versiones. Es la guarda de regresion al
 * actualizar pgjdbc o Hibernate: si cambia como se enlaza un Instant, este test fallara en el caso
 * Madrid (comprobado forzando hibernate.type.preferred_instant_jdbc_type=TIMESTAMP: la columna pasa
 * a la hora de Madrid y falla la cola en la hora repetida). Lo que si depende de la zona de sesion
 * es el DEFAULT now() de una fila insertada a mano por SQL: ver DefaultNowPostgresTest.
 */
class InstantTimestampPostgresTest {

    /** createdAt de la Gestoria. */
    private static final Instant T_GESTORIA = Instant.parse("2026-03-01T08:00:00Z");
    /** createdAt del Tramite: invierno (Madrid = UTC+1). */
    private static final Instant T_CREADO = Instant.parse("2026-01-15T10:00:00Z");
    /** proximoIntentoExtraccion: verano (Madrid = UTC+2). */
    private static final Instant T_PROXIMO = Instant.parse("2026-07-15T10:00:00Z");
    /** createdAt de los mensajes: en Madrid ya es el dia siguiente (00:30). */
    private static final Instant T_MENSAJE = Instant.parse("2026-07-20T22:30:00Z");
    /**
     * Cambio de hora de octubre: 2026-10-25 a las 03:00 CEST se vuelve a las 02:00 CET, asi que las
     * 02:30 de Madrid existen dos veces. 00:30Z es la primera (02:30 CEST).
     */
    private static final Instant T_HORA_REPETIDA = Instant.parse("2026-10-25T00:30:00Z");

    private static final String TEXTO_ELIMINADO = "[texto eliminado por antigüedad]";

    @ParameterizedTest(name = "JVM en {0}")
    @ValueSource(strings = {"UTC", "Europe/Madrid"})
    void unInstantVaYVuelveIgualYLasComparacionesDeLaColaYLaRetencionSonExactas(String zona) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zona));
            String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
            try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarAplicacion(baseDeDatos, "validate")) {
                comprobar(zona, contexto);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private void comprobar(String zona, ConfigurableApplicationContext contexto) {
        EntityManagerFactory emf = contexto.getBean(EntityManagerFactory.class);
        JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
        TramiteRepository tramites = contexto.getBean(TramiteRepository.class);
        MensajeCampoRepository mensajes = contexto.getBean(MensajeCampoRepository.class);

        // --- Datos, guardados por Hibernate (el camino de escritura real) ---
        long[] ids = new long[4]; // gestoria, tramite, tramite de la hora repetida, contacto
        enTransaccion(emf, em -> {
            Gestoria gestoria = new Gestoria("Gestoría Zona");
            gestoria.setCreatedAt(T_GESTORIA);
            em.persist(gestoria);
            Contacto contacto = new Contacto();
            contacto.setGestoria(gestoria);
            contacto.setTelefono("+34600000001");
            contacto.setNombre("Contacto");
            em.persist(contacto);
            Tramite tramite = tramitePendiente(gestoria, contacto, T_CREADO, T_PROXIMO);
            em.persist(tramite);
            Tramite tramiteHoraRepetida = tramitePendiente(gestoria, contacto, T_CREADO, T_HORA_REPETIDA);
            em.persist(tramiteHoraRepetida);
            em.persist(mensaje("SM-sin-tramite", null, gestoria));
            em.persist(mensaje("SM-con-tramite", tramite, gestoria));
            em.flush();
            ids[0] = gestoria.getId();
            ids[1] = tramite.getId();
            ids[2] = tramiteHoraRepetida.getId();
        });
        long gestoriaId = ids[0];
        long tramiteId = ids[1];
        long tramiteHoraRepetidaId = ids[2];

        // --- Lo que ha quedado en la columna (texto tal cual, sin pasar por Java) y la zona de sesion ---
        String zonaSesion = jdbc.queryForObject("show timezone", String.class);
        Map<String, Object> crudo = jdbc.queryForMap(
                "select created_at::text as creado, proximo_intento_extraccion::text as proximo from tramite where id = ?",
                tramiteId);
        String gestoriaCruda = jdbc.queryForObject("select created_at::text from gestoria where id = ?", String.class, gestoriaId);
        List<String> mensajesCrudos = jdbc.queryForList("select created_at::text from mensaje_campo order by id", String.class);

        // --- Lectura con un EntityManager limpio (sin cache de primer nivel) ---
        Instant[] leidos = new Instant[3];
        enTransaccion(emf, em -> {
            Tramite t = em.createQuery(
                            "select t from Tramite t where t.id = :id and t.gestoria.id = :g", Tramite.class)
                    .setParameter("id", tramiteId).setParameter("g", gestoriaId).getSingleResult();
            leidos[0] = t.getCreatedAt();
            leidos[1] = t.getProximoIntentoExtraccion();
            leidos[2] = em.createQuery("select g.createdAt from Gestoria g where g.id = :g", Instant.class)
                    .setParameter("g", gestoriaId).getSingleResult();
        });

        // --- Cola de extraccion en el limite ---
        List<Long> colaUnSegundoAntes = cola(tramites, T_PROXIMO.minusSeconds(1));
        List<Long> colaJusto = cola(tramites, T_PROXIMO);
        List<Long> colaUnSegundoDespues = cola(tramites, T_PROXIMO.plusSeconds(1));
        // Hora repetida de octubre: 15 min antes (02:15 CEST) y 45 min despues (02:15 CET, misma hora de pared)
        List<Long> colaHoraRepetidaAntes = cola(tramites, T_HORA_REPETIDA.minusSeconds(15 * 60));
        List<Long> colaHoraRepetidaDespues = cola(tramites, T_HORA_REPETIDA.plusSeconds(45 * 60));

        // --- Retencion en el limite (estrictamente antes de limite) ---
        int vaciadosJusto = mensajes.vaciarTextoConTramiteRecibidosAntesDe(T_MENSAJE, TEXTO_ELIMINADO);
        int borradosJusto = mensajes.borrarSinTramiteRecibidosAntesDe(T_MENSAJE);
        int vaciadosDespues = mensajes.vaciarTextoConTramiteRecibidosAntesDe(T_MENSAJE.plusSeconds(1), TEXTO_ELIMINADO);
        int borradosDespues = mensajes.borrarSinTramiteRecibidosAntesDe(T_MENSAJE.plusSeconds(1));

        System.out.printf("""
                        [T1 TIMESTAMP] JVM=%s, zona de sesion=%s
                          gestoria.created_at  guardado=%s columna=%s leido=%s
                          tramite.created_at   guardado=%s columna=%s leido=%s
                          proximo_intento      guardado=%s columna=%s leido=%s
                          mensaje_campo.created_at guardado=%s columnas=%s
                          cola: -1s=%s, justo=%s, +1s=%s (tramite %d)
                          hora repetida: -15min=%s, +45min=%s (tramite %d)
                          retencion: limite justo -> vaciados=%d borrados=%d; limite +1s -> vaciados=%d borrados=%d
                        """,
                zona, zonaSesion,
                T_GESTORIA, gestoriaCruda, leidos[2],
                T_CREADO, crudo.get("creado"), leidos[0],
                T_PROXIMO, crudo.get("proximo"), leidos[1],
                T_MENSAJE, mensajesCrudos,
                colaUnSegundoAntes, colaJusto, colaUnSegundoDespues, tramiteId,
                colaHoraRepetidaAntes, colaHoraRepetidaDespues, tramiteHoraRepetidaId,
                vaciadosJusto, borradosJusto, vaciadosDespues, borradosDespues);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(zonaSesion).as("zona de sesion que fija pgjdbc").isEqualTo(zona);
        // Convencion de la columna: hora de pared UTC, sea cual sea la zona de la JVM/sesion (lo que
        // permite comparar a mano por SQL y, si algun dia se pasa a TIMESTAMPTZ, convertir "at time zone 'UTC'").
        soft.assertThat(gestoriaCruda).as("gestoria.created_at en la columna").isEqualTo("2026-03-01 08:00:00");
        soft.assertThat(crudo.get("creado")).as("tramite.created_at en la columna").isEqualTo("2026-01-15 10:00:00");
        soft.assertThat(crudo.get("proximo")).as("proximo_intento_extraccion en la columna").isEqualTo("2026-07-15 10:00:00");
        soft.assertThat(mensajesCrudos).as("mensaje_campo.created_at en la columna")
                .containsOnly("2026-07-20 22:30:00");
        soft.assertThat(leidos[2]).as("gestoria.createdAt leido").isEqualTo(T_GESTORIA);
        soft.assertThat(leidos[0]).as("tramite.createdAt leido").isEqualTo(T_CREADO);
        soft.assertThat(leidos[1]).as("tramite.proximoIntentoExtraccion leido").isEqualTo(T_PROXIMO);
        soft.assertThat(colaUnSegundoAntes).as("cola un segundo antes").doesNotContain(tramiteId);
        soft.assertThat(colaJusto).as("cola justo en el instante").contains(tramiteId);
        soft.assertThat(colaUnSegundoDespues).as("cola un segundo despues").contains(tramiteId);
        // "15 min antes" no discrimina (con la hora de Madrid en la columna tambien queda fuera: 02:15 <
        // 02:30); se deja como control. La que detecta el fallo es la de "45 min despues".
        soft.assertThat(colaHoraRepetidaAntes).as("hora repetida, 15 min antes").doesNotContain(tramiteHoraRepetidaId);
        soft.assertThat(colaHoraRepetidaDespues).as("hora repetida, 45 min despues").contains(tramiteHoraRepetidaId);
        soft.assertThat(vaciadosJusto).as("retencion: vaciar con limite = createdAt").isZero();
        soft.assertThat(borradosJusto).as("retencion: borrar con limite = createdAt").isZero();
        soft.assertThat(vaciadosDespues).as("retencion: vaciar con limite = createdAt + 1 s").isEqualTo(1);
        soft.assertThat(borradosDespues).as("retencion: borrar con limite = createdAt + 1 s").isEqualTo(1);
        soft.assertAll();
    }

    private static Tramite tramitePendiente(Gestoria gestoria, Contacto contacto, Instant creado, Instant proximo) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setCreatedAt(creado);
        tramite.setEstadoExtraccion(EstadoExtraccion.PENDIENTE);
        tramite.setProximoIntentoExtraccion(proximo);
        return tramite;
    }

    private static MensajeCampo mensaje(String messageSid, Tramite tramite, Gestoria gestoria) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(messageSid);
        mensaje.setTelefonoOrigen("+34600000001");
        mensaje.setCuerpo("hola");
        mensaje.setGestoria(gestoria);
        mensaje.setTramite(tramite);
        mensaje.setCreatedAt(T_MENSAJE);
        return mensaje;
    }

    private static List<Long> cola(TramiteRepository tramites, Instant ahora) {
        return tramites.findPendientesDeExtraccion(List.of(EstadoExtraccion.PENDIENTE), ahora, PageRequest.of(0, 50))
                .stream().map(TramitePendienteExtraccion::tramiteId).toList();
    }

    private static void enTransaccion(EntityManagerFactory emf, Consumer<EntityManager> trabajo) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            trabajo.accept(em);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }
}
