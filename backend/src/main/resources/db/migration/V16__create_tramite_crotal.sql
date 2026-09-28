-- Crotales mencionados en un Tramite (Prompt A1, Task 5, decision 20).
-- crotal_indicado: lo que se escribio (normalizado), nunca se pierde; al cambiar la Explotacion
-- del Tramite se vuelve a resolver desde aqui. crotal: completo si se resolvio a un unico Animal,
-- si no = crotal_indicado. resolucion: ResolucionCrotal.
CREATE TABLE tramite_crotal (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    tramite_id BIGINT NOT NULL REFERENCES tramite(id),
    crotal_indicado VARCHAR(30) NOT NULL,
    crotal VARCHAR(30) NOT NULL,
    animal_id BIGINT REFERENCES animal(id),
    resolucion VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tramite_id, crotal_indicado)
);
CREATE INDEX idx_tramite_crotal_tramite_id ON tramite_crotal(tramite_id);
CREATE INDEX idx_tramite_crotal_gestoria_id ON tramite_crotal(gestoria_id);
