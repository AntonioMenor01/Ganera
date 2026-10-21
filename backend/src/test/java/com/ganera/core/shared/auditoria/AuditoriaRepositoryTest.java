package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La auditoria es de solo insercion tambien en Java: el repositorio no hereda ningun metodo de borrado ni de
 * actualizacion (ni findById a secas) y solo declara save y la lectura por gestoria.
 */
class AuditoriaRepositoryTest {

    @Test
    void noHeredaCrudNiTieneMetodosDeBorradoNiDeActualizacion() {
        assertThat(Repository.class).isAssignableFrom(AuditoriaRepository.class);
        for (Class<?> prohibido : new Class<?>[] {CrudRepository.class, ListCrudRepository.class,
                PagingAndSortingRepository.class, JpaRepository.class}) {
            assertThat(prohibido.isAssignableFrom(AuditoriaRepository.class)).as(prohibido.getName()).isFalse();
        }
        assertThat(AuditoriaRepository.class.getInterfaces()).containsExactly(Repository.class);

        Method[] metodos = AuditoriaRepository.class.getMethods(); // incluidos los heredados
        assertThat(metodos).extracting(Method::getName)
                .containsExactlyInAnyOrder("save", "findByGestoriaIdOrderByIdAsc");
        for (Method metodo : metodos) {
            String nombre = metodo.getName().toLowerCase(Locale.ROOT);
            assertThat(nombre).as(metodo.toString())
                    .doesNotStartWith("delete").doesNotStartWith("remove").doesNotStartWith("update");
            assertThat(metodo.isAnnotationPresent(Modifying.class)).as(metodo.toString()).isFalse();
            assertThat(metodo.isAnnotationPresent(Query.class)).as(metodo.toString()).isFalse();
        }
        assertThat(Arrays.stream(metodos).filter(m -> m.getName().equals("save")).findFirst().orElseThrow()
                .getParameterTypes()).containsExactly(Auditoria.class);
    }
}
