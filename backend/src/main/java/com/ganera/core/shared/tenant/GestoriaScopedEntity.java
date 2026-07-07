package com.ganera.core.shared.tenant;

import com.ganera.core.gestoria.Gestoria;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "gestoriaFilter", parameters = @ParamDef(name = "gestoriaId", type = Long.class))
@Filter(name = "gestoriaFilter", condition = "gestoria_id = :gestoriaId")
public abstract class GestoriaScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestoria_id", nullable = false, updatable = false)
    private Gestoria gestoria;

    private Instant createdAt = Instant.now();
}
