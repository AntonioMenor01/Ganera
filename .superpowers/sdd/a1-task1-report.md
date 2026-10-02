# Prompt A1 - Task 1 report (implementer)

## Files
Created (backend/):
- src/main/resources/db/migration/V15__contacto_tenant_rol_y_activo.sql (exactly as in the plan)
- src/main/java/com/ganera/core/contacto/RolContacto.java (TITULAR, EMPLEADO)
- src/main/java/com/ganera/core/contacto/TelefonoNormalizador.java (public final, static normalizar, no Spring)
- src/main/java/com/ganera/core/contacto/ContactoExplotacionRepository.java (3 finders, all with explicit gestoriaId)
- src/test/java/com/ganera/core/contacto/TelefonoNormalizadorTest.java (22 parameterized cases, pure JUnit)
- src/test/java/com/ganera/core/contacto/ContactoExplotacionRepositoryTest.java (5 tests)

Modified:
- contacto/Contacto.java: extends GestoriaScopedEntity; telefono (unique), nombre, activo (default true); tipo removed; Javadoc updated (tenant-scoped, telefono still globally unique by decision A, logical delete)
- contacto/ContactoExplotacion.java: extends GestoriaScopedEntity; contacto, explotacion, rol (@Enumerated STRING, NOT NULL); Javadoc updated (role on the relation, EMPLEADO not limited to one Explotacion)
- contacto/ContactoRepository.java: findByTelefono kept + Javadoc (NOT tenant-scoped, EXCLUSIVE to the 3b Twilio webhook; importer/authenticated endpoints must use findByGestoriaIdAndTelefono); added findByIdAndGestoriaId, findByGestoriaIdAndTelefono, findByGestoriaIdAndActivoTrue(Pageable), findByGestoriaId(Pageable)
- test ContactoRepositoryTest: rewritten for new model (2 -> 7 tests)
- test TramiteControllerTest, TramiteRepositoryTest, TenantIsolationEndToEndTest: Contacto now gets setGestoria(...) instead of setTipo(...). In TramiteControllerTest the helper became nuevoContacto(gestoria, telefono); the Contacto is attached to the same Gestoria as its Tramite (gestoriaAjena in the 3 cross-tenant tests). No assertion changed or removed.

Deleted:
- contacto/TipoContacto.java

## TDD evidence
1. TelefonoNormalizadorTest written first -> compilation failure (TelefonoNormalizador missing). Then a stub always returning Optional.empty() -> "Tests run: 22, Failures: 10" (all 10 valid cases failed, invalid ones passed trivially). Real implementation -> 22/22 green.
2. ContactoRepositoryTest (rewritten) and ContactoExplotacionRepositoryTest written before the model changes -> compilation failure (ContactoExplotacionRepository, RolContacto, setActivo/new finders missing). After migration + entities + repos -> 7/7 and 5/5 green.

## Final full suite
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` -> Tests run: 167, Failures: 0, Errors: 0, Skipped: 0
(135 previous + 22 normalizer + 5 ContactoExplotacionRepositoryTest + 5 net new in ContactoRepositoryTest).

## Deviations / concerns
- None functional. Extra tests beyond the brief: telefono duplicate within the SAME Gestoria also rejected; findByIdAndGestoriaId cross-tenant check; activo defaults to true on insert; an EMPLEADO linked to two Explotaciones (decision 4).
- Normalizer: input is strip()-ed before removing the "whatsapp:" prefix; separators removed are whitespace (\s), '.' and '-'. "(612)345678" rejected as required.
- @Column(length = 20) added on ContactoExplotacion.rol to match VARCHAR(20); harmless with ddl-auto=none.
- Test data uses "ES-ROL-1"-style codigoRega values (no format validation exists on Explotacion).
- Nothing staged/committed. CLAUDE.md not updated (belongs to Task 7).

## Fixes after review
- I1: TelefonoNormalizador INTERNACIONAL is now `\+[1-9]\d{7,14}` (the +34 sub-rule is unchanged). Added "+0034612345678" and "+06123456789" to the invalid cases.
- M1: SEPARADORES now also strips U+00A0, U+2007 and U+202F. Added two valid cases: "612<U+00A0>345<U+00A0>678" and "612<U+2007>345<U+202F>678", both expected to give "+34612345678". The test source uses `\uXXXX` escapes so the file stays ASCII-only. The Javadoc rules 2 and 3 are updated to match.
- M4: ContactoExplotacionRepositoryTest.buscarPorContactoYExplotacionNuncaDevuelveFilasDeOtraGestoria now also creates a link in Gestoria B. It asserts that A's lookup returns only A's row (TITULAR), that B's lookup returns only B's row (EMPLEADO), and that both cross lookups are empty.
- TDD: the 4 new normalizer cases were added first and failed ("Tests run: 26, Failures: 4": invalid [11],[12] and valid [11],[12]). After the fix they pass 26/26. The M4 test passed straight away, as expected, because it strengthens an existing test rather than covering a bug.
- M2 ("+612345678") was deliberately not changed; it is a product decision for Antonio. Nothing else was touched, and nothing is staged or committed.
- Full suite: Tests run: 171, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS). That is 167 + 4 new normalizer cases.
