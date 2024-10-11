package com.demo.stocklambda.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignora campos desconocidos, como "precioTotal"
public class ItemPedidoDTO {
    private String productoId;      // ID del producto
    private String nombreProducto;  // Nombre del producto
    private int cantidad;           // Cantidad de productos
    private double precioUnitario;  // Precio unitario del producto

    // Método para calcular el precio total de los ítems
    public double getPrecioTotal() {
        return cantidad * precioUnitario;
    }
}
