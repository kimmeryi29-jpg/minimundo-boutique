"""
Prueba de punta a punta de MiniMundo Boutique a través del api-gateway (HU-98: "el flujo completo de compra puede
probarse de punta a punta"). Recorre los cinco roles con los criterios de aceptación principales.

Uso:  python e2e/flujo_api.py [URL_GATEWAY]      (por defecto http://localhost:8090)
"""
import os
import sys
import time
import uuid

import requests

GW = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("GATEWAY_URL", "http://localhost:8090")).rstrip("/")
DEMO = os.environ.get("DEMO_PASSWORD", "Demo12345")
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@minimundo.co"), os.environ.get("ADMIN_PASSWORD", "Admin12345"))
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

resultados = []


def check(nombre, condicion, detalle=""):
    resultados.append((nombre, bool(condicion), detalle))
    estado = "OK  " if condicion else "FALLA"
    print(f"[{estado}] {nombre}" + (f" -> {detalle}" if detalle and not condicion else ""), flush=True)
    return condicion


def req(metodo, ruta, token=None, **kw):
    h = kw.pop("headers", {})
    if token:
        h["Authorization"] = "Bearer " + token
    return requests.request(metodo, GW + ruta, headers=h, timeout=90, **kw)


def login(email, password):
    r = req("POST", "/api/auth/login", json={"email": email, "password": password})
    if r.status_code != 200:
        print("Login fallido", email, r.status_code, r.text)
        return None
    return r.json()["token"]


def esperar_gateway():
    for _ in range(60):
        try:
            if requests.get(GW + "/api/productos", timeout=10).status_code == 200:
                return True
        except requests.RequestException:
            pass
        time.sleep(5)
    return False


