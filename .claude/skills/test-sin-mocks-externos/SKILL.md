---
name: test-sin-mocks-externos
description: Patrón de testing de Ganera para lógica que depende de SDKs externos (Stripe, Twilio, Anthropic). Usa esto al escribir tests para servicios que llaman a integraciones externas.
---

No mockees la deserialización de SDKs externos (Stripe Event/Session/Invoice,
Twilio RequestValidator, etc.). En su lugar:
1. Separa la lógica de negocio pura (decisiones de estado, cálculos) en
   métodos package-private que reciben tipos simples/propios, no objetos del
   SDK — ver StripeWebhookService.calcularEstadoTrasActualizacion como ejemplo.
2. Testea esos métodos directamente sin ningún mock.
3. Los métodos que sí tocan el objeto del SDK (manejarXxx) quedan sin cobertura
   unitaria directa — se validan en el smoke test end-to-end, no aquí.