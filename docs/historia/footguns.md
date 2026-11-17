# Footguns (post-mortems)

Post-mortems largos movidos desde las notas de arquitectura de `CLAUDE.md`. La regla accionable de cada uno queda como una linea en `CLAUDE.md`; aqui esta el detalle.


## Lombok footgun

- **Lombok footgun**: this JDK/Maven combination breaks Lombok's annotation processing when
  `--release 21` is combined with implicit classpath-based processor discovery (silent
  "cannot find symbol" for `@Getter`/`@Setter`/`@Slf4j`-generated members). Fixed by declaring
  `annotationProcessorPaths` explicitly for Lombok in the `maven-compiler-plugin` config in
  `backend/pom.xml` — don't remove that block thinking it's redundant boilerplate.

## Twilio footgun

- **Twilio footgun (fixed)**: `TwilioWebhookController` used to build `new RequestValidator(authToken)`
  in its constructor — Twilio's SDK throws `IllegalArgumentException: Empty key` on a blank token,
  which meant the *entire app* refused to start whenever `TWILIO_AUTH_TOKEN` was unset (true for
  every environment at the time; Twilio has been wired to business logic since Prompt B1, and the
  token can still be blank — the webhook then fails closed). Fixed by constructing
  `RequestValidator` per-request instead of in the constructor. Don't move Stripe's or any other
  optional integration's client construction back into a constructor without checking it tolerates
  a blank credential — verified live by actually starting the app, not just by compiling (see
  `mvn spring-boot:run` note below).

## commons-io footgun

- **commons-io footgun (fixed)**: adding Apache POI (`poi`/`poi-ooxml` 5.3.0, for the Prompt 3d Excel
  importer) caused a runtime `NoSuchMethodError` on `BoundedInputStream.builder()` — Twilio's SDK
  transitively pulls `commons-io:2.14.0`, which Maven's nearest-wins mediation picked over the
  newer version POI actually needs (`BoundedInputStream.Builder` only exists from `commons-io`
  `2.16+`), and this only surfaces at runtime when the importer actually reads a file, never at
  `mvn compile`. Fixed by pinning `commons-io` to `2.16.1` as an explicit direct dependency in
  `backend/pom.xml` (a direct dependency always wins mediation over anything transitive, regardless
  of version). If any future dependency bump touches Twilio, POI, or commons-io, re-check this pin
  doesn't drift back out of range.
