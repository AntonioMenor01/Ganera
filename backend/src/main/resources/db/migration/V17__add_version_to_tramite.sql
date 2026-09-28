-- Version optimista del Tramite (Prompt A1, Task 7a, decision 27). PATCH /tramites/{id} y
-- POST /tramites/{id}/aprobar exigen la version que mostraba la pantalla; si no coincide con la
-- actual -> 409 sin cambios. Toda escritura del Tramite (incluida la re-resolucion de crotales al
-- aprobar, que solo toca tramite_crotal) la incrementa. Los Tramites existentes empiezan en 0.
ALTER TABLE tramite ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
