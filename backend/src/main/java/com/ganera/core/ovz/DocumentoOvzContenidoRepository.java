package com.ganera.core.ovz;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * El binario de un documento OVZ. Se carga por el id del documento, que SIEMPRE se ha validado antes con
 * {@code DocumentoOvzRepository.findByIdAndGestoriaId}: no es una {@code GestoriaScopedEntity}, asi que el
 * {@code findById} aqui es legitimo (el id no viene del atacante sin pasar por el filtro de gestoria).
 */
public interface DocumentoOvzContenidoRepository extends JpaRepository<DocumentoOvzContenido, Long> {
}
