"""
Toma capturas de las pantallas principales de cada rol (escritorio y celular) para revisar el diseño.
Uso:  python e2e/capturas.py [URL_WEB] [CARPETA]
"""
import os
import re
import sys

from playwright.sync_api import sync_playwright

WEB = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
OUT = sys.argv[2] if len(sys.argv) > 2 else "capturas"
DEMO = os.environ.get("DEMO_PASSWORD", "Demo12345")

ROLES = {
    None: ["/", "/catalogo", "PRODUCTO", "/login", "/registro"],
    "cliente@minimundo.co": ["/carrito", "/cuenta/pedidos", "PEDIDO_CLIENTE", "/cuenta/pqrs/nueva", "/cuenta/perfil"],
    "asesor@minimundo.co": ["/asesor/pqrs", "PQRS_ASESOR", "/asesor/solicitudes", "/asesor/clientes"],
    "operario@minimundo.co": ["/operario/alistamiento", "/operario/despachos", "/operario/productos", "PRODUCTO_FORM",
                              "/operario/inventario", "/operario/categorias"],
    "repartidor@minimundo.co": ["/repartidor/entregas", "/repartidor/historial"],
    "admin@minimundo.co": ["/admin", "/admin/reportes", "/admin/pedidos", "/admin/usuarios", "/admin/parametros",
                           "/admin/auditoria"],
}
DINAMICAS = {
    "PRODUCTO": ("/catalogo", r'href="(/producto/[0-9a-f]{24})"'),
    "PEDIDO_CLIENTE": ("/cuenta/pedidos", r'href="(/cuenta/pedidos/[0-9a-f]{24})"'),
    "PQRS_ASESOR": ("/asesor/pqrs", r'href="(/asesor/pqrs/[0-9a-f]{24})"'),
    "PRODUCTO_FORM": ("/operario/productos", r'href="(/operario/productos/[0-9a-f]{24})"'),
}


def nombre_archivo(rol, ruta):
    base = (rol or "visitante").split("@")[0]
    return base + "_" + (re.sub(r"[^a-z0-9]+", "-", ruta.lower()).strip("-") or "inicio")


def main():
    os.makedirs(OUT, exist_ok=True)
    with sync_playwright() as p:
        nav = p.chromium.launch()
        for rol, rutas in ROLES.items():
            for ancho, sufijo in ((1366, "escritorio"), (390, "celular")):
                ctx = nav.new_context(viewport={"width": ancho, "height": 900}, locale="es-CO")
                page = ctx.new_page()
                if rol:
                    page.goto(WEB + "/login")
                    page.fill("input[name=email]", rol)
                    page.fill("input[name=password]", "Admin12345" if rol.startswith("admin") else DEMO)
                    page.click("form[action$='/login'] button[type=submit]")
                    page.wait_for_load_state("networkidle")
                for ruta in rutas:
                    if ruta in DINAMICAS:
                        origen, patron = DINAMICAS[ruta]
                        page.goto(WEB + origen)
                        m = re.search(patron, page.content())
                        if not m:
                            continue
                        ruta = m.group(1)
                    page.goto(WEB + ruta)
                    page.wait_for_load_state("networkidle")
                    archivo = os.path.join(OUT, f"{nombre_archivo(rol, ruta)}_{sufijo}.jpg")
                    page.screenshot(path=archivo, full_page=True, type="jpeg", quality=70)
                    print("captura", archivo)
                ctx.close()
        nav.close()


if __name__ == "__main__":
    main()
