package com.ganera.core.demo;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.ovz.PreferenciaOvz;
import com.ganera.core.ovz.PreferenciaOvzRepository;
import com.ganera.core.tramite.EstadoExtraccion;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.MotivoRechazo;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.ResolucionCrotal;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteCrotal;
import com.ganera.core.tramite.TramiteCrotalRepository;
import com.ganera.core.tramite.TramiteEvento;
import com.ganera.core.tramite.TramiteEventoRepository;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.tramite.TipoEventoTramite;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import com.ganera.core.whatsapp.ResultadoMensaje;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
/**
 * Datos inventados realistas (Extremadura) para ver la app llena: ganaderos, explotaciones,
 * animales, contactos y trámites en todos los estados/tipos útiles del piloto. Idempotente por
 * email del usuario demo. Nunca se activa en Clever (falta {@code ganera.demo.sembrar=true}).
 */
@Service
public class DemoSemillaService {

    public static final String EMAIL_DEMO = "demo@ganera.local";
    public static final String PASSWORD_DEMO = "demo123456";
    public static final String NOMBRE_GESTORIA = "Gestoría Demo Extremadura";

    private final UsuarioRepository usuarioRepository;
    private final GestoriaRepository gestoriaRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PreferenciaOvzRepository preferenciaOvzRepository;
    private final GanaderoRepository ganaderoRepository;
    private final ExplotacionRepository explotacionRepository;
    private final AnimalRepository animalRepository;
    private final ContactoRepository contactoRepository;
    private final ContactoExplotacionRepository contactoExplotacionRepository;
    private final TramiteRepository tramiteRepository;
    private final TramiteCrotalRepository tramiteCrotalRepository;
    private final TramiteEventoRepository tramiteEventoRepository;
    private final MensajeCampoRepository mensajeCampoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoSemillaService(UsuarioRepository usuarioRepository,
                              GestoriaRepository gestoriaRepository,
                              SuscripcionRepository suscripcionRepository,
                              PreferenciaOvzRepository preferenciaOvzRepository,
                              GanaderoRepository ganaderoRepository,
                              ExplotacionRepository explotacionRepository,
                              AnimalRepository animalRepository,
                              ContactoRepository contactoRepository,
                              ContactoExplotacionRepository contactoExplotacionRepository,
                              TramiteRepository tramiteRepository,
                              TramiteCrotalRepository tramiteCrotalRepository,
                              TramiteEventoRepository tramiteEventoRepository,
                              MensajeCampoRepository mensajeCampoRepository,
                              PasswordEncoder passwordEncoder,
                              Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.preferenciaOvzRepository = preferenciaOvzRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
        this.contactoRepository = contactoRepository;
        this.contactoExplotacionRepository = contactoExplotacionRepository;
        this.tramiteRepository = tramiteRepository;
        this.tramiteCrotalRepository = tramiteCrotalRepository;
        this.tramiteEventoRepository = tramiteEventoRepository;
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record Resultado(boolean creada, Long gestoriaId) {
    }

    @Transactional
    public Resultado sembrarSiHaceFalta() {
        if (usuarioRepository.findByEmail(EMAIL_DEMO).isPresent()) {
            return new Resultado(false, null);
        }

        Gestoria gestoria = gestoriaRepository.save(new Gestoria(NOMBRE_GESTORIA));

        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(EMAIL_DEMO);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD_DEMO));
        usuario.setNombre("Ana Demo");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setExplotacionesContratadas(10);
        suscripcionRepository.save(suscripcion);

        PreferenciaOvz pref = new PreferenciaOvz();
        pref.setGestoria(gestoria);
        pref.setOficinaDi("MERIDA");
        pref.setOficinaMer("MERIDA");
        pref.setEmisionDi("Prefiero que mis DI's sean emitidos en la OVZ");
        pref.setTipoIdentificador("Crotal electrónico");
        pref.setTelefonoMovimiento("+34924111222");
        pref.setMedioTransporte("Camión");
        pref.setTipoResponsable("Titular");
        preferenciaOvzRepository.save(pref);

        Ganadero g1 = ganadero(gestoria, "Hermanos Pérez Robledo SL", "B06999881");
        Ganadero g2 = ganadero(gestoria, "María Luz Sánchez", "08999888X");
        Ganadero g3 = ganadero(gestoria, "Explotaciones de la Vera SA", "A10999882");
        Ganadero g4 = ganadero(gestoria, "Antonio Gallardo Ruiz", "12345678Z");

