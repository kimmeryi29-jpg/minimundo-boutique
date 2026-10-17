"""
Prueba de las pantallas de MiniMundo Boutique (web-frontend). Inicia sesión con cada rol, abre todas sus pantallas
y recorre en el navegador (sin JavaScript) el flujo de una compra: carrito, pago, alistamiento, despacho, reparto y
entrega. Detecta errores de plantilla, páginas de error y pantallas cortadas.

Uso:  python e2e/flujo_web.py [URL_WEB]      (por defecto http://localhost:8080)
"""
import os
import re
import sys
import time
from html import unescape
from urllib.parse import quote

import requests

WEB = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("WEB_URL", "http://localhost:8080")).rstrip("/")
DEMO = os.environ.get("DEMO_PASSWORD", "Demo12345")
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@minimundo.co"), os.environ.get("ADMIN_PASSWORD", "Admin12345"))

resultados = []
MARCAS_ERROR = ["Un servicio no respondió", "No se pudo completar la operación", "No encontramos esta página",
                "Whitelabel Error Page", "TemplateProcessingException", "SpelEvaluationException"]


def check(nombre, condicion, detalle=""):
    resultados.append((nombre, bool(condicion), detalle))
    print(f"[{'OK  ' if condicion else 'FALLA'}] {nombre}" + (f" -> {detalle}" if detalle and not condicion else ""),
          flush=True)
    return condicion


class Navegador:
    def __init__(self):
        self.s = requests.Session()
        self.ultima = None

    def get(self, ruta, **kw):
        r = self.s.get(WEB + ruta, timeout=90, **kw)
        self.ultima = r
        return r

    def csrf(self, html=None):
        html = html if html is not None else (self.ultima.text if self.ultima is not None else "")
        m = re.search(r'name="_csrf"\s+value="([^"]+)"', html) or re.search(r'value="([^"]+)"\s+name="_csrf"', html)
        if not m:
            m = re.search(r'<meta name="_csrf" content="([^"]+)"', html)
        return m.group(1) if m else None

    def post(self, ruta, datos=None, desde=None, **kw):
        if desde:
            self.get(desde)
        token = self.csrf()
        datos = dict(datos or {})
        if token:
            datos["_csrf"] = token
        r = self.s.post(WEB + ruta, data=datos, timeout=90, **kw)
        self.ultima = r
        return r


def pantalla(nav, ruta, nombre=None, contiene=None):
    nombre = nombre or ruta
    try:
        r = nav.get(ruta)
    except requests.RequestException as e:
        return check(f"Pantalla {nombre}", False, str(e))
    texto = r.text
    problema = next((m for m in MARCAS_ERROR if m in texto), None)
    ok = r.status_code == 200 and problema is None and "</html>" in texto.lower()
    detalle = f"HTTP {r.status_code}, url final {r.url}"
    if problema:
        detalle += f", contiene '{problema}'"
        msg = re.search(r'<p class="medida mx-auto">([^<]*)</p>', texto)
        if msg:
            detalle += f": {unescape(msg.group(1))[:200]}"
    elif "</html>" not in texto.lower():
        detalle += ", página cortada (posible error de plantilla a mitad de render)"
    if ok and contiene and contiene not in unescape(texto):
        ok, detalle = False, f"no contiene '{contiene}'"
    check(f"Pantalla {nombre}", ok, detalle)
    return texto if ok else None


def login(email, password):
    nav = Navegador()
    nav.get("/login")
    r = nav.post("/login", {"email": email, "password": password}, allow_redirects=True)
    if not check(f"Login web {email}", r.status_code == 200 and "/login" not in r.url, f"{r.status_code} {r.url}"):
        return None
    return nav


def enlaces(html, patron):
    return list(dict.fromkeys(re.findall(patron, html or "")))


def aviso_ok(html):
    m = re.search(r'class="aviso aviso-ok[^"]*"[^>]*>(.*?)</', html or "", re.S)
    return unescape(m.group(1)).strip() if m else None


def aviso_error(html):
    m = re.search(r'class="aviso aviso-error[^"]*"[^>]*>(.*?)</', html or "", re.S)
    return unescape(m.group(1)).strip() if m else None


