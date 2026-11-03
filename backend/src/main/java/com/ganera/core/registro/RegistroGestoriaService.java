package com.ganera.core.registro;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroGestoriaService {

    private final GestoriaRepository gestoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public RegistroGestoriaService(
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea Gestoria + Usuario para el alta publica. Deliberadamente NO atrapa
     * DataIntegrityViolationException aqui dentro (UNIQUE(email)) -- debe propagar sin capturar
     * para que Spring marque esta transaccion como rollback-only y deshaga tambien el Gestoria ya
     * insertado; si se capturara aqui, ese Gestoria quedaria huerfano (sin Usuario). El caller
     * (RegistroGestoriaController), fuera de esta transaccion, es quien atrapa la excepcion para
     * dar forma a la respuesta HTTP. (Ese mismo controller, en cambio, deja subir sin capturar la
     * StripeException del checkout, que Spring traduce en un 500 generico.)
     */
    @Transactional
    public Long crearGestoriaYUsuario(RegistroGestoriaRequest request) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria(request.nombreGestoria()));

        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(request.email());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombreUsuario());
        usuario.setActivo(true);
        usuarioRepository.saveAndFlush(usuario);

        // Alta publica: actor SISTEMA (no hay usuario autenticado), gestoria = la recien creada, ORIGEN=REGISTRO
        // para distinguirla del onboarding interno. En la misma transaccion; si el UNIQUE(email) revienta en el
        // saveAndFlush de arriba, no se llega aqui y nada queda (ni auditoria ni gestoria).
        auditoriaService.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.GESTORIA, gestoria.getId(), null,
                DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.REGISTRO));
        auditoriaService.registrar(AccionAuditoria.USUARIO_CREADO, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.USUARIO, usuario.getId(), null,
                DatosAuditoria.con(Clave.ORIGEN, OrigenAuditoria.REGISTRO).y(Clave.ACTIVO, Boolean.TRUE));

        return gestoria.getId();
    }
}
