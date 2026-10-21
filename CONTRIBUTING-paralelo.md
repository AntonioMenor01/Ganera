# Trabajo en paralelo — Antonio y Álvaro

> Borrador para acordar entre los dos. Hasta que Antonio lo apruebe, esto no es el acuerdo: es la
> propuesta. Última revisión: 2026-10-21.

La regla de fondo: cada uno toca su zona del código, y lo que depende del otro espera a que esté
fusionado en `main`.

## 1. Ramas y pull requests

1. **Una rama por tarea**, con nombre claro: `feat/mistral`, `feat/documentos`, `feat/despliegue`.
   Se crea desde `main` actualizado.
2. **Nunca push a `main`.** Todo entra por pull request.
3. **Nadie fusiona su propia pull request.** Antonio revisa las de Álvaro; Álvaro revisa las de
   Antonio. Se fusiona solo con tests, build y lint en verde.
4. **Antes de abrir la pull request**, actualizar la rama con `main` (`git fetch` + `git rebase
   origin/main` o `merge`), relanzar todos los tests y comprobar las cifras.
5. **Nada de `force push`** a una rama que el otro haya mirado. El historial de `main` no se
   reescribe nunca.

### Qué cubre cada revisión

Álvaro no puede auditar aislamiento multi-tenant en Spring Boot, y fingir que sí da una falsa
sensación de control. Su revisión cubre: que el comportamiento es el acordado, que los textos en
pantalla están bien, que nada contradice `PRODUCT.md` y que las cifras de tests no han bajado.

La parte técnica de las pull requests de backend la revisa además un agente con esta lista cerrada,
sacada de las reglas innegociables de `CLAUDE.md`:

- [ ] Ningún `findById` desnudo sobre una entidad que extiende `GestoriaScopedEntity`.
- [ ] Ninguna migración ya fusionada modificada.
- [ ] Nada que envíe algo a OVZ.net sin el clic del gestor.
- [ ] `ENCRYPTION_KEY` sin valor por defecto silencioso.
- [ ] Ningún secreto en el código, en un comando o en un fichero del repo.
- [ ] La IA no recibe el inventario de animales.

## 2. Propiedad de las zonas

| Zona | Dueño | Quien no es dueño |
| --- | --- | --- |
| `backend/**` | Antonio | avisa antes y abre PR pequeña |
| `frontend/src/features/**` (UI) | Álvaro | avisa antes |
| `frontend/src/shared/layout`, `shared/brand`, `DESIGN.md` | Álvaro | — |
| `frontend/src/shared/api`, `shared/auth` | Antonio | avisa antes |
| `frontend/vercel.json`, `frontend/package.json` | compartido | avisa antes |
| `CLAUDE.md`, `PRODUCT.md`, `ganera-prompts.md` | compartido | siempre avisa |
| `db/migration/**` | por rangos (ver 3) | nunca toca el rango del otro |

**Ficheros calientes.** Antes de tocar uno, avisar al otro: `MensajeEntranteService`,
`SecurityConfig`, `application.yml`, `application-clever.yml`, `CLAUDE.md`.

## 3. Migraciones de Flyway

La última migración fusionada es la **`V23`**. La siguiente libre es la `V24`.

- **Antonio se reserva de la `V24` a la `V39`. Álvaro, de la `V40` en adelante.** Cada uno numera
  dentro de su rango sin preguntar. Así dos ramas no pueden traer nunca la misma versión.
- Álvaro **avisa antes** de crear una, no para pedir permiso sino para que Antonio sepa qué tabla
  aparece.
- **Ninguna migración ya fusionada se modifica jamás.**

## 4. Tests

- Una pull request **nunca reduce el número de tests** ni debilita uno para ponerlo en verde.
- Si un cambio legítimo rompe un test que afirmaba sobre un detalle de implementación (una clase de
  CSS, por ejemplo), se reescribe el test para que afirme sobre lo que ve el usuario **y se dice en
  la pull request**.
- Cada tarea se cierra con las cifras: backend y frontend, enteros, en verde.
- Un test en rojo en `main` se avisa el mismo día. Mientras siga ahí, nadie puede distinguir «he
  roto algo» de «ya estaba roto».

## 5. El contrato escrito

- **`CLAUDE.md` se actualiza en la misma pull request que cambia el comportamiento.** Si no, el
  fichero que los dos usamos como contrato deja de ser verdad y los agentes trabajan con
  información falsa.
- Una decisión de producto o de infraestructura que solo vive en una conversación no existe. Se
  escribe en `CLAUDE.md` o en `ganera-prompts.md` el mismo día.
- **`progress.md` está en `.superpowers/sdd/progress.md`**, no en la raíz. Cada rama añade solo en
  su propia sección («Rama `feat/mistral`») para que los conflictos se resuelvan en un minuto.

## 6. Secretos

- Cada uno los pone en su terminal o en las variables del servidor. **Nunca en el código, en el
  chat, ni dentro de un comando.** Las claves compartidas van en un gestor de contraseñas común.
- **`ENCRYPTION_KEY` no se regenera.** Esa clave descifra las contraseñas de OVZ guardadas: si
  cambia entre entornos o alguien la regenera, **todas las credenciales guardadas quedan
  ilegibles**, y una cifra corrupta da un 500 en el detalle del ganadero y en `/ganaderos`. En
  cuanto haya una credencial real de un ganadero, esa clave es para siempre.
- El `.env` no se sube nunca. Comprobar el `.gitignore` antes de cada pull request que toque
  configuración.

## 7. Sincronización

**Diez minutos al día**: qué has fusionado, qué vas a tocar hoy y qué te bloquea.
