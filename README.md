# Payment Service · Logitrack

Servicio Spring Boot, Java 25 y MySQL para pagar la tarifa de un envío mediante
Mercado Pago Checkout Pro (Preferences API). Usa Eureka, Config Server, JWT RS256
y base propia `payment_db`. No recibe ni almacena datos de tarjetas.

## Flujo

1. CLIENTE dueño del envío o ADMIN, con correo verificado, solicita
   `POST /api/v1/payments/shipments/{shipmentId}/checkout` sin importe en el cuerpo.
2. Se consulta el detalle privado de Shipment propagando el JWT. La tarifa se
   toma del backend y se fija en ARS. Solo se aceptan envíos PENDIENTE.
3. Se reserva una orden por envío en MySQL y se crea la preferencia. Solicitudes
   repetidas reutilizan el enlace; concurrencia y resultado incierto no crean
   nuevas preferencias automáticamente. El resultado incierto requiere revisión.
4. El comprador completa el checkout en Mercado Pago y vuelve al detalle.
   La URL de retorno nunca cambia el estado del pago.
5. `POST /api/v1/payments/webhooks/mercadopago?data.id=...` valida HMAC-SHA256
   y la coincidencia del ID del cuerpo. El aviso se confirma solo tras guardarlo.
6. Un conciliador consulta `/v1/payments/{id}`, comprueba importe, moneda,
   referencia, cuenta receptora y modo de prueba/producción; actualiza el estado.
   Los fallos se reintentan con espera creciente, hasta una hora entre intentos.
   Además consulta periódicamente pagos por referencia para recuperar avisos
   perdidos. Las órdenes pendientes se revisan cada minuto y las aprobadas cada
   cinco minutos, en lotes limitados de cinco órdenes.

`GET /api/v1/payments/shipments/{shipmentId}` muestra el pago al dueño o ADMIN.
El repartidor no puede consultar ni iniciar pagos. Los JWT necesitan la audiencia
`payment-service` además de `shipment-service`, para la consulta entre servicios.

## Estados y consistencia

- NOT_STARTED: sin orden local; PENDING: sin aprobación confirmada.
- APPROVED, REJECTED, CANCELLED, REFUNDED y CHARGED_BACK reflejan datos consultados
  al proveedor. CANCELLED admite reintento si el envío sigue pendiente.
- REVIEW_REQUIRED: importe/cuenta/moneda/modo incorrecto, devolución parcial o más
  de un pago aprobado. No se emiten reembolsos automáticamente.
- Las notificaciones se deduplican por ID del pago. La versión del recurso evita
  que un aviso viejo revierta un estado nuevo. Cada reintento vuelve a consultar
  al proveedor; no se confía en estados enviados en el cuerpo del Webhook.
- Un pago en proceso bloquea otro checkout hasta conocerse el resultado.
- Si la creación de la preferencia tuvo un resultado incierto, revisar en el panel
  del proveedor usando `external_reference` (UUID de la orden). No borrar la orden
  ni resetear el estado sin comprobar si existe una preferencia o un pago.

Esta primera integración registra y cobra el envío. Todavía no bloquea asignación
ni despacho por falta de pago; esa regla requiere una decisión de negocio y la
integración de confirmaciones con Shipment. Tampoco ejecuta devoluciones.

## Configuración privada

Copiar `.env.example` a un archivo privado e inyectarlo como variables de entorno
(el archivo no se carga automáticamente por Spring). Nunca subirlo a Git.

Para habilitar el sandbox se necesitan `PAYMENTS_ENABLED=true`,
`MERCADOPAGO_SANDBOX=true`, Access Token de prueba y clave de Webhooks. El Client
Secret de OAuth no es la clave del Webhook. Sin configuración, se rechaza iniciar
pagos con 503; no se simula un pago exitoso.

La URL HTTPS de `LOGITRACK_PUBLIC_URL` incluye `/logitrack` si se publica bajo ese
prefijo. Configurar esa misma base en Mercado Pago, en modo prueba, evento Pagos:
`{LOGITRACK_PUBLIC_URL}/api/v1/payments/webhooks/mercadopago`.
Un túnel temporal obliga a actualizar esa configuración cuando cambia de URL.

En frontend, compilar con `NEXT_PUBLIC_PAYMENTS_ENABLED=true` para habilitar el
panel. Las claves y el token siempre quedan en el backend. Tras agregar la nueva
audiencia, iniciar sesión nuevamente para obtener el JWT actualizado.

## Ejecutar y comprobar

Desde deploy, con variables privadas ya cargadas:

```bash
docker compose --env-file /ruta/privada/runtime.env -f docker-compose.yml -f docker-compose.payments.yml up -d --build
```

El overlay ejecuta `scripts/init-payment-db.sh` en un contenedor de inicialización
antes de arrancar el servicio: crea la base y el usuario dedicados si faltan,
tanto con un volumen nuevo como con uno existente. No borra volúmenes ni altera
las bases de los demás servicios.

Puerto de negocio: 8085; salud y Prometheus: 18085, red privada sin puertos
publicados. La entrada externa es exclusivamente Gateway.

```bash
./mvnw verify
```

Las pruebas usan H2 aislado y un proveedor simulado; no necesitan credenciales
ni generan cargos. Incluyen firma alterada, propiedad del envío, duplicados,
respuestas inciertas, importe incorrecto, reembolsos, estados fuera de orden y
reintentos. El flujo real de sandbox debe comprobarse además con credenciales
válidas y tarjetas de prueba de Mercado Pago.

## Referencias

- [Crear preferencia](https://www.mercadopago.com.ar/developers/es/reference/online-payments/checkout-pro-preferences/create-preference/post)
- [Webhooks](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-preferences/additional-content/notifications/webhooks)
- [Compras de prueba](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-preferences/integration-test/test-purchases)
