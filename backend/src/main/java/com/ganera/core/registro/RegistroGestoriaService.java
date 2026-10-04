package com.ganera.core.registro;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroGestoriaService {

    private final GestoriaRepository gestoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistroGestoriaService(
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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

        return gestoria.getId();
    }
}
