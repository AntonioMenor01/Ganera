package com.ganera.core.contacto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {
    Optional<Contacto> findByTelefono(String telefono);
}
