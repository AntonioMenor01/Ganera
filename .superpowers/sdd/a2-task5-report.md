# A2 — Task 5: labels and badges (implementer report)

Frontend only. `backend/` was read to check the DTOs and was not modified. No git operations, no new dependencies.

## Files changed

**New:**
- `frontend/src/features/tramites/etiquetas.ts`: the single source of labels and badge variants per domain.
- `frontend/src/features/tramites/etiquetas.test.ts`
- `frontend/src/features/tramites/BadgesTramite.tsx`: `BadgeEstadoTramite` and `BadgeResolucionCrotal`. The second one is ready for Task 9.
- `frontend/src/features/tramites/BadgesTramite.test.tsx`
- `frontend/src/features/explotaciones/ImportarExcelSection.test.tsx`

**Modified:**
- `frontend/src/features/tramites/types.ts`:
  - `EstadoTramite` and the old `BADGE_POR_ESTADO` move to `etiquetas.ts`.
  - `badgeVarianteDeEstado` is re-exported from here, because DESIGN.md cites it.
  - New type `TramiteCrotal`.
  - `Tramite` and `TramiteDetalle` gain `crotales` and `version`.
- `frontend/src/features/tramites/TramitesPage.tsx`:
  - The state badge now shows its label, and the tipo shows its label.
  - The state filter is derived from `ESTADOS_TRAMITE`. Before, it had divergent labels: "Ejecutado en OVZ" and "Error OVZ".
- `frontend/src/features/tramites/TramiteReviewDialog.tsx`: the estado and tipo show their labels. Nothing else changed; the editable modal belongs to Task 9.
- `frontend/src/features/explotaciones/types.ts`: `ImportResumen.contactos` (H10).
- `frontend/src/features/explotaciones/ImportarExcelSection.tsx`:
  - A "Contactos" line in the summary.
  - `data-testid="resumen-importacion"`.
  - The description mentions the optional sheet.
- Tests updated: `TramitesPage.test.tsx` and `TramiteReviewDialog.test.tsx` gained a "etiquetas legibles" block.

## Contract checked against `backend/`

All field names were checked against the DTOs.

| Frontend type | Backend DTO | Fields |
|---|---|---|
| `TramiteCrotal` | `TramiteCrotalResponse` | `crotalIndicado`, `crotal`, `animalId` (Long, nullable), `enInventario` (boolean), `resolucion` (the `ResolucionCrotal` name) |
| `Tramite` | `TramiteResponse` | adds `crotales` and `version` (Long) |
| `TramiteDetalle` | `TramiteDetalleResponse` | adds `crotales` and `version` |
| `ImportResumen.contactos` | `ImportResumenResponse.contactos` | `ImportHojaResumen` |

`RolContacto` is `TITULAR` or `EMPLEADO`.

`tipoTramite` stays typed as `string | null`, not `TipoTramite`. After prompt B the backend may send tipos the frontend does not know yet, and the type should not pretend otherwise.

## Constants (`etiquetas.ts`)

- **`TIPOS_TRAMITE`** is the only tipo constant:
  - `ALTA` "Alta", `BAJA` "Baja", `CENSO` "Censo", `MOVIMIENTO` "Movimiento", `DEMORA` "Demora".
  - Derived type: `TipoTramite`.
  - Helper: `etiquetaTipoTramite(tipo: string)`.
- **`ESTADOS_TRAMITE`** gives `{etiqueta, variante}` for all 7 states, exactly as the table in the brief:
  - warning: "Pendiente de extracción", "Pendiente de revisión", "En proceso";
  - success: "Aprobado", "Ejecutado en OVZ.net";
  - danger: "Error en OVZ.net", "Rechazado".
  - Derived type: `EstadoTramite`.
  - Helpers: `presentacionEstado` and `badgeVarianteDeEstado`. The latter is also re-exported from `types.ts`.
- **`RESOLUCIONES_CROTAL`**:
  - `EN_INVENTARIO` "En inventario" (success);
  - `AMBIGUO` "Varios animales coinciden" (warning);
  - `NO_ENCONTRADO` "No está en el inventario" (**outline, PROVISIONAL**);
  - `SIN_EXPLOTACION` "Falta la explotación" (warning).
  - Derived type: `ResolucionCrotal`.
  - Helper: `presentacionResolucion`.
