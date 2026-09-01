## Objetivo del bloque

Entregar en una sola rama y un único PR la épica **“Registro de visitas”**, limitada a `spring-petclinic-visits-service`, de modo que:

- `POST owners/*/pets/{petId}/visits` rechace con HTTP 400 las visitas cuyo día sea posterior al día actual en `America/Mexico_City`.
- El rechazo conserve el error estándar de Spring Boot e incluya exactamente `la fecha de la visita no puede ser posterior a hoy`.
- Se mantenga el comportamiento actual para fechas de hoy, fechas pasadas y solicitudes sin `date`.
- No se modifiquen `spring-petclinic-api-gateway`, `VisitsServiceClient`, la interfaz web, otros servicios ni los contratos de consulta.
- Las visitas históricas futuras sigan siendo consultables y no se actualicen ni validen durante su lectura.

El bloque no tiene trabajo frontend ni mock visual aprobado: ambas historias son **sin UI**. La validación e2e se realizará contra la frontera HTTP real de `spring-petclinic-visits-service`, con persistencia de prueba.

Criterio de cierre del PR:

- Una prueba automatizada como mínimo por cada criterio de aceptación.
- Cobertura de líneas y ramas ≥90% sobre las clases backend nuevas o modificadas.
- Lado frontend: no aplica por alcance explícito; no se crearán componentes ni pruebas UI artificiales.
- Suite existente del módulo sin regresiones, separando y documentando las 3 pruebas ya conocidas en cuarentena si afectan la ejecución.

## Contratos y modelos compartidos

**Contrato HTTP existente que se conserva**

- Alta: `POST owners/*/pets/{petId}/visits`, implementado actualmente por `VisitResource`.
- Consulta por mascota: se conserva el mapping ya definido en `VisitResource`; no se renombra ni cambia su payload.
- Consulta agregada: `pets/visits`, sin cambios funcionales.
- Modelo de entrada y persistencia: reutilizar la entidad/modelo `Visit` existente y su tipo actual de `date`. No introducir un DTO incompatible ni cambiar el formato JSON vigente.
- `petId` continúa obteniéndose del path y asignándose como lo hace hoy `VisitResource`.

**Nueva regla contractual**

1. La comparación se hace por **día calendario**, no por instante.
2. La zona de negocio es una constante explícita:
   `ZoneId.of("America/Mexico_City")`.
3. El día de la visita se obtiene usando esa misma zona a partir del valor temporal recibido, respetando la deserialización vigente de `Visit.date`.
4. Día menor o igual a hoy: válido.
5. Día posterior a hoy: inválido solamente durante el alta.
6. `date` ausente: no se rechaza y conserva la asignación predeterminada actual; no se reemplazará el mecanismo existente basado en `new Date()`.
7. La lectura y carga desde repositorio no ejecutan esta validación.

Antes de implementar se debe caracterizar con una prueba el formato de fecha actualmente aceptado por Jackson, incluyendo fecha sola y fecha con hora. No se cambiará el tipo de `Visit.date` para resolver la regla.

**Contrato de error**

Para una fecha futura:

- Estado: HTTP 400.
- Cuerpo: estructura estándar producida por Spring Boot 2.6.7, sin `@ControllerAdvice` ni envelope propio.
- Debe contener como mínimo los campos estándar disponibles en el servicio (`status`, `error`, `message`, `path` y/o `timestamp`, según la configuración existente).
- `message` debe contener exactamente:
  `la fecha de la visita no puede ser posterior a hoy`.

La implementación preferente es una excepción específica anotada con `@ResponseStatus(HttpStatus.BAD_REQUEST)`, por ejemplo `FutureVisitDateException`, sin capturarla en consultas ni en repositorio. Si la configuración actual de Spring Boot oculta `message`, se habilitará `server.error.include-message=always` únicamente en la configuración propia del servicio de visitas, después de demostrar la necesidad con una prueba de contrato. No se construirá manualmente un JSON de error.

**Interfaz interna para hacer determinista el tiempo**

Crear un colaborador acotado, por ejemplo `VisitDateValidator`, con:

- Constante de zona `America/Mexico_City`.
- Dependencia de `java.time.Clock`, compatible con Java 8.
- Reloj productivo configurado para la zona de Ciudad de México.
- Reloj fijo en pruebas para cubrir hoy, ayer, mañana y cambios de zona del servidor.

No se añade ninguna dependencia externa: se utilizarán `Clock`, `Instant`, `LocalDate` y `ZoneId` de Java 8.

## Orden de construcción y porqué

1. **Caracterizar y fijar primero el contrato HTTP existente.**
   - Confirmar mappings exactos de `VisitResource`, formato JSON de `Visit.date`, asignación predeterminada y forma actual del error estándar.
   - Añadir primero pruebas fallidas para fecha futura, hoy, fecha pasada y ausencia de fecha.
   - Esto evita implementar una regla correcta sobre una interpretación incorrecta del contrato temporal del legado.