def main():
    check("Gateway y catálogo responden", esperar_gateway())

    # ---------------- Seguridad del gateway (HU-14, HU-91, HU-92, HU-102)
    r = req("GET", "/api/pedidos/mis-pedidos")
    check("HU-92 sin token → 401", r.status_code == 401, r.text)
    check("HU-102 formato uniforme de error", "mensaje" in r.json() and "codigo" in r.json(), r.text)
    r = req("GET", "/api/pedidos/mis-pedidos", token="token.invalido.x")
    check("HU-92 token inválido → 401", r.status_code == 401, r.text)
    r = req("GET", "/api/productos/interno/variantes")
    check("Rutas internas no expuestas → 404", r.status_code == 404, r.text)
    r = req("GET", "/api/no-existe")
    check("HU-91 ruta sin servicio → 404", r.status_code == 404, r.text)

    # ---------------- Usuarios (HU-1, HU-3, HU-7, HU-8, HU-13)
    admin = login(*ADMIN)
    check("Login del administrador", admin)
    cli_demo = login("cliente@minimundo.co", DEMO)
    asesor = login("asesor@minimundo.co", DEMO)
    operario = login("operario@minimundo.co", DEMO)
    repartidor = login("repartidor@minimundo.co", DEMO)
    check("Usuarios demo de cada rol inician sesión", all([cli_demo, asesor, operario, repartidor]))

    correo = f"e2e.{uuid.uuid4().hex[:8]}@correo.com"
    r = req("POST", "/api/auth/registro", json={"nombre": "Cliente E2E", "email": correo, "password": "clave12345"})
    check("HU-1 registro de cliente", r.status_code == 201 and r.json()["rol"] == "CLIENTE", r.text)
    r = req("POST", "/api/auth/registro", json={"nombre": "Otro", "email": correo.upper(), "password": "clave12345"})
    check("HU-7 correo repetido (mayúsculas) → 409", r.status_code == 409, r.text)
    r = req("POST", "/api/auth/registro", json={"nombre": "", "email": "malo", "password": "123"})
    check("HU-1 validación → 400 con campos", r.status_code == 400 and r.json().get("campos"), r.text)
    r = req("POST", "/api/auth/login", json={"email": correo, "password": "incorrecta"})
    check("HU-3 credenciales incorrectas → 401", r.status_code == 401 and "incorrectos" in r.json()["mensaje"], r.text)
    cliente = login(correo, "clave12345")
    check("HU-2 login del cliente nuevo", cliente)

    r = req("GET", "/api/usuarios", token=cliente)
    check("HU-14 cliente en gestión de usuarios → 403", r.status_code == 403, r.text)
    r = req("POST", "/api/usuarios", token=admin, json={"nombre": "Repartidor E2E", "email": f"rep.{uuid.uuid4().hex[:6]}@minimundo.co",
                                                        "password": "clave12345", "rol": "REPARTIDOR"})
    check("HU-8 admin crea repartidor", r.status_code == 201 and r.json()["rol"] == "REPARTIDOR", r.text)
    r = req("GET", "/api/usuarios?rol=REPARTIDOR&activo=true", token=admin)
    check("HU-9 listado filtrado por rol", r.status_code == 200 and all(u["rol"] == "REPARTIDOR" for u in r.json()), r.text)

    r = req("PUT", "/api/usuarios/me", token=cliente, json={"nombre": "Cliente E2E", "telefono": "3000000000",
                                                             "direccion": {"direccion": "Calle 1 # 2-3", "barrio": "Centro",
                                                                           "ciudad": "Bucaramanga", "telefono": "3000000000"}})
    check("HU-5 actualizar perfil y dirección", r.status_code == 200 and r.json()["direccion"]["ciudad"] == "Bucaramanga", r.text)

    # ---------------- Catálogo (HU-16 a HU-30)
    r = req("GET", "/api/productos")
    productos = r.json()
    check("HU-27 catálogo público con productos", r.status_code == 200 and len(productos) >= 5, len(productos))
    check("HU-22 borradores no aparecen", all(p["estado"] == "PUBLICADO" for p in productos))
    r = req("GET", "/api/productos?q=VESTIDO")
    check("HU-28 búsqueda sin distinguir mayúsculas", r.status_code == 200 and len(r.json()) >= 1, r.text)
    r = req("GET", "/api/productos?genero=NINA&edad=A1_3")
    check("HU-27 filtros combinados", r.status_code == 200 and all(p["rangoEdad"] == "A1_3" for p in r.json()), r.text)
    r = req("GET", "/api/productos?q=zzzzzz")
    check("HU-27 filtro sin resultados → lista vacía", r.status_code == 200 and r.json() == [], r.text)
    vestido = next(p for p in productos if p["codigo"] == "VES-001")
    check("HU-26 precio con descuento vigente", vestido["precioFinal"] < vestido["precio"], vestido)

    r = req("GET", "/api/categorias?tipo=CATEGORIA")
    categoria = r.json()[0]["id"]
    nombre_cat = "Categoría E2E " + uuid.uuid4().hex[:5]
    r = req("POST", "/api/categorias", token=operario, json={"nombre": nombre_cat, "tipo": "CATEGORIA"})
    check("HU-21 crear categoría", r.status_code == 201, r.text)
    cat_e2e = r.json()["id"]
    r = req("POST", "/api/categorias", token=operario, json={"nombre": nombre_cat.upper(), "tipo": "CATEGORIA"})
    check("HU-21 nombre repetido → 409", r.status_code == 409, r.text)

    nuevo = {"nombre": "Medias antideslizantes E2E", "descripcion": "Prueba", "categoriaId": cat_e2e, "coleccionIds": [],
             "genero": "UNISEX", "rangoEdad": "M0_12", "precio": 12900, "variantes": [], "publicar": False}
    r = req("POST", "/api/productos", token=operario, json=nuevo)
    check("HU-24 sin variantes → 400", r.status_code == 400, r.text)
    nuevo["variantes"] = [{"talla": "6-9M", "color": "Blanco"}, {"talla": "9-12M", "color": "Blanco"}]
    r = req("POST", "/api/productos", token=operario, json=nuevo)
    check("HU-16/HU-22 crear producto en borrador", r.status_code == 201 and r.json()["estado"] == "BORRADOR", r.text)
    prod = r.json()
    check("HU-17 SKU con código-talla-color", prod["variantes"][0]["sku"].endswith("-69M-BLANCO"), prod["variantes"])
    r = req("DELETE", f"/api/categorias/{cat_e2e}", token=operario)
    check("HU-21 categoría con productos no se elimina → 409", r.status_code == 409, r.text)
    r = req("GET", f"/api/productos/{prod['id']}")
    check("HU-22 borrador no visible al cliente → 404", r.status_code == 404, r.text)
    with open(os.path.join(RAIZ, "ms-catalogo/src/main/resources/demo/bod-001-1.png"), "rb") as f:
        r = req("POST", f"/api/productos/{prod['id']}/imagenes", token=operario,
                files=[("archivos", ("foto.png", f.read(), "image/png"))])
    check("HU-18 carga de imagen PNG", r.status_code == 200 and len(r.json()["imagenes"]) == 1, r.text)
    r = req("POST", f"/api/productos/{prod['id']}/imagenes", token=operario,
            files=[("archivos", ("virus.exe", b"MZ....", "application/octet-stream"))])
    check("HU-18 formato no permitido → 400", r.status_code == 400, r.text)
    r = req("PATCH", f"/api/productos/{prod['id']}/publicar", token=operario)
    check("HU-22 publicar borrador", r.status_code == 200 and r.json()["estado"] == "PUBLICADO", r.text)
    img = prod_img = r.json()["imagenes"][0]
    r = req("GET", f"/api/imagenes/{img}")
    check("HU-18 imagen visible", r.status_code == 200 and r.headers["Content-Type"].startswith("image/png"))
    r = req("PUT", f"/api/productos/{prod['id']}/descuento", token=operario, json={"porcentaje": 10, "inicio": "2026-10-20", "fin": "2026-10-10"})
    check("HU-23 fin anterior al inicio → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/productos", token=cliente, json=nuevo)
    check("HU-14 cliente no crea productos → 403", r.status_code == 403, r.text)

    # ---------------- Inventario (HU-46 a HU-49)
    sku_nuevo = prod["variantes"][0]["sku"]
    r = req("POST", "/api/inventario/entradas", token=operario, json={"sku": sku_nuevo, "cantidad": 0})
    check("HU-46 cantidad cero → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/inventario/entradas", token=operario, json={"sku": "NO-EXISTE-1", "cantidad": 3})
    check("HU-46 SKU inexistente → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/inventario/entradas", token=operario, json={"sku": sku_nuevo, "cantidad": 3})
    check("HU-46 entrada de mercancía", r.status_code == 201 and r.json()["stock"] == 3, r.text)
    r = req("POST", "/api/inventario/ajustes", token=operario, json={"sku": sku_nuevo, "cantidad": -10, "motivo": "Conteo físico"})
    check("HU-48 ajuste que deja stock negativo → 409", r.status_code == 409, r.text)
    r = req("POST", "/api/inventario/ajustes", token=operario, json={"sku": sku_nuevo, "cantidad": -1, "motivo": "Prenda dañada"})
    check("HU-48 ajuste con motivo", r.status_code == 201 and r.json()["stock"] == 2, r.text)
    r = req("GET", f"/api/inventario/{sku_nuevo}/movimientos", token=operario)
    check("HU-56 historial de movimientos", r.status_code == 200 and len(r.json()) == 2, r.text)
    r = req("GET", "/api/inventario?filtro=bajo", token=operario)
    check("HU-49/HU-60 alertas de stock bajo", r.status_code == 200 and r.json()["alertas"] >= 1, r.text[:300])
    r = req("GET", "/api/inventario?filtro=agotado", token=operario)
    check("HU-60 filtro de agotadas", r.status_code == 200 and all(i["disponible"] == 0 for i in r.json()["items"]), r.text[:300])

    # ---------------- Compra (HU-29 a HU-41)
    body = next(p for p in productos if p["codigo"] == "BOD-001")
    detalle = req("GET", f"/api/productos/{body['id']}").json()
    sku = detalle["variantes"][0]["sku"]
    disp = req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku]
    check("HU-29 disponibilidad por variante", disp > 0, disp)
    agotado = "VES-002-6-ROJO"
    r = req("POST", "/api/carrito/items", token=cliente, json={"sku": agotado, "cantidad": 1})
    check("HU-30 variante agotada no se agrega", r.status_code == 409 and "Agotado" in r.json()["mensaje"], r.text)
    r = req("POST", "/api/carrito/items", token=cliente, json={"sku": sku, "cantidad": disp + 1})
    check("HU-35 más que el disponible → 409", r.status_code == 409, r.text)
    r = req("POST", "/api/carrito/items", token=cliente, json={"sku": sku, "cantidad": 1})
    r = req("POST", "/api/carrito/items", token=cliente, json={"sku": sku, "cantidad": 1})
    check("HU-31 mismo SKU suma cantidades", r.status_code == 200 and r.json()["items"][0]["cantidad"] == 2, r.text)
    r = req("POST", "/api/carrito/items", token=cliente, json={"sku": vestido_sku(vestido), "cantidad": 1})
    r = req("PUT", f"/api/carrito/items/{sku}", token=cliente, json={"cantidad": 0})
    check("HU-32 cantidad menor a 1 → 400", r.status_code == 400, r.text)
    r = req("GET", "/api/carrito", token=cliente)
    c = r.json()
    check("HU-34 envío pendiente sin ciudad", c["envioPendiente"] is True, c)
    r = req("GET", "/api/carrito?ciudad=Bucaramanga", token=cliente)
    c = r.json()
    check("HU-34/HU-38 total = subtotal − descuentos + envío", c["total"] == c["subtotal"] - c["descuentos"] + c["envio"], c)
    check("HU-26 descuento aplicado en el carrito", c["descuentos"] > 0, c)
    r = req("GET", "/api/carrito?ciudad=Ciudad Inventada", token=cliente)
    check("HU-38 ciudad sin cobertura", r.json()["sinCobertura"] is True, r.text)

    direccion = {"nombreRecibe": "Cliente E2E", "direccion": "Calle 1 # 2-3", "barrio": "Centro", "ciudad": "Bucaramanga",
                 "telefono": "3000000000"}
    pago_malo = {"titular": "Cliente E2E", "numeroTarjeta": "4000 0000 0000 0002", "vencimiento": "12/30", "cvv": "123"}
    pago_ok = dict(pago_malo, numeroTarjeta="4242 4242 4242 4242")
    antes = req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku]
    r = req("POST", "/api/pedidos", token=cliente, json={"direccion": direccion, "pago": pago_malo})
    check("HU-40 pago rechazado → 402 con motivo", r.status_code == 402 and "Fondos" in r.json()["mensaje"], r.text)
    despues = req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku]
    check("HU-40/HU-55 la reserva se libera tras el rechazo", antes == despues, (antes, despues))
    check("HU-40 el carrito se conserva", len(req("GET", "/api/carrito", token=cliente).json()["items"]) == 2)
    r = req("POST", "/api/pedidos", token=cliente, json={"direccion": dict(direccion, ciudad="Ciudad Inventada"), "pago": pago_ok})
    check("HU-38 checkout sin cobertura → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/pedidos", token=cliente, json={"direccion": direccion, "pago": pago_ok})
    check("HU-39 pago aprobado → PAGADO", r.status_code == 201 and r.json()["estado"] == "PAGADO", r.text)
    pedido = r.json()
    check("HU-41 código único de pedido", pedido["codigo"].startswith("MM-"), pedido.get("codigo"))
    check("HU-54 stock reservado al confirmar", req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku] == antes - 2)
    check("HU-39 carrito vacío tras la compra", req("GET", "/api/carrito", token=cliente).json()["items"] == [])
    r = req("GET", "/api/pedidos/mis-pedidos", token=cliente)
    check("HU-42 mis pedidos", r.status_code == 200 and r.json()[0]["codigo"] == pedido["codigo"], r.text[:200])
    r = req("GET", f"/api/pedidos/{pedido['id']}", token=cli_demo)
    check("HU-42 un cliente no ve pedidos ajenos → 403", r.status_code == 403, r.text)

    # Cancelación de un segundo pedido (HU-44)
    req("POST", "/api/carrito/items", token=cliente, json={"sku": sku, "cantidad": 1})
    p2 = req("POST", "/api/pedidos", token=cliente, json={"direccion": direccion, "pago": pago_ok}).json()
    antes_c = req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku]
    r = req("PATCH", f"/api/pedidos/{p2['id']}/cancelar", token=cliente)
    check("HU-44 cancelar pedido PAGADO", r.status_code == 200 and r.json()["estado"] == "CANCELADO", r.text)
    check("HU-44 la reserva se libera al cancelar", req("GET", f"/api/inventario/disponibilidad?skus={sku}").json()[sku] == antes_c + 1)

    # ---------------- Alistamiento y despacho (HU-50 a HU-57)
    r = req("GET", "/api/pedidos?estado=PAGADO", token=operario)
    check("HU-50 cola de pedidos pagados", any(p["id"] == pedido["id"] for p in r.json()), r.text[:300])
    r = req("POST", "/api/envios", token=operario, json={"pedidoId": pedido["id"]})
    check("HU-51 tomar pedido → EN_ALISTAMIENTO", r.status_code == 201, r.text)
    envio = r.json()
    r = req("POST", "/api/envios", token=operario, json={"pedidoId": pedido["id"]})
    check("HU-51 pedido ya tomado → 409", r.status_code == 409, r.text)
    r = req("PATCH", f"/api/envios/{envio['id']}/despacho", token=operario, json={"transportadora": "Servientrega", "guia": "GU-1"})
    check("HU-52 sin verificar no se despacha → 409", r.status_code == 409, r.text)
    r = req("PATCH", f"/api/envios/{envio['id']}/alistamiento", token=operario)
    check("HU-52 confirmar con ítems sin verificar → 409", r.status_code == 409, r.text)
    for item in envio["items"]:
        r = req("PATCH", f"/api/envios/{envio['id']}/verificacion", token=operario, json={"sku": item["sku"], "cantidad": item["cantidad"]})
    check("HU-52 verificación de ítems", r.status_code == 200 and all(i["verificado"] for i in r.json()["items"]), r.text)
    r = req("PATCH", f"/api/envios/{envio['id']}/alistamiento", token=operario)
    check("HU-52 confirmar alistamiento", r.status_code == 200 and r.json()["alistamientoConfirmado"], r.text)
    r = req("PATCH", f"/api/envios/{envio['id']}/despacho", token=operario, json={"transportadora": "", "guia": ""})
    check("HU-53 transportadora/guía vacías → 400", r.status_code == 400, r.text)
    stock_antes = req("GET", f"/api/inventario?sku={sku}", token=operario).json()["items"][0]["stock"]
    r = req("PATCH", f"/api/envios/{envio['id']}/despacho", token=operario, json={"transportadora": "Reparto MiniMundo", "guia": "MM-GU-001"})
    check("HU-53 despacho con guía", r.status_code == 200 and r.json()["estado"] == "DESPACHADO", r.text)
    stock_despues = req("GET", f"/api/inventario?sku={sku}", token=operario).json()["items"][0]["stock"]
    check("HU-54 stock descontado al despachar", stock_despues == stock_antes - 2, (stock_antes, stock_despues))
    r = req("PATCH", f"/api/envios/{envio['id']}/despacho", token=operario, json={"transportadora": "X", "guia": "Y"})
    check("HU-57 despachar dos veces → 409", r.status_code == 409, r.text)
    r = req("GET", f"/api/pedidos/{pedido['id']}", token=cliente)
    check("HU-53 el cliente ve la guía", r.json()["guia"] == "MM-GU-001" and r.json()["repartidorNombre"] is None, r.text[:300])
    r = req("PATCH", f"/api/pedidos/{pedido['id']}/cancelar", token=cliente)
    check("HU-44 pedido despachado no se cancela → 409", r.status_code == 409, r.text)

    # ---------------- Reparto (HU-106 a HU-112, HU-58)
    reps = req("GET", "/api/usuarios/repartidores", token=operario).json()
    jhon = next(u for u in reps if u["email"] == "repartidor@minimundo.co")
    sergio = next(u for u in reps if u["email"] == "repartidor2@minimundo.co")
    r = req("PATCH", f"/api/envios/{envio['id']}/repartidor", token=operario, json={"repartidorId": jhon["id"]})
    check("HU-106 asignar repartidor", r.status_code == 200 and r.json()["repartidorNombre"] == jhon["nombre"], r.text)
    r = req("GET", "/api/entregas/mis-entregas", token=repartidor)
    check("HU-107 mis entregas del repartidor", any(e["id"] == envio["id"] for e in r.json()), r.text[:300])
    rep2 = login("repartidor2@minimundo.co", DEMO)
    r = req("PATCH", f"/api/entregas/{envio['id']}/en-ruta", token=rep2)
    check("HU-112 otro repartidor → 403", r.status_code == 403, r.text)
    r = req("PATCH", f"/api/entregas/{envio['id']}/en-ruta", token=operario)
    check("HU-112 otro rol no gestiona entregas → 403", r.status_code == 403, r.text)
    r = req("PATCH", f"/api/entregas/{envio['id']}/en-ruta", token=repartidor)
    check("HU-108 marcar en camino → EN_RUTA", r.status_code == 200 and r.json()["estado"] == "EN_RUTA", r.text)
    r = req("GET", f"/api/pedidos/{pedido['id']}", token=cliente)
    check("HU-111 el cliente ve 'En camino' y el repartidor", r.json()["estado"] == "EN_RUTA" and r.json()["repartidorNombre"] == jhon["nombre"], r.text[:300])
    r = req("PATCH", f"/api/entregas/{envio['id']}/fallida", token=repartidor, json={"motivo": ""})
    check("HU-109 fallida sin motivo → 400", r.status_code == 400, r.text)
    r = req("PATCH", f"/api/entregas/{envio['id']}/fallida", token=repartidor, json={"motivo": "CLIENTE_AUSENTE"})
    check("HU-109 entrega fallida vuelve a DESPACHADO", r.status_code == 200 and r.json()["estado"] == "DESPACHADO", r.text)
    r = req("GET", "/api/envios?fallidas=true", token=operario)
    check("HU-109 el operario ve la entrega fallida", any(e["id"] == envio["id"] for e in r.json()), r.text[:300])
    r = req("PATCH", f"/api/envios/{envio['id']}/repartidor", token=operario, json={"repartidorId": jhon["id"]})
    req("PATCH", f"/api/entregas/{envio['id']}/en-ruta", token=repartidor)
    r = req("PATCH", f"/api/entregas/{envio['id']}/entrega", token=repartidor)
    check("HU-58 registrar entrega → ENTREGADO", r.status_code == 200 and r.json()["estado"] == "ENTREGADO", r.text)
    r = req("GET", f"/api/pedidos/{pedido['id']}", token=cliente)
    check("HU-111 el cliente ve fecha de entrega", r.json()["estado"] == "ENTREGADO" and r.json()["fechaEntrega"], r.text[:300])
    r = req("GET", "/api/entregas/historial", token=repartidor)
    h = r.json()
    check("HU-110 historial con entregado y fallido", {"ENTREGADO", "FALLIDO"} <= {x["resultado"] for x in h}, h[:3])
    r = req("GET", f"/api/pedidos/{pedido['id']}", token=admin)
    estados = [x["estado"] for x in r.json()["historialEstados"]]
    check("HU-87 historial de estados completo", estados[-1] == "ENTREGADO" and "EN_ALISTAMIENTO" in estados and "EN_RUTA" in estados, estados)

    # ---------------- Atención (HU-61 a HU-75)
    r = req("POST", "/api/pqrs", token=cliente, json={"tipo": "RECLAMO", "descripcion": "", "pedidoId": pedido["id"]})
    check("HU-61 sin descripción → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/pqrs", token=cli_demo, json={"tipo": "QUEJA", "descripcion": "x", "pedidoId": pedido["id"]})
    check("HU-62 pedido ajeno no se asocia → 403", r.status_code == 403, r.text)
    r = req("POST", "/api/pqrs", token=cliente, json={"tipo": "RECLAMO", "descripcion": "Llegó con una mancha", "pedidoId": pedido["id"]})
    check("HU-61/HU-62 radicar PQRS asociada al pedido", r.status_code == 201 and r.json()["estado"] == "RADICADA", r.text)
    pq = r.json()
    r = req("GET", "/api/pqrs", token=cliente)
    check("HU-68 cliente en la bandeja → 403", r.status_code == 403, r.text)
    r = req("GET", "/api/pqrs?estado=RADICADA", token=asesor)
    check("HU-68 bandeja del asesor", any(x["id"] == pq["id"] for x in r.json()), r.text[:300])
    r = req("PATCH", f"/api/pqrs/{pq['id']}/tomar", token=asesor)
    check("HU-69 tomar PQRS → EN_GESTION", r.status_code == 200 and r.json()["estado"] == "EN_GESTION", r.text)
    r = req("PATCH", f"/api/pqrs/{pq['id']}", token=asesor, json={"respuesta": ""})
    check("HU-70 respuesta vacía → 400", r.status_code == 400, r.text)
    r = req("PATCH", f"/api/pqrs/{pq['id']}", token=asesor, json={"respuesta": "Te enviamos una prenda nueva."})
    check("HU-70 responder y cerrar", r.status_code == 200 and r.json()["estado"] == "CERRADA", r.text)
    r = req("GET", "/api/pqrs/mias", token=cliente)
    check("HU-63 el cliente ve la respuesta", r.json()[0]["respuesta"], r.text[:300])
    check("HU-75 historial de la PQRS", len(r.json()[0]["historial"]) == 3, r.json()[0]["historial"])

    item = pedido["items"][0]
    r = req("POST", "/api/devoluciones", token=cliente, json={"tipo": "DEVOLUCION", "pedidoId": pedido["id"], "sku": item["sku"], "cantidad": 1, "motivo": ""})
    check("HU-65 devolución sin motivo → 400", r.status_code == 400, r.text)
    r = req("POST", "/api/devoluciones", token=cliente, json={"tipo": "DEVOLUCION", "pedidoId": pedido["id"], "sku": item["sku"], "cantidad": 1, "motivo": "No era lo esperado"})
    check("HU-65 solicitud de devolución PENDIENTE", r.status_code == 201 and r.json()["estado"] == "PENDIENTE", r.text)
    sol = r.json()
    r = req("PATCH", f"/api/devoluciones/{sol['id']}/decision", token=asesor, json={"aprobar": False, "motivo": ""})
    check("HU-73 rechazo sin motivo → 400", r.status_code == 400, r.text)
    r = req("PATCH", f"/api/devoluciones/{sol['id']}/decision", token=asesor, json={"aprobar": True})
    check("HU-72 aprobar solicitud", r.status_code == 200 and r.json()["estado"] == "APROBADA", r.text)
    r = req("PATCH", f"/api/devoluciones/{sol['id']}/decision", token=asesor, json={"aprobar": True})
    check("HU-72 ya decidida → 409", r.status_code == 409, r.text)
    stock_r = req("GET", f"/api/inventario?sku={item['sku']}", token=operario).json()["items"][0]["stock"]
    r = req("POST", "/api/inventario/reingresos", token=operario, json={"solicitudId": sol["id"]})
    check("HU-59 reingreso de la devolución", r.status_code == 201 and r.json()["stock"] == stock_r + 1, r.text)
    r = req("POST", "/api/inventario/reingresos", token=operario, json={"solicitudId": sol["id"]})
    check("HU-59 reingreso duplicado → 409", r.status_code == 409, r.text)
    r = req("GET", "/api/usuarios/clientes?q=E2E", token=asesor)
    check("HU-71 el asesor busca clientes", r.status_code == 200 and len(r.json()) >= 1, r.text[:200])

    # ---------------- Administración (HU-15, HU-45, HU-76 a HU-90)
    r = req("GET", "/api/reportes/resumen", token=admin)
    res = r.json()
    check("HU-76 panel con ventas del periodo", r.status_code == 200 and res["ventasTotal"] > 0, r.text[:300])
    check("HU-77 suma por estados = total", sum(x["cantidad"] for x in res["porEstado"]) == res["totalPedidos"], res["porEstado"])
    check("HU-78 productos más vendidos", len(res["masVendidos"]) >= 1, res["masVendidos"])
    check("HU-88 tiempo promedio pago→despacho", res["tiempoPromedioDespachoHoras"] is not None, res)
    r = req("GET", "/api/reportes/resumen?desde=2026-10-20&hasta=2026-10-10", token=admin)
    check("HU-79 rango inválido → 400", r.status_code == 400, r.text)
    r = req("GET", "/api/reportes/resumen?desde=2020-01-01&hasta=2020-01-31", token=admin)
    check("HU-90 periodo sin datos", r.json()["sinDatos"] is True, r.text[:200])
    r = req("GET", "/api/reportes/resumen", token=cliente)
    check("HU-76 cliente en el panel → 403", r.status_code == 403, r.text)
    r = req("GET", "/api/reportes/ventas/exportar", token=admin)
    check("HU-86 exportar ventas CSV", r.status_code == 200 and "codigo,cliente" in r.text, r.text[:200])
    r = req("GET", "/api/reportes/ventas/exportar?desde=2020-01-01&hasta=2020-01-31", token=admin)
    check("HU-86 sin datos no genera archivo", r.status_code == 404, r.text)
    r = req("GET", "/api/inventario/exportar", token=admin)
    check("HU-86 exportar inventario CSV", r.status_code == 200 and "sku,producto" in r.text, r.text[:200])
    r = req("GET", "/api/reportes/sin-ventas?dias=30", token=admin)
    check("HU-80 productos sin ventas", r.status_code == 200, r.text[:200])
    r = req("GET", "/api/pqrs/reporte", token=admin)
    check("HU-85 reporte de PQRS", r.status_code == 200 and r.json()["total"] >= 1, r.text[:200])
    r = req("POST", "/api/tarifas-envio", token=admin, json={"ciudad": "Ciudad E2E " + uuid.uuid4().hex[:4], "costo": -1})
    check("HU-81 costo negativo → 400", r.status_code == 400, r.text)
    r = req("PUT", "/api/inventario/parametros", token=admin, json={"stockMinimoDefecto": 4})
    check("HU-82 stock mínimo por defecto", r.status_code == 200, r.text)
    r = req("PUT", "/api/atencion/parametros", token=admin, json={"plazoCambiosDias": 0, "diasVencimientoPqrs": 5})
    check("HU-83 plazo inválido → 400", r.status_code == 400, r.text)
    r = req("PUT", "/api/atencion/parametros", token=admin, json={"plazoCambiosDias": 30, "diasVencimientoPqrs": 5})
    check("HU-83/HU-84 parámetros de atención", r.status_code == 200, r.text)
    r = req("GET", "/api/auditoria", token=admin)
    tipos = {e["tipo"] for e in r.json()}
    check("HU-15 auditoría con logins fallidos y exitosos", {"LOGIN_EXITOSO", "LOGIN_FALLIDO"} <= tipos, tipos)

    # HU-13 bloqueo tras 5 intentos
    for _ in range(5):
        req("POST", "/api/auth/login", json={"email": correo, "password": "mala"})
    r = req("POST", "/api/auth/login", json={"email": correo, "password": "clave12345"})
    check("HU-13 cuenta bloqueada aun con clave correcta", r.status_code == 401 and "bloqueada" in r.json()["mensaje"], r.text)

    fallas = [r for r in resultados if not r[1]]
    print(f"\nResultado: {len(resultados) - len(fallas)}/{len(resultados)} verificaciones correctas")
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::notice title=E2E::{len(resultados) - len(fallas)}/{len(resultados)} verificaciones correctas")
        for nombre, _, detalle in fallas[:40]:
            print(f"::error title=E2E falla::{nombre} -> {str(detalle)[:400]}")
    return 1 if fallas else 0


def vestido_sku(vestido):
    d = req("GET", f"/api/productos/{vestido['id']}").json()
    return d["variantes"][0]["sku"]


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        if os.environ.get("GITHUB_ACTIONS"):
            ok = len([r for r in resultados if r[1]])
            print(f"::notice title=E2E::{ok}/{len(resultados)} verificaciones correctas antes del error")
            for nombre, _, detalle in [r for r in resultados if not r[1]][:30]:
                print(f"::error title=E2E falla::{nombre} -> {str(detalle)[:400]}")
            print(f"::error title=E2E excepción::{type(e).__name__}: {str(e)[:500]}")
        sys.exit(1)
