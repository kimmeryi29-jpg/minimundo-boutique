// Interacciones pequeñas de MiniMundo: galería, selector de talla/color, filas de variantes y confirmaciones.
document.addEventListener('DOMContentLoaded', () => {
  // Galería del detalle de producto
  document.querySelectorAll('[data-galeria]').forEach(g => {
    const principal = g.querySelector('[data-principal]');
    g.querySelectorAll('[data-miniatura]').forEach(b => b.addEventListener('click', () => {
      principal.src = b.dataset.miniatura;
      g.querySelectorAll('[data-miniatura]').forEach(x => x.classList.remove('activo'));
      b.classList.add('activo');
    }));
  });

  // HU-29 / HU-30: elegir talla y color muestra la disponibilidad de esa variante
  const comprar = document.querySelector('[data-variantes]');
  if (comprar) {
    const variantes = JSON.parse(comprar.dataset.variantes);
    const sku = comprar.querySelector('input[name=sku]');
    const info = comprar.querySelector('[data-disponibilidad]');
    const boton = comprar.querySelector('button[type=submit]');
    const cantidad = comprar.querySelector('input[name=cantidad]');
    const actualizar = () => {
      const t = comprar.querySelector('input[name=talla]:checked');
      const c = comprar.querySelector('input[name=color]:checked');
      comprar.querySelectorAll('input[name=color]').forEach(inp => {
        const v = t && variantes.find(x => x.talla === t.value && x.color === inp.value);
        const lab = comprar.querySelector('label[for="' + inp.id + '"]');
        lab.classList.toggle('agotada', !!t && (!v || v.disponible <= 0));
      });
      if (!t || !c) { info.textContent = 'Elige la talla y el color.'; info.className = 'texto-suave'; sku.value = ''; boton.disabled = true; return; }
      const v = variantes.find(x => x.talla === t.value && x.color === c.value);
      if (!v) { info.textContent = 'Esta combinación no existe. Prueba otro color.'; info.className = 'aviso-agotado'; sku.value = ''; boton.disabled = true; return; }
      sku.value = v.sku;
      if (v.disponible <= 0) { info.textContent = 'Agotado en talla ' + v.talla + ' color ' + v.color + '. Prueba otra talla o color.'; info.className = 'aviso-agotado'; boton.disabled = true; }
      else { info.textContent = v.disponible + (v.disponible === 1 ? ' unidad disponible' : ' unidades disponibles'); info.className = 'text-success fw-bold'; boton.disabled = false; cantidad.max = v.disponible; }
    };
    comprar.querySelectorAll('input[name=talla], input[name=color]').forEach(i => i.addEventListener('change', actualizar));
    actualizar();
  }

  // Formulario de producto: agregar y quitar filas de variantes (HU-17)
  const filas = document.querySelector('[data-filas-variantes]');
  if (filas) {
    const plantilla = document.querySelector('#plantilla-variante');
    const renumerar = () => filas.querySelectorAll('.fila-variante').forEach((f, i) => {
      f.querySelectorAll('[data-campo]').forEach(inp => inp.name = 'variantes[' + i + '].' + inp.dataset.campo);
    });
    document.querySelector('[data-agregar-variante]').addEventListener('click', () => {
      filas.appendChild(plantilla.content.cloneNode(true));
      renumerar();
    });
    filas.addEventListener('click', e => {
      if (e.target.closest('[data-quitar-variante]')) {
        e.target.closest('.fila-variante').remove();
        renumerar();
      }
    });
    renumerar();
  }

  // Checkout: dirección guardada o nueva (HU-37)
  document.querySelectorAll('input[name=usarGuardada]').forEach(r => r.addEventListener('change', () => {
    const nueva = document.querySelector('[data-direccion-nueva]');
    if (nueva) nueva.hidden = r.value === 'si' && r.checked;
  }));

  // Ciudad del checkout recalcula el envío (HU-38)
  const ciudad = document.querySelector('[data-recalcular-envio]');
  if (ciudad) ciudad.addEventListener('change', () => {
    const url = new URL(window.location.href);
    url.searchParams.set('ciudad', ciudad.value);
    window.location.href = url.toString();
  });

  // Confirmación antes de acciones que no se deshacen
  document.querySelectorAll('form[data-confirmar]').forEach(f => f.addEventListener('submit', e => {
    if (!window.confirm(f.dataset.confirmar)) e.preventDefault();
  }));
});
