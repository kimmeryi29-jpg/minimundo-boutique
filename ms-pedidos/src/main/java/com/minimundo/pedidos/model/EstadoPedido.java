package com.minimundo.pedidos.model;

/** Ciclo de vida del pedido (sección 5.4): CREADO → PAGADO → EN_ALISTAMIENTO → DESPACHADO → EN_RUTA → ENTREGADO, y CANCELADO. */
public enum EstadoPedido {
    CREADO("Pago pendiente"),
    PAGADO("Pagado"),
    EN_ALISTAMIENTO("En alistamiento"),
    DESPACHADO("Despachado"),
    EN_RUTA("En camino"),
    ENTREGADO("Entregado"),
    CANCELADO("Cancelado");

    private final String texto;

    EstadoPedido(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }

    /** Transiciones válidas que puede solicitar ms-logistica. */
    public boolean puedePasarA(EstadoPedido destino) {
        return switch (this) {
            case PAGADO -> destino == EN_ALISTAMIENTO || destino == CANCELADO;
            case EN_ALISTAMIENTO -> destino == DESPACHADO || destino == CANCELADO;
            case DESPACHADO -> destino == EN_RUTA;
            case EN_RUTA -> destino == ENTREGADO || destino == DESPACHADO;
            case CREADO -> destino == PAGADO || destino == CANCELADO;
            default -> false;
        };
    }

    /** HU-44: solo se cancela lo que aún no se ha despachado. */
    public boolean cancelable() {
        return this == CREADO || this == PAGADO || this == EN_ALISTAMIENTO;
    }

    /** Pedidos que cuentan como venta (pagados y no cancelados). */
    public boolean esVenta() {
        return this != CREADO && this != CANCELADO;
    }
}
