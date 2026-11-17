package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NumeroWhatsappRepository extends JpaRepository<NumeroWhatsapp, Long> {

    /**
     * Resuelve un número DEDICADO por su número de DESTINO (el {@code To} del webhook), normalizado a
     * E.164. Consulta DELIBERADAMENTE sin scope de tenant: al recibir el mensaje aún no hay gestoría, y
     * el número es globalmente UNIQUE, igual que {@code ContactoRepository.findByTelefono} y las dos
     * consultas por SID de {@code RespuestaWhatsappRepository}.
     *
     * <p><b>Reservada al webhook de recepción ({@code MensajeEntranteService}).</b> Ningún otro bean de
     * producción debe llamarla (lo vigila {@code ConsultasSinTenantTest}): resolver así fuera del webhook
     * se saltaría el aislamiento por gestoría.
     */
    Optional<NumeroWhatsapp> findByNumero(String numero);

    boolean existsByNumero(String numero);
}
