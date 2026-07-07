package com.ganera.core.shared.tenant;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TenantFilterActivationInterceptorTest {

    @Autowired
    private EntityManager entityManager;

    private final TenantFilterActivationInterceptor interceptor = new TenantFilterActivationInterceptor();

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activaElFiltroConElGestoriaIdDelPrincipalAutenticado() throws Exception {
        GaneraUserPrincipal principal = new GaneraUserPrincipal(1L, 42L, "empleado@gestoria.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        interceptor.preHandle(null, null, null);

        Session session = entityManager.unwrap(Session.class);
        assertThat(session.getEnabledFilter("gestoriaFilter")).isNotNull();
        assertThat(session.getEnabledFilter("gestoriaFilter").getParameter("gestoriaId")).isEqualTo(42L);
    }

    @Test
    void noActivaNadaSinAutenticacionYNoLanzaExcepcion() throws Exception {
        interceptor.preHandle(null, null, null);

        Session session = entityManager.unwrap(Session.class);
        assertThat(session.getEnabledFilter("gestoriaFilter")).isNull();
    }
}