        Explotacion e1 = explotacion(gestoria, g1, "ES060000900001", "Finca Robledo Alto");
        Explotacion e2 = explotacion(gestoria, g1, "ES060000900002", "Dehesa Los Pinos");
        Explotacion e3 = explotacion(gestoria, g2, "ES100000900003", "Cortijo La Luz");
        Explotacion e4 = explotacion(gestoria, g3, "ES100000900004", "Finca La Vera Norte");
        Explotacion e5 = explotacion(gestoria, g4, "ES060000900005", "Pozo Amargo");

        Animal a1274 = animal(gestoria, e1, "ES060000001274");
        Animal a1275 = animal(gestoria, e1, "ES060000001275");
        animal(gestoria, e1, "ES060000003301");
        animal(gestoria, e1, "ES060000003302");
        animal(gestoria, e1, "ES060000004401");
        animal(gestoria, e1, "ES060000004402");
        animal(gestoria, e2, "ES060000009901");
        animal(gestoria, e2, "ES060000089901"); // mismo sufijo 9901 → AMBIGUO en trámites
        animal(gestoria, e2, "ES060000005501");
        animal(gestoria, e3, "ES100000001101");
        animal(gestoria, e3, "ES100000001102");
        animal(gestoria, e3, "ES100000001103");
        animal(gestoria, e4, "ES100000007701");
        animal(gestoria, e4, "ES100000007702");
        animal(gestoria, e5, "ES060000008801");
        animal(gestoria, e5, "ES060000008802");
        animal(gestoria, e5, "ES060000008803");

        Contacto cTitular = contacto(gestoria, "+34600111222", "Pedro Pérez");
        Contacto cEmpleado = contacto(gestoria, "+34600111333", "Luis Peón");
        Contacto cMaria = contacto(gestoria, "+34600999888", "María Luz Sánchez");
        Contacto cAntonio = contacto(gestoria, "+34600777888", "Antonio Gallardo");
        Contacto cInactivo = contacto(gestoria, "+34600666777", "Baja Temporal");
        cInactivo.setActivo(false);
        contactoRepository.save(cInactivo);

        vincular(gestoria, cTitular, e1, RolContacto.TITULAR);
        vincular(gestoria, cTitular, e2, RolContacto.TITULAR);
        vincular(gestoria, cEmpleado, e1, RolContacto.EMPLEADO);
        vincular(gestoria, cMaria, e3, RolContacto.TITULAR);
        vincular(gestoria, cAntonio, e5, RolContacto.TITULAR);

