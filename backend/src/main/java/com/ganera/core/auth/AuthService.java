package com.ganera.core.auth;

import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoriaService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Auditoria (A2): un login correcto y un login fallido con email EXISTENTE dejan fila (en la gestoria de
     * ese usuario), con {@code registrarAparte} (REQUIRES_NEW) porque no hay transaccion de negocio y la fila
     * debe quedar aunque la respuesta sea {@code 401}. Un email inexistente NO deja fila: solo un log sin el
     * email. El {@code 401} que devuelve el controlador es identico byte a byte en los tres casos (el motivo
     * solo va a la fila, nunca al cliente).
     */
    public Optional<String> autenticar(String email, String password) {
        // BCrypt lanza IllegalArgumentException con password null: sin este guard, un body
        // malformado responderia 500 solo cuando el email existe (oraculo de enumeracion). Sin email o
        // password no hay usuario que auditar: se trata como un email inexistente (sin fila).
        if (email == null || password == null) {
            log.info("Login rechazado: faltan credenciales");
            return Optional.empty();
        }
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(email);
        if (encontrado.isEmpty()) {
            // Sin fila (no hay gestoria ni usuario): solo un log SIN el email ni nada que lo identifique.
            log.info("Login fallido: email desconocido");
            return Optional.empty();
        }
        Usuario usuario = encontrado.get();
        if (!usuario.isActivo()) {
            auditarLoginFallido(usuario, MotivoLoginFallido.USUARIO_INACTIVO);
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            auditarLoginFallido(usuario, MotivoLoginFallido.CONTRASENA_INCORRECTA);
            return Optional.empty();
        }
        auditoriaService.registrarAparte(AccionAuditoria.LOGIN_CORRECTO, usuario.getGestoria().getId(),
                Actor.usuario(usuario.getId()), EntidadAuditada.USUARIO, usuario.getId(), null, null);
        return Optional.of(jwtService.generarToken(
                new GaneraUserPrincipal(usuario.getId(), usuario.getGestoria().getId(), usuario.getEmail())));
    }

    private void auditarLoginFallido(Usuario usuario, MotivoLoginFallido motivo) {
        auditoriaService.registrarAparte(AccionAuditoria.LOGIN_FALLIDO, usuario.getGestoria().getId(),
                Actor.usuario(usuario.getId()), EntidadAuditada.USUARIO, usuario.getId(),
                null, DatosAuditoria.con(Clave.MOTIVO, motivo));
    }

    /**
     * Cambia la contrasena del propio usuario del JWT (el {@code usuarioId} nunca es atacable: viene del token,
     * no de la peticion). Carga con {@code findByIdAndGestoriaId} (nunca findById a secas sobre una
     * GestoriaScopedEntity), comprueba la contrasena actual, guarda el nuevo hash, baja el flag
     * {@code debeCambiarPassword} y deja fila de auditoria {@code USUARIO_CAMBIO_PASSWORD} dentro de la misma
     * transaccion (registrar, MANDATORY). La contrasena NUNCA va a auditoria ni a logs (antes/despues null). La
     * validacion de formato de la contrasena nueva la hace el controlador antes de llegar aqui.
     */
    @Transactional
    public Usuario cambiarPassword(GaneraUserPrincipal principal, String passwordActual, String passwordNueva) {
        Usuario usuario = usuarioRepository.findByIdAndGestoriaId(principal.usuarioId(), principal.gestoriaId())
                .orElseThrow(UsuarioNoEncontradoException::new);
        if (passwordActual == null || !passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new PasswordActualIncorrectaException();
        }
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuario.setDebeCambiarPassword(false);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(AccionAuditoria.USUARIO_CAMBIO_PASSWORD, usuario.getGestoria().getId(),
                Actor.usuario(usuario.getId()), EntidadAuditada.USUARIO, usuario.getId(), null, null);
        return usuario;
    }

    /** La contrasena actual no coincide: el controlador la mapea a 400 {motivo} (no es enumeracion: es tu cuenta). */
    public static class PasswordActualIncorrectaException extends RuntimeException {
    }

    /** No existe el usuario del JWT en su gestoria (practicamente imposible con un token valido): 404 sin cuerpo. */
    public static class UsuarioNoEncontradoException extends RuntimeException {
    }
}
