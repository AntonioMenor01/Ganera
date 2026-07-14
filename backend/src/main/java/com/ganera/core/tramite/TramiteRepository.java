package com.ganera.core.tramite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
    Page<Tramite> findByEstado(EstadoTramite estado, Pageable pageable);

    /**
     * Usar SIEMPRE esta en vez de findById(id) a secas cuando el id venga de un path variable
     * (potencialmente de cualquier Gestoria): Hibernate NO aplica gestoriaFilter a
     * EntityManager.find()/findById() por clave primaria -- el filtro solo se aplica a queries
     * derivadas/HQL de resultado (findAll, findByEstado, esta misma). Sin el gestoriaId explicito
     * aqui, un id de otra Gestoria se resuelve igual (encontrado en auditoria post-mortem del
     * bug de WebMvcTenantConfig, 2026-07-14 -- ver TenantIsolationEndToEndTest).
     */
    Optional<Tramite> findByIdAndGestoriaId(Long id, Long gestoriaId);
}
