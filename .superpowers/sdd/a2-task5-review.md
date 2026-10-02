# A2 — Task 5: labels and badges (independent review)

## VERDICT: APPROVED (no Critical, no Important findings; 3 Minor, optional)

## Command results (from `frontend/`, run by the reviewer)

- `npm test`: **17 files, 166/166 passed** (13.0 s).
- `npm run build`: green (`tsc -b && vite build`, built in 752 ms). Only the usual >500 kB chunk notice.
- `npm run lint`: 0 errors, **3 warnings, all pre-existing** (`only-export-components` in
  `button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:231`).

## Mutation testing (scratch copy, deleted afterwards)

Each mutation was applied to a copy of `frontend/src` (node_modules junctioned) and `vitest run src/features` rerun:

| Mutation | Result |
|---|---|
| Unknown value falls back to `success` (`VARIANTE_DESCONOCIDA = "success"`) | 5 tests fail |
| Label `"Error en OVZ.net"` → `"Error OVZ"` | 4 fail |
| `NO_ENCONTRADO` → `warning` | 3 fail |
| Extra key `TRASLADO` added to `TIPOS_TRAMITE` | 1 fails (`toEqual` on the whole constant) |
| Contactos line removed from the import summary | 2 fail |
| `BadgePresentacion` ignores the variant (default green) | 10 fail |
| Dialog shows raw `detalle.tipoTramite` instead of the label | 1 fails |

The tests are meaningful: each regression class is caught.

## Verified, no finding

1. **Labels** (`frontend/src/features/tramites/etiquetas.ts`) match decision 6 exactly:
   - the 7 estados, with their labels and variants (warning ×3, success ×2, danger ×2);
   - the 5 tipos;
   - the 4 resoluciones;
   - the 2 roles.

   Grepping non-test `.ts`/`.tsx` for any tipo, estado, resolución or rol label, and for the old `"Ejecutado en OVZ"`/`"Error OVZ"`, only hits `etiquetas.ts`. `TIPOS_TRAMITE` is the single source. The queue filter is derived from `ESTADOS_TRAMITE` (`TramitesPage.tsx:30-36`), and the old divergent labels are gone. Nothing else renders a raw tramite enum: the only `<Badge>` for state is `BadgesTramite.tsx`.
2. **No backend rule is duplicated.**
   - Nothing classifies crotales as complete or incomplete.
   - Nothing re-derives approvability.
   - `NO_ENCONTRADO` is `outline`, and its comment marks it as PROVISIONAL (decision 21 (a)). Switching it is a single-entry change, as the M3 mutation showed.
3. **Unknown or blank values** are shown raw with the neutral `outline` variant, and a blank or whitespace value becomes "—".
   - `hasOwnProperty` guards against prototype keys such as `toString` and `constructor`.
   - The pre-existing bug where an unknown estado got `variant={undefined}` and so the default `bg-primary` green is fixed. The tests cover it.
   - A null or blank `tipoTramite` shows "—" in the queue and "Sin determinar todavía" in the dialog.
4. **API types match the backend field by field.**
   - `TramiteCrotal` matches `TramiteCrotalResponse`: `crotalIndicado`, `crotal`, `animalId` (Long, nullable), `enInventario` (boolean), `resolucion`.
   - `Tramite` matches `TramiteResponse`, including `crotales` and `version`. `Tramite.version` is `@Version @Column(nullable=false)`, so `number` is correct.
   - `TramiteDetalle` matches `TramiteDetalleResponse` (all 10 fields).
   - `ImportResumen` matches `ImportResumenResponse`: `explotaciones`, `animales`, `contactos` and `errores`.
   - `ImportHojaResumen` and `ImportErrorDto` were unchanged and still match.

   The enum sets match `EstadoTramite`, `TipoTramite`, `ResolucionCrotal` and `RolContacto` in the backend.