def main():
    for i in range(60):
        try:
            if requests.get(WEB + "/actuator/health", timeout=10).status_code == 200:
                break
        except requests.RequestException:
            pass
        time.sleep(5)

    # ---------------------------------------------------------------- Visitante
    anon = Navegador()
    inicio = pantalla(anon, "/", "inicio")
    catalogo = pantalla(anon, "/catalogo", "catálogo")
    pantalla(anon, "/catalogo?q=body&orden=precio_asc", "catálogo con búsqueda")
    pantalla(anon, "/catalogo?genero=NINA&talla=6M", "catálogo filtrado")
    productos = enlaces(catalogo, r'href="/producto/([0-9a-f]{24})"')
    check("El catálogo muestra productos", len(productos) >= 5, f"{len(productos)} productos")
    for pid in productos[:3]:
        pantalla(anon, f"/producto/{pid}", f"producto {pid}")
    pantalla(anon, "/login", "login")
    pantalla(anon, "/registro", "registro")
    pantalla(anon, "/recuperar", "recuperar contraseña")
    r = anon.get("/cuenta/pedidos", allow_redirects=False)
    check("Sin sesión, /cuenta redirige al login", r.status_code in (302, 303) and "/login" in r.headers.get("Location", ""))
    img = enlaces(inicio or catalogo, r'src="/img/([0-9a-f]{24})"')
    if img:
        r = anon.get(f"/img/{img[0]}")
        check("Las fotos de producto se sirven desde MongoDB", r.status_code == 200 and r.headers.get("Content-Type", "").startswith("image/"))

    # Registro de un cliente nuevo desde la web
    correo = f"web{int(time.time())}@prueba.co"
    anon.get("/registro")
    r = anon.post("/registro", {"nombre": "Cliente Web", "email": correo, "telefono": "3001234567",
                                 "password": "Clave12345", "confirmacion": "Clave12345", "terminos": "si"})
    if check("Registro de cliente desde la web", r.status_code == 200 and r.url.endswith("/login") and "quedó creada" in r.text,
             f"{r.status_code} {r.url} {aviso_error(r.text)}"):
        nuevo = login(correo, "Clave12345")
        if nuevo:
            pantalla(nuevo, "/cuenta/pedidos", "mis pedidos de un cliente nuevo")

    # ---------------------------------------------------------------- Cliente: compra completa
    cli = login("cliente@minimundo.co", DEMO)
    pedido_id = None
    if cli:
        for ruta in ["/cuenta/pedidos", "/cuenta/perfil", "/cuenta/pqrs", "/cuenta/pqrs/nueva", "/cuenta/solicitudes",
                     "/carrito"]:
            pantalla(cli, ruta)
        # Elegir un producto con una variante disponible
        sku = prod = None
        for pid in productos:
            html = cli.get(f"/producto/{pid}").text
            m = re.search(r"data-variantes='([^']+)'", html) or re.search(r'data-variantes="([^"]+)"', html)
            if not m:
                continue
            import json
            try:
                variantes = json.loads(unescape(m.group(1)))
            except ValueError:
                continue
            libres = [v for v in variantes if (v.get("disponible") or 0) >= 1]
            if libres:
                sku, prod = libres[0]["sku"], pid
                break
        check("Hay una variante disponible para comprar", sku is not None)
        if sku:
            r = cli.post("/carrito/agregar", {"sku": sku, "cantidad": "1", "productoId": prod}, desde=f"/producto/{prod}")
            check("Agregar al carrito desde la ficha", r.status_code == 200 and r.url.endswith("/carrito") and sku in r.text,
                  f"{r.url} {aviso_error(r.text)}")
            pantalla(cli, "/checkout?ciudad=Bucaramanga", "checkout", contiene="Bucaramanga")
            datos = {"usarGuardada": "no", "nombreRecibe": "Laura Gómez", "telefono": "3001112233",
                     "direccion": "Calle 45 # 27-10", "barrio": "Cabecera", "ciudad": "Bucaramanga",
                     "indicaciones": "Portería", "titular": "LAURA GOMEZ", "numeroTarjeta": "4000 0000 0000 0002",
                     "vencimiento": "12/30", "cvv": "123"}
            r = cli.post("/checkout", datos)
            check("Pago rechazado: vuelve al checkout con el motivo y conserva el carrito",
                  r.status_code == 200 and "/checkout" in r.url and "fondos" in r.text.lower(), f"{r.url} {aviso_error(r.text)}")
            datos["numeroTarjeta"] = "4242 4242 4242 4242"
            r = cli.post("/checkout", datos)
            ok = check("Pago aprobado: muestra el pedido creado", r.status_code == 200 and "/cuenta/pedidos/" in r.url,
                       f"{r.url} {aviso_error(r.text)}")
            if ok:
                pedido_id = re.search(r"/cuenta/pedidos/([0-9a-f]{24})", r.url).group(1)
                pantalla(cli, f"/cuenta/pedidos/{pedido_id}", "detalle del pedido del cliente", contiene="Pagado")
        html = pantalla(cli, "/cuenta/pedidos", "mis pedidos")
        for pid in enlaces(html, r'href="/cuenta/pedidos/([0-9a-f]{24})"')[:4]:
            pantalla(cli, f"/cuenta/pedidos/{pid}", f"pedido {pid}")
        html = pantalla(cli, "/cuenta/pqrs", "mis PQRS")
        for pid in enlaces(html, r'href="/cuenta/pqrs/([0-9a-f]{24})"')[:2]:
            pantalla(cli, f"/cuenta/pqrs/{pid}", f"PQRS {pid}")
        r = cli.post("/cuenta/pqrs", {"tipo": "PETICION", "descripcion": "Quisiera saber si llegan tallas 4T de los bodies."},
                     desde="/cuenta/pqrs/nueva")
        check("Radicar PQRS desde la web", r.status_code == 200 and "PQRS-" in r.text, f"{r.url} {aviso_error(r.text)}")
        r = cli.post("/cuenta/perfil", {"nombre": "", "telefono": "300"}, desde="/cuenta/perfil")
        check("Perfil con datos inválidos muestra el error en el formulario", r.status_code == 200 and "is-invalid" in r.text,
              f"{r.status_code} {r.url}")
        r = cli.get("/admin", allow_redirects=True)
        check("El cliente no entra al panel de administración", "/403" in r.url or r.status_code == 403, r.url)

    # ---------------------------------------------------------------- Asesor
    ase = login("asesor@minimundo.co", DEMO)
    if ase:
        html = pantalla(ase, "/asesor/pqrs", "bandeja de PQRS")
        for pid in enlaces(html, r'href="/asesor/pqrs/([0-9a-f]{24})"')[:3]:
            pantalla(ase, f"/asesor/pqrs/{pid}", f"PQRS asesor {pid}")
        pantalla(ase, "/asesor/pqrs?estado=RADICADA", "PQRS radicadas")
        pantalla(ase, "/asesor/solicitudes", "solicitudes de cambio")
        pantalla(ase, "/asesor/solicitudes?estado=APROBADA", "solicitudes aprobadas")
        html = pantalla(ase, "/asesor/clientes", "clientes")
        pantalla(ase, "/asesor/clientes?q=laura", "buscar clientes")
        for cid in enlaces(html, r'href="/asesor/clientes/([0-9a-f]{24})"')[:2]:
            pantalla(ase, f"/asesor/clientes/{cid}", f"cliente {cid}")

    # ---------------------------------------------------------------- Operario: alistar y despachar el pedido web
    ope = login("operario@minimundo.co", DEMO)
    envio_id = None
    if ope:
        html = pantalla(ope, "/operario/alistamiento", "alistamiento")
        if pedido_id:
            r = ope.post("/operario/alistamiento/tomar", {"pedidoId": pedido_id})
            ok = check("Operario toma el pedido", r.status_code == 200 and "/operario/envios/" in r.url,
                       f"{r.url} {aviso_error(r.text)}")
            if ok:
                envio_id = r.url.rstrip("/").split("/")[-1].split("?")[0]
                items = re.findall(r'<td class="font-monospace">([A-Z0-9-]+)</td>\s*<td>.*?</td>\s*<td>(\d+)</td>', r.text, re.S)
                check("El alistamiento lista las prendas del pedido", len(items) >= 1, str(len(items)))
                for s, c in items:
                    r = ope.post(f"/operario/envios/{envio_id}/verificar", {"sku": s, "cantidad": c})
                check("Verificación de prendas", "Verificado" in r.text, aviso_error(r.text))
                r = ope.post(f"/operario/envios/{envio_id}/confirmar", {})
                check("Confirmar alistamiento", "Alistamiento confirmado" in r.text, aviso_error(r.text))
                r = ope.post(f"/operario/envios/{envio_id}/despacho", {"transportadora": "Reparto propio MiniMundo", "guia": "WEB-001"})
                check("Registrar despacho", "WEB-001" in r.text and "Asigna un repartidor" in r.text, aviso_error(r.text))
                rep = re.search(r'<option value="([0-9a-f]{24})">Jhon', r.text)
                if check("La lista de repartidores activos aparece", rep is not None):
                    r = ope.post(f"/operario/envios/{envio_id}/repartidor", {"repartidorId": rep.group(1)})
                    check("Asignar repartidor", "Asignado a" in r.text, aviso_error(r.text))
        html = pantalla(ope, "/operario/despachos", "despachos y reparto")
        for eid in enlaces(html, r'href="/operario/envios/([0-9a-f]{24})"')[:2]:
            pantalla(ope, f"/operario/envios/{eid}", f"envío {eid}")
        pantalla(ope, "/operario/devoluciones", "devoluciones por reingresar")
        html = pantalla(ope, "/operario/productos", "productos")
        pantalla(ope, "/operario/productos?estado=BORRADOR", "productos en borrador")
        pantalla(ope, "/operario/productos?estado=INACTIVO", "productos desactivados")
        pantalla(ope, "/operario/productos?q=BOD", "buscar productos")
        pantalla(ope, "/operario/productos/nuevo", "nuevo producto")
        for pid in enlaces(html, r'href="/operario/productos/([0-9a-f]{24})"')[:3]:
            pantalla(ope, f"/operario/productos/{pid}", f"editar producto {pid}")
        r = ope.post("/operario/productos", {"nombre": "", "precio": "", "variantes[0].talla": "", "variantes[0].color": ""},
                     desde="/operario/productos/nuevo")
        check("Producto incompleto muestra los errores en el formulario", r.status_code == 200 and "is-invalid" in r.text
              and not any(m in r.text for m in MARCAS_ERROR), f"{r.status_code} {r.url}")
        cat = re.search(r'<option value="([0-9a-f]{24})">Bodies', ope.get("/operario/productos/nuevo").text)
        if cat:
            r = ope.post("/operario/productos", {"nombre": "Body web de prueba", "descripcion": "Creado desde la prueba web",
                                                 "categoriaId": cat.group(1), "precio": "39.900", "genero": "UNISEX",
                                                 "rangoEdad": "M0_12", "variantes[0].talla": "3M", "variantes[0].color": "Verde",
                                                 "variantes[1].talla": "6M", "variantes[1].color": "Verde"})
            check("Crear producto desde la web", "/operario/productos/" in r.url and "BOD-" in r.text, f"{r.url} {aviso_error(r.text)}")
        pantalla(ope, "/operario/categorias", "categorías y colecciones")
        html = pantalla(ope, "/operario/inventario", "inventario")
        pantalla(ope, "/operario/inventario?filtro=bajo", "inventario bajo el mínimo")
        pantalla(ope, "/operario/inventario?filtro=agotado", "inventario agotado")
        skus = enlaces(html, r'href="/operario/inventario/([A-Z0-9%-]+)"')
        for s in skus[:2]:
            pantalla(ope, f"/operario/inventario/{s}", f"movimientos {s}")
        if skus:
            r = ope.post("/operario/inventario/entrada", {"sku": skus[0], "cantidad": "3", "motivo": "Prueba web", "volver": "movimientos"})
            check("Registrar entrada de inventario", "Entrada registrada" in r.text, aviso_error(r.text))

    # ---------------------------------------------------------------- Repartidor: entrega el pedido web
    rep = login("repartidor@minimundo.co", DEMO)
    if rep:
        html = pantalla(rep, "/repartidor/entregas", "mis entregas")
        pantalla(rep, "/repartidor/historial", "historial de entregas")
        if envio_id:
            r = rep.post(f"/repartidor/entregas/{envio_id}/en-ruta", {}, desde="/repartidor/entregas")
            check("Repartidor sale a entregar", "En camino" in r.text, aviso_error(r.text))
            r = rep.post(f"/repartidor/entregas/{envio_id}/entrega", {})
            check("Repartidor registra la entrega", "Entrega registrada" in r.text, aviso_error(r.text))
            pantalla(rep, "/repartidor/historial", "historial con la entrega", contiene="Entregado")
    if cli and pedido_id:
        pantalla(cli, f"/cuenta/pedidos/{pedido_id}", "pedido entregado visto por el cliente", contiene="Entregado")

    # ---------------------------------------------------------------- Administrador
    adm = login(*ADMIN)
    if adm:
        pantalla(adm, "/admin", "panel del negocio")
        pantalla(adm, f"/admin?desde=2020-01-01&hasta=2099-12-31", "panel con periodo")
        pantalla(adm, "/admin?desde=2026-12-31&hasta=2026-01-01", "panel con periodo inválido")
        pantalla(adm, "/admin/reportes", "reportes")
        r = adm.get("/admin/reportes/ventas.csv?desde=2020-01-01&hasta=2099-12-31")
        check("Exportar ventas en CSV", r.status_code == 200 and "csv" in r.headers.get("Content-Type", ""), r.headers.get("Content-Type"))
        r = adm.get("/admin/reportes/inventario.csv")
        check("Exportar inventario en CSV", r.status_code == 200 and "csv" in r.headers.get("Content-Type", ""), r.headers.get("Content-Type"))
        html = pantalla(adm, "/admin/pedidos", "pedidos")
        pantalla(adm, "/admin/pedidos?estado=ENTREGADO", "pedidos entregados")
        for pid in enlaces(html, r'href="/admin/pedidos/([0-9a-f]{24})"')[:3]:
            pantalla(adm, f"/admin/pedidos/{pid}", f"pedido admin {pid}")
        html = pantalla(adm, "/admin/usuarios", "usuarios")
        pantalla(adm, "/admin/usuarios?rol=REPARTIDOR", "usuarios repartidores")
        pantalla(adm, "/admin/usuarios/nuevo", "nuevo usuario")
        for uid in enlaces(html, r'href="/admin/usuarios/([0-9a-f]{24})"')[:2]:
            pantalla(adm, f"/admin/usuarios/{uid}", f"editar usuario {uid}")
        r = adm.post("/admin/usuarios", {"nombre": "", "email": "malo", "password": "1", "rol": "ASESOR"}, desde="/admin/usuarios/nuevo")
        check("Usuario inválido muestra errores", r.status_code == 200 and "is-invalid" in r.text, f"{r.status_code} {r.url}")
        pantalla(adm, "/admin/auditoria", "auditoría")
        pantalla(adm, "/admin/auditoria?tipo=LOGIN_FALLIDO", "auditoría filtrada")
        pantalla(adm, "/admin/parametros", "parámetros")
        r = adm.post("/admin/parametros/tarifas", {"ciudad": "Floridablanca", "costo": "7000"}, desde="/admin/parametros")
        check("Agregar tarifa de envío", "Floridablanca" in r.text, aviso_error(r.text))

    fallas = [r for r in resultados if not r[1]]
    print(f"\n{len(resultados) - len(fallas)}/{len(resultados)} verificaciones de pantallas correctas")
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::notice title=E2E web::{len(resultados) - len(fallas)}/{len(resultados)} verificaciones correctas")
        for nombre, _, detalle in fallas[:40]:
            print(f"::error title=E2E web falla::{nombre} -> {str(detalle)[:400]}")
    return 1 if fallas else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        if os.environ.get("GITHUB_ACTIONS"):
            for nombre, _, detalle in [r for r in resultados if not r[1]][:30]:
                print(f"::error title=E2E web falla::{nombre} -> {str(detalle)[:400]}")
            print(f"::error title=E2E web excepción::{type(e).__name__}: {str(e)[:500]}")
        sys.exit(1)
