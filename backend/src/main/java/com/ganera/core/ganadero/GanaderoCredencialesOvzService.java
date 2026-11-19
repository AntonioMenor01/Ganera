package com.ganera.core.ganadero;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Credenciales OVZ por ganadero (escalon 2, T1). gestoriaId y usuarioId SIEMPRE del JWT. Carga con
 * {@code findByIdAndGestoriaId} (nunca findById a secas): vacio si el Ganadero no existe o es de otra Gestoria, y
 * el controlador responde 404 sin cuerpo (y audita el acceso ajeno fuera de esta transaccion).
 *
 * <p>La contrasena se cifra con {@link CifradorAesGcm#cifrar} (tipo {@code CredencialOvzCifrada}); nunca se guarda
 * en claro ni vuelve en respuestas. La auditoria lleva SOLO los codigos de los campos cambiados y el ORIGEN
 * (MANUAL): jamas el usuario ni la contrasena ({@code DatosAuditoria} no admite String).
 *
 * <p>Si {@code usaCertificado} es {@code true}, la cuenta entra con certificado (D8, no soportada en automatico):
 * se marca el flag y se BORRAN usuario y contrasena. Si es {@code false}, se guardan usuario + contrasena cifrada
 * y el flag queda a {@code false}.
 */
@Service
public class GanaderoCredencialesOvzService {

    private final GanaderoRepository ganaderoRepository;
    private final GanaderoConsultaService ganaderoConsultaService;
    private final AuditoriaService auditoriaService;
    private final CifradorAesGcm cifrador;

    public GanaderoCredencialesOvzService(GanaderoRepository ganaderoRepository,
                                          GanaderoConsultaService ganaderoConsultaService,
                                          AuditoriaService auditoriaService,
                                          CifradorAesGcm cifrador) {
        this.ganaderoRepository = ganaderoRepository;
        this.ganaderoConsultaService = ganaderoConsultaService;
        this.auditoriaService = auditoriaService;
        this.cifrador = cifrador;
    }

    /**
     * Guarda (o reemplaza) las credenciales OVZ. {@code usuario}/{@code password} ya vienen validados por el
     * controlador cuando {@code usaCertificado} es false (ambos no vacios); con certificado se ignoran. Devuelve el
     * detalle actualizado, o vacio si el Ganadero no es de esta Gestoria.
     */
    @Transactional
    public Optional<GanaderoDetalleResponse> guardar(Long gestoriaId, Long usuarioId, Long ganaderoId,
                                                     String usuario, String password, boolean usaCertificado) {
        Optional<Ganadero> encontrado = ganaderoRepository.findByIdAndGestoriaId(ganaderoId, gestoriaId);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        Ganadero ganadero = encontrado.get();
        boolean teniaCredenciales = ganadero.getOvzPasswordCifrada() != null
                || ganadero.isUsaCertificado() || ganadero.getOvzUsuario() != null;

        List<CampoCredencialOvz> cambiados = new ArrayList<>();
        if (usaCertificado) {
            // Cuenta con certificado: no hay usuario/contrasena validos. Se borran si los habia.
            if (ganadero.getOvzUsuario() != null) {
                cambiados.add(CampoCredencialOvz.USUARIO);
                ganadero.setOvzUsuario(null);
            }
            if (ganadero.getOvzPasswordCifrada() != null) {
                cambiados.add(CampoCredencialOvz.PASSWORD);
                ganadero.setOvzPasswordCifrada(null);
            }
            if (!ganadero.isUsaCertificado()) {
                cambiados.add(CampoCredencialOvz.USA_CERTIFICADO);
            }
            ganadero.setUsaCertificado(true);
        } else {
            if (!Objects.equals(ganadero.getOvzUsuario(), usuario)) {
                cambiados.add(CampoCredencialOvz.USUARIO);
            }
            ganadero.setOvzUsuario(usuario);
            // La contrasena es de escritura: no se puede comparar con la anterior (IV nuevo cada vez), asi que
            // reescribirla cuenta siempre como cambio. Se cifra aqui (nunca se guarda en claro).
            cambiados.add(CampoCredencialOvz.PASSWORD);
            ganadero.setOvzPasswordCifrada(cifrador.cifrar(password));
            if (ganadero.isUsaCertificado()) {
                cambiados.add(CampoCredencialOvz.USA_CERTIFICADO);
            }
            ganadero.setUsaCertificado(false);
        }
        ganaderoRepository.saveAndFlush(ganadero);

        AccionAuditoria accion = teniaCredenciales
                ? AccionAuditoria.CREDENCIAL_OVZ_MODIFICADA
                : AccionAuditoria.CREDENCIAL_OVZ_GUARDADA;
        auditoriaService.registrar(accion, gestoriaId, Actor.usuario(usuarioId), EntidadAuditada.GANADERO, ganaderoId,
                null, DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.MANUAL)
                        .yCodigos(Clave.CAMPOS_CAMBIADOS, cambiados));
        return ganaderoConsultaService.detalle(gestoriaId, ganaderoId);
    }

    /**
     * Borra las credenciales OVZ (usuario, contrasena y flag de certificado). Idempotente: si no habia nada,
     * tambien deja la fila {@code CREDENCIAL_OVZ_BORRADA}. Vacio si el Ganadero no es de esta Gestoria.
     */
    @Transactional
    public Optional<GanaderoDetalleResponse> borrar(Long gestoriaId, Long usuarioId, Long ganaderoId) {
        Optional<Ganadero> encontrado = ganaderoRepository.findByIdAndGestoriaId(ganaderoId, gestoriaId);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        Ganadero ganadero = encontrado.get();
        ganadero.setOvzUsuario(null);
        ganadero.setOvzPasswordCifrada(null);
        ganadero.setUsaCertificado(false);
        ganaderoRepository.saveAndFlush(ganadero);

        auditoriaService.registrar(AccionAuditoria.CREDENCIAL_OVZ_BORRADA, gestoriaId, Actor.usuario(usuarioId),
                EntidadAuditada.GANADERO, ganaderoId, null, DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.MANUAL));
        return ganaderoConsultaService.detalle(gestoriaId, ganaderoId);
    }
}