- **`ROLES_CONTACTO`**: `TITULAR` "Titular", `EMPLEADO` "Empleado". Helper: `etiquetaRolContacto`.

**Fallback for unknown values:**
- The raw value is shown with the `outline` variant.
- A blank value becomes "—", so the UI never shows an empty string.
- Lookups use `hasOwnProperty`, so values like "toString" or "constructor" also fall back to the raw value.

**Bug fixed along the way:** before this task, an unknown estado got `variant={undefined}`, and `Badge` then applied its default green (`bg-primary`). That breaks the One Green Rule and makes an unknown state look like a success. The red run showed it.

## Contrast (WCAG AA, text at 12px/500 needs 4.5:1)

Computed from the `:root` hex values in `index.css` with the WCAG relative-luminance formula. The script is in the scratchpad, `contraste.mjs`. No pair fails, and the palette was not touched.

| Badge | Text | Background | Ratio | AA |
|---|---|---|---|---|
| success | `--success-foreground` #27500a | `--success` #eaf3de | **8.21:1** | pass |
| warning | `--warning-foreground` #854f0b | `--warning` #faeeda | **5.87:1** | pass |
| danger | `--danger-foreground` #791f1f | `--danger` #fcebeb | **8.98:1** | pass |
| outline on a table (`--card`) | `--foreground` #1c1b17 | #ffffff | **17.23:1** | pass |
| outline in the modal (`--popover`) | #1c1b17 | #ffffff | **17.23:1** | pass |
| outline on the page (`--background`) | #1c1b17 | #f7f6f1 | **15.93:1** | pass |
| outline on a hovered row (card + `muted`/50) | #1c1b17 | ≈#f7f6f2 | **15.94:1** | pass |

Notes on the table:
- The outline badge has a transparent background, so its contrast depends on the surface it sits on. All real surfaces are listed.
- The outline border `--border` #e6e4da on white is 1.27:1. It is decorative: a badge is not interactive and its meaning comes from the text, so WCAG 1.4.11 does not apply. The side effect is that outline badges read as "plain text in a pill", which is the intent for a neutral.
- The outline variant uses tokens only: `border-border text-foreground`. There are no hex values in components.
- The `.dark` block was not verified: DESIGN.md says it is not a designed theme.

## Impeccable guidance applied

- **Launcher:** `impeccable context` ran fine and loaded PRODUCT.md and DESIGN.md. No `CONTEXT_STALE` directive appeared. There was no interview step, because the brief fully specifies the work.
- **Mode:** Operate. This is a refinement, not a redesign: identity and copy are preserved, and nothing outside scope was touched.
- **colorize.md, "Contrast and perception":** computed pairs are checked with numbers rather than by eye, every relevant surface is covered, and meaning is never carried by color alone. Every badge carries its text label.
- **clarify.md:** the same noun is used for the same concept everywhere. The filter and the badges now share one source, which removed the "Error OVZ" / "Error en OVZ.net" drift.
- **craft-floor.md:** read before editing. Contrast is at or above 4.5:1, there are no new colors or hex values, and the Token-Only and Exact Pair rules hold. State reuses the three existing pairs, plus the neutral `outline`.

## TDD evidence

1. The tests were written first:
   - `etiquetas.test.ts`, `BadgesTramite.test.tsx` and `ImportarExcelSection.test.tsx` are new;
   - the "etiquetas legibles" blocks were added to the queue and dialog tests.
2. **Red run:** `vitest run src/features/tramites src/features/explotaciones/ImportarExcelSection.test.tsx` gave **5 files failed, 6 tests failed, 6 passed (12)**.
   - The two new modules failed at import.
   - The queue did not find "Pendiente de revisión".
   - An unknown estado came out with `bg-primary` instead of `border-border`.
   - The import summary had no Contactos line.
3. **Green run after the implementation:** the same folders gave 6 files and **48/48 passed**.

