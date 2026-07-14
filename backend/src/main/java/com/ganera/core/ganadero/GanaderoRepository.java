package com.ganera.core.ganadero;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {
    Optional<Ganadero> findByNif(String nif);
}
