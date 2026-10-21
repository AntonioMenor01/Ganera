# Certificados SOLO DE PRUEBA (migración a Supabase, T3, decisión S5)

Estos ficheros **no protegen nada**. Son una CA autofirmada y un certificado de servidor que solo sirven para que el
PostgreSQL embebido de los tests (`PostgresEmbebido`) hable SSL y el perfil `clever` se pruebe con
`sslmode=verify-full`, igual que contra Supabase. La clave privada está en el repositorio a propósito: es pública y no
vale para nada más. **Nunca** se usan fuera de los tests ni se copian a `src/main/resources`.

| Fichero | Qué es |
|---|---|
| `ca-PRUEBA-solo-tests.crt` | CA autofirmada de prueba (`CN=Ganera CA de PRUEBA solo tests`, `CA:TRUE`). Su clave no se guarda. |
| `servidor-PRUEBA-localhost.crt` | Certificado del servidor, firmado por esa CA. SAN **solo** `DNS:localhost` (sin IP): con `127.0.0.1` como host, `verify-full` falla, y eso se prueba. |
| `servidor-PRUEBA-localhost.key` | Clave privada del servidor (PKCS#8, sin cifrar). `PostgresEmbebido` la copia a un temporal con permisos `0600` (PostgreSQL lo exige en Linux). |
| `../supabase/ca-raiz.crt` | **Copia** de `ca-PRUEBA-solo-tests.crt`. En el classpath de los tests tapa a la CA real de Supabase de `src/main/resources/supabase/ca-raiz.crt` (como `application.yml`), así el perfil `clever` confía en la CA de prueba. |

Válidos 100 años (desde el 19/10/2026). RSA 2048, SHA-256.

## Cómo regenerarlos

Desde `backend/src/test/resources/ssl-pruebas/`, en Git Bash (su `openssl`). `MSYS_NO_PATHCONV=1` evita que Git Bash
convierta el `/O=…` de `-subj` en una ruta de Windows. La clave de la CA se genera en un temporal y se borra: si hay que
regenerar, se regeneran **los tres** ficheros y la copia `../supabase/ca-raiz.crt`.

```bash
export MSYS_NO_PATHCONV=1
T=$(cygpath -m "$(mktemp -d)")
printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost\n" > "$T/servidor.ext"

openssl req -x509 -newkey rsa:2048 -nodes -keyout "$T/ca.key" -out ca-PRUEBA-solo-tests.crt -days 36500 -sha256 \
  -subj "/O=Ganera PRUEBA/CN=Ganera CA de PRUEBA solo tests" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

openssl req -newkey rsa:2048 -nodes -keyout servidor-PRUEBA-localhost.key -out "$T/servidor.csr" \
  -subj "/O=Ganera PRUEBA/CN=localhost"

openssl x509 -req -in "$T/servidor.csr" -CA ca-PRUEBA-solo-tests.crt -CAkey "$T/ca.key" \
  -CAserial "$T/ca.srl" -CAcreateserial -out servidor-PRUEBA-localhost.crt -days 36500 -sha256 -extfile "$T/servidor.ext"

openssl verify -CAfile ca-PRUEBA-solo-tests.crt servidor-PRUEBA-localhost.crt   # servidor-PRUEBA-localhost.crt: OK
cp ca-PRUEBA-solo-tests.crt ../supabase/ca-raiz.crt
rm -rf "$T"
```

Sin `openssl`, el `keytool` de cualquier JDK 21+ sirve para lo mismo (después hay que pasar la clave a PEM PKCS#8,
por ejemplo con `openssl pkcs12`, así que en la práctica lo más corto es `openssl`):

```bash
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 36500 -dname "O=Ganera PRUEBA, CN=Ganera CA de PRUEBA solo tests" \
  -ext bc:c=ca:true -ext ku:c=keyCertSign,cRLSign -keystore ca.p12 -storetype PKCS12 -storepass prueba
keytool -genkeypair -alias servidor -keyalg RSA -keysize 2048 -validity 36500 -dname "O=Ganera PRUEBA, CN=localhost" \
  -keystore servidor.p12 -storetype PKCS12 -storepass prueba
keytool -certreq -alias servidor -keystore servidor.p12 -storepass prueba \
  | keytool -gencert -alias ca -keystore ca.p12 -storepass prueba -validity 36500 \
      -ext bc:c=ca:false -ext ku:c=digitalSignature,keyEncipherment -ext eku=serverAuth -ext san=dns:localhost -rfc \
  > servidor-PRUEBA-localhost.crt
keytool -exportcert -alias ca -keystore ca.p12 -storepass prueba -rfc > ca-PRUEBA-solo-tests.crt
# clave del servidor a PEM PKCS#8:
openssl pkcs12 -in servidor.p12 -nocerts -nodes -passin pass:prueba | openssl pkey -out servidor-PRUEBA-localhost.key
```

Después, `./mvnw clean test -Dtest='*PerfilClever*'` tiene que seguir en verde.
