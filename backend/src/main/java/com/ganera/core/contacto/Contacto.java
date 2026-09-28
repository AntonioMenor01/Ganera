package com.ganera.core.contacto;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quien escribe por WhatsApp. Pertenece a una Gestoria (gestoria_id NOT NULL),
 * pero telefono sigue siendo UNIQUE global a proposito (decision A): hay un
 * unico numero de Twilio compartido entre todas las gestorias, asi que al
 * llegar un mensaje solo se conoce el telefono, y ContactoRepository.findByTelefono
 * (sin scope de tenant) es la unica forma de resolver a que Gestoria pertenece.
 * Consecuencia: un mismo telefono no puede estar dado de alta en dos Gestorias.
 *
 * Borrado logico: activo=false conserva el historial (tramites/mensajes); los
 * inactivos no salen en listados ni pueden enlazarse a Explotaciones. El rol
 * (titular/empleado) vive en ContactoExplotacion, no aqui.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto")
public class Contacto extends GestoriaScopedEntity {

    @Column(nullable = false, unique = true)
    private String telefono;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;
}
