package com.ganera.core.explotacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnimalRepository extends JpaRepository<Animal, Long> {
    List<Animal> findByExplotacionIdAndCrotalUltimosDigitos(Long explotacionId, String crotalUltimosDigitos);

    Optional<Animal> findByCrotal(String crotal);
}
