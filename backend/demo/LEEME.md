# Semilla demo (datos inventados)

Para ver Ganera **llena** en local: ganaderos, explotaciones, animales, contactos y trámites
en todos los estados útiles del piloto (WhatsApp, manual, aprobado, hecho en OVZ, descartado…).

## Arranque

Necesitas las variables habituales (`JWT_SECRET`, `ENCRYPTION_KEY`, base de datos) y el perfil
`demo`:

```bash
export SPRING_PROFILES_ACTIVE=demo
# … resto de env …
./mvnw spring-boot:run
```

O en un solo comando:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

La semilla es **idempotente**: si ya existe el usuario `demo@ganera.local`, no vuelve a crear nada.

## Login

| Campo | Valor |
|-------|--------|
| Email | `demo@ganera.local` |
| Contraseña | `demo123456` |

## Qué verás

- 4 ganaderos y 5 explotaciones (Extremadura inventada)
- Inventario de animales por explotación
- Contactos titulares/empleados (uno inactivo)
- Trámites: baja por muerte (WhatsApp), alta nacimiento, movimiento ambiguo, sin explotación,
  extracción fallida, sin trámite, alta manual, aprobado, hecho en OVZ, rechazado, descartado,
  pendiente de extracción
- Preferencias OVZ de ejemplo (Mérida)

## Seguridad

- `ganera.demo.sembrar` es `false` por defecto.
- El perfil `demo` **no** se usa en Clever/producción.
- No defines `GANERA_DEMO_*` en Clever (el binding relajado de Spring lo sobrescribiría).