        Instant ahora = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        // 1) WhatsApp baja por muerte — pendiente de revisión (el caso estrella del piloto)
        Tramite tBaja = tramite(gestoria, cTitular, e1, TipoTramite.BAJA_MUERTE,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tBaja.setFechaHecho(LocalDate.now(clock).minusDays(0));
        tramiteRepository.save(tBaja);
        crotal(gestoria, tBaja, "1274", a1274.getCrotal(), a1274, ResolucionCrotal.EN_INVENTARIO);
        mensaje(gestoria, cTitular, tBaja, "SM_DEMO_BAJA_1274",
                "Hola, la vaca con crotal 1274 se ha muerto hoy en el Robledo. ¿Podéis tramitar la baja?");

        // 2) Alta por nacimiento — pendiente, con madre y fechas
        Tramite tAlta = tramite(gestoria, cEmpleado, e1, TipoTramite.ALTA_NACIMIENTO,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tAlta.setFechaNacimiento(LocalDate.now(clock).minusDays(3));
        tAlta.setFechaIdentificacion(LocalDate.now(clock).minusDays(1));
        tAlta.setSexo("Hembra");
        tAlta.setCrotalMadreIndicado(a1275.getCrotal());
        tramiteRepository.save(tAlta);
        crotal(gestoria, tAlta, "ES060000009999", "ES060000009999", null, ResolucionCrotal.NO_ENCONTRADO);
        mensaje(gestoria, cEmpleado, tAlta, "SM_DEMO_ALTA_NAC",
                "Nació una ternera ayer, crotal ES060000009999, madre 1275, hembra.");

        // 3) Movimiento pendiente — crotal incompleto ambiguo
        Tramite tMov = tramite(gestoria, cTitular, e2, TipoTramite.SOLICITUD_MOVIMIENTO,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tMov.setFechaHecho(LocalDate.now(clock).plusDays(2));
        tMov.setDestinoTexto("Matadero municipal");
        tMov.setAptitudMovimiento("SACRIFICIO");
        tramiteRepository.save(tMov);
        crotal(gestoria, tMov, "9901", "9901", null, ResolucionCrotal.AMBIGUO);
        mensaje(gestoria, cTitular, tMov, "SM_DEMO_MOV_9901",
                "Quiero sacar la 9901 al matadero el viernes.");

        // 4) Sin explotación asignada (contacto con varias)
        Tramite tSinExp = tramite(gestoria, cTitular, null, TipoTramite.BAJA_MUERTE,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tramiteRepository.save(tSinExp);
        crotal(gestoria, tSinExp, "4401", "4401", null, ResolucionCrotal.SIN_EXPLOTACION);
        mensaje(gestoria, cTitular, tSinExp, "SM_DEMO_SIN_EXP",
                "Se ha muerto la 4401, no sé si era del Robledo o de Los Pinos.");

        // 5) Extracción fallida
        Tramite tFallida = tramite(gestoria, cMaria, e3, null,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.FALLIDA);
        tramiteRepository.save(tFallida);
        mensaje(gestoria, cMaria, tFallida, "SM_DEMO_FALLIDA",
                "Mmm creo q hay q hacer algo con unas vacas pero no me acuerdo bien...");

        // 6) Mensaje sin trámite (descartable)
        Tramite tSin = tramite(gestoria, cMaria, e3, null,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.WHATSAPP, EstadoExtraccion.SIN_TRAMITE);
        tramiteRepository.save(tSin);
        mensaje(gestoria, cMaria, tSin, "SM_DEMO_SIN_TRAMITE", "Vale, gracias. Un saludo.");

        // 7) Manual — confirmación de movimiento
        Tramite tManual = tramite(gestoria, null, e4, TipoTramite.CONFIRMACION_MOVIMIENTO,
                EstadoTramite.PENDIENTE_REVISION, OrigenTramite.MANUAL, null);
        tManual.setFechaHecho(LocalDate.now(clock).minusDays(1));
        tManual.setNumeroMer("MER-DEMO-7788");
        tramiteRepository.save(tManual);
        crotal(gestoria, tManual, "7701", "ES100000007701",
                animalRepository.findByCrotalAndGestoriaId("ES100000007701", gestoria.getId()).orElseThrow(),
                ResolucionCrotal.EN_INVENTARIO);

        // 8) Aprobado — falta hacer en OVZ
        Tramite tAprobado = tramite(gestoria, cAntonio, e5, TipoTramite.BAJA_MUERTE,
                EstadoTramite.APROBADO, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tAprobado.setFechaHecho(LocalDate.now(clock).minusDays(2));
        tramiteRepository.save(tAprobado);
        Animal a8801 = animalRepository.findByCrotalAndGestoriaId("ES060000008801", gestoria.getId()).orElseThrow();
        crotal(gestoria, tAprobado, "8801", a8801.getCrotal(), a8801, ResolucionCrotal.EN_INVENTARIO);
        mensaje(gestoria, cAntonio, tAprobado, "SM_DEMO_APROBADO",
                "Se murió la 8801 anteayer, tramitad la baja cuando podáis.");

        // 9) Hecho en OVZ
        Tramite tHecho = tramite(gestoria, cAntonio, e5, TipoTramite.DEMORA_CROTALIZACION,
                EstadoTramite.HECHO_EN_OVZ, OrigenTramite.MANUAL, null);
        tHecho.setFechaIdentificacion(LocalDate.now(clock).minusDays(5));
        tHecho.setHechoEnOvzPor(usuario);
        tHecho.setHechoEnOvzEn(ahora.minus(2, ChronoUnit.DAYS));
        tramiteRepository.save(tHecho);
        Animal a8802 = animalRepository.findByCrotalAndGestoriaId("ES060000008802", gestoria.getId()).orElseThrow();
        crotal(gestoria, tHecho, a8802.getCrotal(), a8802.getCrotal(), a8802, ResolucionCrotal.EN_INVENTARIO);

        // 10) Rechazado normal
        Tramite tRech = tramite(gestoria, cEmpleado, e1, TipoTramite.DECLARACION_CENSO,
                EstadoTramite.RECHAZADO, OrigenTramite.WHATSAPP, EstadoExtraccion.COMPLETADA);
        tramiteRepository.save(tRech);
        mensaje(gestoria, cEmpleado, tRech, "SM_DEMO_RECHAZADO",
                "Hay que hacer el censo de este año para el Robledo.");

        // 11) Descartado como SIN_TRAMITE
        Tramite tDesc = tramite(gestoria, cTitular, e1, null,
                EstadoTramite.RECHAZADO, OrigenTramite.WHATSAPP, EstadoExtraccion.SIN_TRAMITE);
        tDesc.setMotivoRechazo(MotivoRechazo.SIN_TRAMITE);
        tramiteRepository.save(tDesc);
        mensaje(gestoria, cTitular, tDesc, "SM_DEMO_DESCARTADO", "Perfecto, muchas gracias.");
        TramiteEvento evento = new TramiteEvento();
        evento.setGestoria(gestoria);
        evento.setTramite(tDesc);
        evento.setUsuario(usuario);
        evento.setTipo(TipoEventoTramite.DESCARTADO);
        evento.setCreatedAt(ahora.minus(1, ChronoUnit.DAYS));
        tramiteEventoRepository.save(evento);

        // 12) Pendiente de extracción (cola IA)
        Tramite tExtr = tramite(gestoria, cMaria, e3, null,
                EstadoTramite.PENDIENTE_EXTRACCION, OrigenTramite.WHATSAPP, EstadoExtraccion.PENDIENTE);
        tExtr.setProximoIntentoExtraccion(ahora.plus(1, ChronoUnit.HOURS));
        tramiteRepository.save(tExtr);
        mensaje(gestoria, cMaria, tExtr, "SM_DEMO_EXTRACCION",
                "Mañana saco dos vacas a pastos, os aviso con los crotales.");

        return new Resultado(true, gestoria.getId());
    }

    private Ganadero ganadero(Gestoria gestoria, String nombre, String nif) {
        Ganadero g = new Ganadero();
        g.setGestoria(gestoria);
        g.setNombre(nombre);
        g.setNif(nif);
        return ganaderoRepository.save(g);
    }

    private Explotacion explotacion(Gestoria gestoria, Ganadero ganadero, String rega, String nombre) {
        Explotacion e = new Explotacion();
        e.setGestoria(gestoria);
        e.setGanadero(ganadero);
        e.setCodigoRega(rega);
        e.setNombre(nombre);
        return explotacionRepository.save(e);
    }

    private Animal animal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal a = new Animal();
        a.setGestoria(gestoria);
        a.setExplotacion(explotacion);
        a.setCrotal(crotal);
        a.setCrotalUltimosDigitos(crotal.length() <= 6 ? crotal : crotal.substring(crotal.length() - 6));
        return animalRepository.save(a);
    }

    private Contacto contacto(Gestoria gestoria, String telefono, String nombre) {
        Contacto c = new Contacto();
        c.setGestoria(gestoria);
        c.setTelefono(telefono);
        c.setNombre(nombre);
        c.setActivo(true);
        return contactoRepository.save(c);
    }

    private void vincular(Gestoria gestoria, Contacto contacto, Explotacion explotacion, RolContacto rol) {
        ContactoExplotacion ce = new ContactoExplotacion();
        ce.setGestoria(gestoria);
        ce.setContacto(contacto);
        ce.setExplotacion(explotacion);
        ce.setRol(rol);
        contactoExplotacionRepository.save(ce);
    }

    private Tramite tramite(Gestoria gestoria, Contacto contacto, Explotacion explotacion,
                            TipoTramite tipo, EstadoTramite estado, OrigenTramite origen,
                            EstadoExtraccion extraccion) {
        Tramite t = new Tramite();
        t.setGestoria(gestoria);
        t.setContacto(contacto);
        t.setExplotacion(explotacion);
        t.setTipoTramite(tipo);
        t.setEstado(estado);
        t.setOrigen(origen);
        t.setEstadoExtraccion(extraccion);
        return t;
    }

    private void crotal(Gestoria gestoria, Tramite tramite, String indicado, String crotal,
                        Animal animal, ResolucionCrotal resolucion) {
        TramiteCrotal fila = new TramiteCrotal();
        fila.setGestoria(gestoria);
        fila.setTramite(tramite);
        fila.setCrotalIndicado(indicado);
        fila.setCrotal(crotal);
        fila.setAnimal(animal);
        fila.setResolucion(resolucion);
        tramiteCrotalRepository.save(fila);
    }

    private void mensaje(Gestoria gestoria, Contacto contacto, Tramite tramite,
                         String sid, String cuerpo) {
        MensajeCampo m = new MensajeCampo();
        m.setMessageSid(sid);
        m.setTelefonoOrigen(contacto.getTelefono());
        m.setCuerpo(cuerpo);
        m.setNumMedia(0);
        m.setResultado(ResultadoMensaje.TRAMITE_CREADO);
        m.setGestoria(gestoria);
        m.setContacto(contacto);
        m.setTramite(tramite);
        m.setCreatedAt(Instant.now(clock).minus(12, ChronoUnit.HOURS));
        mensajeCampoRepository.save(m);
    }
}