5. **Contrast.** Recomputed independently with the WCAG relative-luminance formula from the `:root` values in `index.css`:

   | Badge | Background | Ratio |
   |---|---|---|
   | success | | 8.21 |
   | warning | | 5.87 |
   | danger | | 8.98 |
   | outline | card or popover (#fff) | 17.23 |
   | outline | page background | 15.93 |
   | outline | hovered row (`muted/50` ≈ #f7f6f2) | 15.94 |
   | outline | selected row (`--muted` #eeede4) | 14.66 |

   - All pass AA at 12px/500, which needs 4.5:1.
   - The outline border is 1.27:1, which is decorative: the badge is not interactive, and its text carries the meaning.
   - Every badge renders its text label, so colour is never the only signal.
   - The `badge.tsx` variants and the colour tokens were not changed by this task. The only `index.css` diff is the Task 4 logo utility.
6. **OVZ.net wording.** "Ejecutado en OVZ.net" and "Error en OVZ.net" only name existing states: they are filter options and badges. The dialog and the Aprobar button have no OVZ.net wording at all. Grepping `OVZ` in non-test `.tsx` only hits a code comment in `ImportarExcelSection.tsx:18`.
7. **Import summary.**
   - The Contactos line uses the same `ResumenHoja` format and is always shown. That is justified, because the backend sends 0/0/0 for both "absent" and "empty".
   - The description names the columns "telefono, nombre, codigo_explotacion, rol", in the index order of `ExplotacionImportService.importarContactos` (columns 0–3) and its Javadoc (lines 35-37).
   - The sheet name "Contactos" matches `HOJA_CONTACTOS`.
8. **Scope.**
   - No file under `backend/` was touched.
   - Task 5 did not touch `DESIGN.md`, `CLAUDE.md`, the skills, `logo.jpg`, `public/`, `index.html` or `package.json`/lock. The only files newer than the Task 4 review are the Task 5 files in `features/tramites` and `features/explotaciones`, plus `index.css`/`PRODUCT.md` (Task 4, at 23:34) and the plan (orchestrator).
   - There are no new dependencies and no hex values in components.
   - Nothing is staged (`git diff --cached` is empty).

## Findings

### Critical
None.

### Important
None.

### Minor

**M1. A null value would crash the fallback.** `frontend/src/features/tramites/etiquetas.ts:25` (`textoCrudo`)
- **Scenario:** `presentacionEstado(null)` or `presentacionResolucion(null)` reaches `valor.trim()` and throws a TypeError, which blanks the queue render. The backend guarantees non-null today: `estado` and `resolucion` are `.name()` of non-null columns. The types also forbid null, so this only matters if the contract drifts.
- **Fix, optional hardening:** `(valor ?? "").trim() === "" ? TEXTO_VACIO : valor`, and accept `string | null | undefined` in the helpers.

**M2. Nothing tests the queue filter options.** `frontend/src/features/tramites/TramitesPage.tsx:30-36`
- **Scenario:** a future edit reintroduces a hard-coded `ESTADOS` list with drifting labels, such as "Error OVZ". No test opens the Select and checks its options, so the drift that Task 5 just fixed could come back silently.
- **Fix:** add a test that opens the filter and asserts that its options equal `"Todos los estados"` plus `Object.values(ESTADOS_TRAMITE).map(e => e.etiqueta)`.

**M3. DESIGN.md is now stale on badges.** `DESIGN.md:189-191`, `:279`, `:334` (not a Task 5 defect)
- **Scenario:** DESIGN.md still says the mapping lives in `BADGE_POR_ESTADO` in `types.ts`, and that `outline` "isn't used for state". Neither is true any more: `outline` is now the neutral for `NO_ENCONTRADO` and for unknown values. The implementer already listed the exact wording changes for Task 11.
- **Fix:** apply those changes in Task 11. Do not edit DESIGN.md in Task 5.

**Out of scope, for information only:** `FacturacionPage.tsx:80` uses the `secondary`/`destructive` badge variants for the subscription state. `destructive` is an opacity tint, not an Exact Pair. This predates Task 5 and is not caused by it; the Task 6 critique or the Task 11 audit could look at it.
