# MiniMundo Boutique

Tienda en línea de ropa infantil construida con microservicios Spring Boot, MongoDB y Thymeleaf, según el documento
del proyecto (112 historias de usuario, 5 roles).

| Pieza | Puerto local | Qué hace |
|---|---|---|
| `web-frontend` | 8080 | La tienda y los paneles de los 5 roles (Thymeleaf + Bootstrap) |
| `api-gateway` | 8090 | Única entrada a la API, valida el JWT y enruta |
| `ms-usuarios` | 8081 | Registro, login, recuperación de contraseña, usuarios, roles y auditoría |
| `ms-catalogo` | 8082 | Productos, variantes (SKU), categorías, colecciones, descuentos e imágenes (GridFS) |
| `ms-pedidos` | 8083 | Carrito, checkout con pago simulado, pedidos, tarifas de envío y reportes |
| `ms-logistica` | 8084 | Inventario, reservas, alistamiento, despacho y entregas del repartidor |
| `ms-atencion` | 8085 | PQRS, cambios y devoluciones |

Cada microservicio tiene su propia base de datos (`db_usuarios`, `db_catalogo`, `db_pedidos`, `db_logistica`,
`db_atencion`) y su documentación Swagger en `/swagger-ui.html`.

## Usuarios de prueba

Se crean solos al arrancar (variable `DEMO_DATA=true`, activa por defecto).

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | admin@minimundo.co | Admin12345 |
| Cliente | cliente@minimundo.co | Demo12345 |
| Asesor de servicio al cliente | asesor@minimundo.co | Demo12345 |
| Operario de catálogo e inventario | operario@minimundo.co | Demo12345 |
| Repartidor | repartidor@minimundo.co y repartidor2@minimundo.co | Demo12345 |

También hay 12 productos de ejemplo con fotos, 46 SKU con inventario y 9 ciudades con tarifa de envío.

## Tarjetas del pago simulado

| Número | Resultado |
|---|---|
| 4242 4242 4242 4242 (o cualquier otro número válido) | Aprobado |
| 4000 0000 0000 0002 | Rechazado por fondos insuficientes |
| 4000 0000 0000 0069 | Rechazado por la entidad financiera |

Cualquier fecha futura (por ejemplo 12/30) y cualquier código de 3 dígitos.

## Correo de recuperación de contraseña

Si configuras `MAIL_HOST`, `MAIL_USERNAME` y `MAIL_PASSWORD` en `ms-usuarios` (por ejemplo una cuenta de Gmail con
contraseña de aplicación, `MAIL_HOST=smtp.gmail.com`), el enlace llega por correo. Sin esas variables la plataforma
queda en modo demostración y muestra el enlace en pantalla.

## Ejecutar en tu computador

Con Docker Desktop:

```bash
docker compose up --build
```

Abre http://localhost:8080. La primera vez tarda unos minutos mientras Maven descarga dependencias.

Con Spring Tool Suite: `File > Import > Maven > Existing Maven Projects`, elige la carpeta del repositorio y
marca los 7 proyectos. Necesitas Java 21 y un MongoDB local en `localhost:27017` (o `docker compose up mongo`).
Arranca cada `*Application.java` con el perfil `local` (es el perfil por defecto).

## Pruebas

- `mvn verify` dentro de cada servicio corre las pruebas unitarias.
- `python e2e/flujo_api.py` recorre la API con los 5 roles (127 verificaciones).
- `python e2e/flujo_web.py` recorre las pantallas: compra, pago rechazado y aprobado, alistamiento, despacho,
  entrega, PQRS, inventario, productos, reportes y parámetros (102 verificaciones).

GitHub Actions corre todo esto en cada cambio (pestaña **Actions**) y deja capturas de las pantallas en la rama
`capturas`.

## Publicar en Render (plan gratuito) con MongoDB Atlas

### 1. MongoDB Atlas

1. Crea una cuenta en https://www.mongodb.com/cloud/atlas y un clúster **M0 (gratis)**.
2. En **Database Access** crea un usuario con contraseña (rol *Read and write to any database*).
3. En **Network Access** agrega `0.0.0.0/0` (Render no tiene IP fija en el plan gratuito).
4. En **Connect > Drivers** copia la cadena `mongodb+srv://usuario:clave@cluster0.xxxxx.mongodb.net/`.
5. Las 5 cadenas de conexión ya están en `render.yaml` (una base por microservicio) con `<db_password>` en lugar
   de la contraseña. Si usas otro clúster o usuario, cambia la parte `usuario@cluster` en ese archivo.

### 2. Render

1. Crea una cuenta en https://render.com entrando con GitHub.
2. **New > Blueprint**, elige este repositorio. Render lee `render.yaml` y propone 7 servicios gratuitos.
3. Pulsa **Deploy Blueprint**. Luego, en **Environment Groups > minimundo-comun**, agrega la variable
   `MONGODB_PASSWORD` con la contraseña del usuario de Atlas y pulsa **Save, rebuild, and deploy**. Así la clave no
   queda escrita en el repositorio.
4. Espera a que los 7 servicios queden en verde (la primera compilación tarda de 10 a 15 minutos).
5. Revisa las URL que Render asignó. Si alguna no es exactamente `https://minimundo-<servicio>.onrender.com`,
   corrígela en **Env Groups > minimundo-urls** y vuelve a desplegar.
6. La tienda queda en `https://minimundo-boutique.onrender.com`.

### 3. Despertador para la presentación

Los servicios gratuitos se duermen tras 15 minutos sin uso y tardan cerca de un minuto en despertar. El flujo
**Despertador de Render** los mantiene despiertos:

- Los lunes de 6:00 a.m. a 6:00 p.m. (hora de Colombia), automáticamente.
- Cuando quieras: **Actions > Despertador de Render > Run workflow** y eliges las horas (máximo 6).

Ábrelo unos 3 minutos antes de usar la plataforma. El plan gratuito da 750 horas al mes para todos los servicios
juntos; un lunes completo con los 7 servicios usa unas 84 horas. Para apagar el horario de los lunes crea la
variable del repositorio `DESPERTADOR_LUNES` con el valor `no` (**Settings > Secrets and variables > Actions >
Variables**). Si cambiaste alguna URL en Render, guarda la lista completa separada por espacios en la variable
`RENDER_URLS`.