2. **HU-1: implementar el contrato de validación en la frontera de alta.**
   - Crear el validador temporal y la excepción HTTP.
   - Integrarlos solamente en el método POST de `VisitResource`, inmediatamente antes de guardar.
   - No colocar la regla en getters de `Visit`, listeners JPA/Mongo, repositorio ni consultas, porque eso podría invalidar registros históricos.

3. **HU-2: consumir el contrato ya estabilizado mediante pruebas e2e de regresión.**
   - Ejecutar altas y consultas a través de HTTP con contexto Spring y persistencia de prueba.
   - Insertar directamente una visita histórica futura en el repositorio para demostrar que las lecturas no aplican la nueva regla.
   - Verificar también `pets/visits` sin modificar gateway ni frontend.

El orden es obligatorio porque HU-2 depende del contrato de alta y error definido por HU-1. Implementar primero las pruebas e2e agregadas obligaría a codificar supuestos sobre el payload, la zona y el error antes de estabilizar la API.

## Por historia (enfoque y archivos)

**HU-1 — Validar fecha futura al registrar una visita**

Enfoque:

1. Inspeccionar `VisitResource`, `Visit`, el repositorio de visitas y las pruebas existentes del módulo.
2. Añadir pruebas de contrato HTTP antes del código productivo.
3. Crear `VisitDateValidator` usando `Clock` y `ZoneId.of("America/Mexico_City")`.
4. Crear `FutureVisitDateException` con HTTP 400 y el mensaje acordado.
5. Invocar el validador únicamente desde el handler de alta de `VisitResource`, antes de `save`.
6. Mantener intacta la asignación existente de fecha cuando `date` no viene informada.
7. No modificar los métodos GET ni introducir validación en la entidad o el repositorio.

Archivos previstos:

- Modificar:
  - `spring-petclinic-visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/VisitResource.java`
- Crear, siguiendo el package real comprobado durante la inspección:
  - `spring-petclinic-visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/VisitDateValidator.java`
  - `spring-petclinic-visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/FutureVisitDateException.java`
- Modificar o crear la prueba correspondiente a `VisitResource` en:
  - `spring-petclinic-visits-service/src/test/java/org/springframework/samples/petclinic/visits/web/VisitResourceTest.java`
- Solo si la prueba demuestra que Spring Boot oculta el mensaje:
  - la configuración local ya existente bajo `spring-petclinic-visits-service/src/main/resources/`; no crear un formato de error propio.
- Solo si el repo no tiene medición de cobertura:
  - `spring-petclinic-visits-service/pom.xml`, limitando el cambio a JaCoCo y sin tocar los `pom.xml` acoplados de otros servicios.

Pruebas mínimas, una por criterio:

1. POST con mañana respecto de un `Clock` fijo en Ciudad de México → HTTP 400.
2. El cuerpo del 400 mantiene el error estándar y contiene el mensaje exacto.
3. POST con el día de hoy y una hora distinta → se guarda.
4. POST con fecha anterior → se guarda con el comportamiento existente.
5. POST sin `date` → se guarda y recibe la fecha predeterminada vigente.
6. Cambiar `TimeZone.setDefault(...)` entre UTC y otra zona extrema no cambia la decisión para el mismo reloj de Ciudad de México; restaurar la zona original en `finally`/`@AfterEach`.
7. Una visita futura insertada directamente en el repositorio puede leerse; la validación no se ejecuta al consultar.

Añadir además una prueba de frontera alrededor de medianoche con reloj fijo para evitar pruebas dependientes de la hora real. No usar `LocalDate.now()` sin reloj dentro de las pruebas.

**HU-2 — Verificar compatibilidad del registro y consulta de visitas**

Enfoque:

- Crear o ampliar una prueba de integración HTTP con contexto completo del servicio y repositorio de prueba.
- Usar el mismo formato JSON que consume actualmente `VisitsServiceClient`; no crear otro contrato para facilitar las pruebas.
- Limpiar los datos de prueba entre escenarios.
- Para el histórico futuro, persistir directamente mediante el repositorio existente, no mediante POST, ya que el objetivo es probar compatibilidad de lectura.
- Probar las rutas de consulta que ya declara `VisitResource`, incluidas la consulta de una mascota y `pets/visits`.

Archivo previsto:

- Crear o ampliar, según la convención existente:
  - `spring-petclinic-visits-service/src/test/java/org/springframework/samples/petclinic/visits/web/VisitResourceIntegrationTest.java`
- Reutilizar:
  - `VisitResource`
  - `Visit`
  - el repositorio existente de visitas
  - la configuración de persistencia embebida ya usada por las pruebas del módulo
- No modificar:
  - `spring-petclinic-api-gateway`
  - `VisitsServiceClient`
  - archivos web
  - mappings de consulta
  - módulos de customers, vets, discovery o config server

Escenarios e2e mínimos:

1. POST de una visita con hoy → éxito y registro persistido.
2. POST de una visita pasada → éxito y registro persistido.
3. POST sin `date` → éxito y fecha predeterminada no nula/coherente con el comportamiento existente.
4. POST con mañana → HTTP 400, error estándar y mensaje exacto.
5. Después de las altas válidas, consulta por mascota → devuelve las visitas creadas y sus datos.
6. `pets/visits` → continúa respondiendo con el status, estructura y agrupación actuales.
7. Insertar una visita futura directamente en el repositorio, consultarla por HTTP y comprobar que:
   - permanece visible;
   - conserva su fecha;
   - no se actualiza ni elimina;
   - no provoca HTTP 400 en lectura.

Ejecución de cierre:

```bash
mvn -pl spring-petclinic-visits-service test
```

Si el reactor exige dependencias internas:

```bash
mvn -pl spring-petclinic-visits-service -am test
```

Generar y revisar el reporte JaCoCo del módulo. `VisitResource`, `VisitDateValidator` y `FutureVisitDateException` deben alcanzar conjuntamente ≥90% de líneas y ramas. No se considerarán como fallo del bloque las 3 pruebas previamente identificadas en cuarentena, pero deben quedar enumeradas en el PR y no ocultarse nuevas cuarentenas.

## Estimación y presupuesto

| HU | Talla | Esfuerzo estimado | Prioridad |
|---|---:|---:|---|
| HU-1 — Contrato, validación temporal y error HTTP | L | 2 jornadas-persona | P0 — habilita toda la épica |
| HU-2 — Compatibilidad e2e de altas y consultas | M | 1 jornada-persona | P1 — depende de HU-1 y bloquea el cierre |

**Esfuerzo total:** 3 jornadas-persona.

**Presupuesto del bloque:** `3 × costo/jornada del equipo`.  
Si el líder define el costo por jornada como `C`, el presupuesto es **`3C`**.

Supuestos de estimación:

- Java 1.8, Spring Boot 2.6.7 y la persistencia actual no se actualizan.
- El endpoint y el modelo `Visit` ya existen; no se incluye migración de datos.
- No hay cambios frontend, gateway ni despliegue de infraestructura.
- Las pruebas pueden reutilizar el soporte de integración y persistencia embebida existente.
- Se reserva esfuerzo en HU-1 por la semántica de zona horaria, el error estándar de Spring Boot y el riesgo de tocar `VisitResource`, archivo caliente con 11 cambios y 2 correcciones.
- Si no existe infraestructura de integración o cobertura en el módulo, deberá reestimarse antes de alterar POMs globales; no se tocarán automáticamente los POMs históricamente acoplados.

## Riesgos

- **Interpretación de `java.util.Date` y JSON:** una fecha sin hora puede deserializarse con una zona distinta y desplazarse de día al convertirla. Debe fijarse primero con pruebas el comportamiento real de Jackson y del modelo `Visit`.
- **Error estándar de Spring Boot 2.6.7:** `message` puede estar oculto por configuración. La solución debe seguir usando `BasicErrorController`/error estándar; habilitar inclusión del mensaje es preferible a crear un `@ControllerAdvice`.
- **Asignación predeterminada:** si `Visit.getDate()` asigna `new Date()` de manera diferida, el validador no debe sustituir ni duplicar esa lógica.
- **Archivo caliente:** `VisitResource.java` concentra correcciones históricas. El cambio debe ser mínimo y quedar cubierto tanto con pruebas web como de integración.
- **Validación demasiado profunda:** colocarla en entidad o repositorio rompería la consulta de visitas históricas futuras. Debe quedar exclusivamente en el handler POST.
- **Pruebas inestables en medianoche:** evitar la hora real mediante `Clock.fixed`; restaurar cualquier cambio de zona horaria global después de cada prueba.
- **Acoplamiento de POMs:** los POMs de visits, vets y gateway cambian juntos históricamente, pero el alcance no justifica tocarlos en conjunto. No hacer cambios de versiones ni formateos masivos.
- **Pruebas en cuarentena:** las 3 pruebas dependientes de infraestructura del cliente pueden impedir una ejecución completa del reactor. Ejecutar primero el módulo y documentar cualquier exclusión preexistente.
- **Gateway no probado dentro del bloque:** por restricción de alcance no se cambia ni levanta `spring-petclinic-api-gateway`; la compatibilidad se garantiza preservando el contrato HTTP que consume `VisitsServiceClient`.

## Sugerencia de ejecutores

- **HU-1 — Dev humano con apoyo del agente:** conviene revisión humana directa por la combinación de zona horaria, deserialización heredada, exposición del mensaje estándar de Spring Boot y modificación de `VisitResource`, un archivo con correcciones concentradas.
- **HU-2 — Agente autónomo:** adecuada para ejecución autónoma una vez aprobado y estabilizado el contrato de HU-1; consiste principalmente en escenarios HTTP/repository repetibles y verificaciones de regresión, con revisión humana del reporte de cobertura y de cualquier prueba en cuarentena.