**Coverage:**
- Every enum value maps to its label and variant (`it.each` per domain, plus `toEqual` on the whole constant, so an extra or missing key fails).
- Unknown and blank values, and prototype keys, fall back correctly.
- The re-export of `badgeVarianteDeEstado` from `types.ts` is covered.
- The queue and the dialog render labels and not enums, including an unknown estado or tipo in the queue.
- The import summary shows Contactos, including the 0/0/0 case.

## Commands (from `frontend/`)

- `npm test`: **17 files, 166/166 passed**. Before this task there were 17 − 3 new files; the suite grew by 40 tests.
- `npm run build`: green (`tsc -b && vite build`, built in 729ms).
  - The first attempt failed on types in the new test (`unknown` passed to `HttpResponse.json`). I fixed it by typing it as `ImportResumen`.
  - Vite prints its usual chunk-size (>500 kB) notice. It is not an error, and this task only added a few small modules.
- `npm run lint`: **only the 3 pre-existing warnings** (`only-export-components` in `AuthContext.tsx:231`, `badge.tsx:55` and `button.tsx:58`). There are no errors.

## Grep: `TIPOS_TRAMITE` is the only source of tipo labels

Two greps were run over `frontend/src`:
- labels: `"(Alta|Baja|Censo|Movimiento|Demora)"`;
- enums: `"(ALTA|…|DEMORA)"` and `(ALTA|…):`.

Results:
- **Non-test code:** the only hits are `etiquetas.ts:36-40`.
- **Tests:** the other hits are test fixtures and assertions, in `etiquetas.test.ts`, `TramitesPage.test.tsx` and `TramiteReviewDialog.test.tsx`.

The same holds for the estado, resolución and rol labels: outside tests, they appear only in `etiquetas.ts`.

## Import summary (H10): decision to always show zeros

The Contactos line is **always shown**, with 0/0/0 when the sheet was absent. The reasons:
1. The frontend cannot tell "sheet absent" from "sheet present but empty", because the backend sends 0/0/0 in both cases. Hiding the line would claim knowledge the frontend does not have.
2. Explotaciones and Animales already show zeros. Contactos in the same format keeps the three lines consistent.
3. H10 exists to avoid silent data, and a hidden line would be silent again.

The card description now also names the optional sheet: "Opcional: hoja "Contactos" (telefono, nombre, codigo_explotacion, rol)". The column order was taken from `ExplotacionImportService`. Otherwise the new summary line would show up with no explanation of where it comes from.

## `NO_ENCONTRADO` is provisional (decision 21)

- It uses the neutral `outline` variant because Antonio has not decided how to distinguish an incomplete crotal.
- The API never returns an empty `crotal` (for `NO_ENCONTRADO`, `crotal = crotalIndicado`), so the frontend cannot know whether what was typed was incomplete.
- **No crotal classification was added** (for example "digits only means incomplete"). That would duplicate a backend rule.
- Switching to amber means changing only the `NO_ENCONTRADO` entry in `RESOLUCIONES_CROTAL` to `variante: "warning"`. The comment on that entry says so.
- If the future decision needs the " · incompleto" suffix, it will have to come from a new API field. The frontend should not derive it.

## `DESIGN.md` updates needed (for Task 11)

- **Exact Pair Rule:** replace "mapping them to one of the three existing pairs in `BADGE_POR_ESTADO` (`features/tramites/types.ts`)" with "in `ESTADOS_TRAMITE` (`features/tramites/etiquetas.ts`)". `badgeVarianteDeEstado` still exists and is re-exported from `types.ts`.
- **Components → Badges:** today it says `outline` "isn't used for state". That is no longer true:
  - `outline` is the neutral for unknown values from the API;
  - it is also used for `NO_ENCONTRADO` (provisional).
  - Suggested wording: "state badges use success/warning/danger, plus `outline` as the neutral for values that are not a problem (a crotal not in inventory) or unknown to the frontend".
- **Do's:** add "badges always show the label from `etiquetas.ts`, never the raw enum; `BadgeEstadoTramite` / `BadgeResolucionCrotal`".
- The contrast table above can go into DESIGN.md as proof of AA: 8.21, 5.87 and 8.98 for the badge pairs, and ≥15.9 for outline.
