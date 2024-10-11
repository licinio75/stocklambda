package com.demo.stocklambda.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PedidoSQSMessageDTO {

    private String pedidoId;               // ID del pedido
    private String usuarioNombre;  // Nombre del usuario que realiza el pedido
    private String usuarioEmail;   // Email del usuario que realiza el pedido
    private List<ItemPedidoDTO> items;  // Lista de ítems pedidos
    private double precioTotal;    // Precio total del pedido

}
