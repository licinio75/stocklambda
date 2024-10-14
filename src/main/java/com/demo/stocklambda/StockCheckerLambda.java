package com.demo.stocklambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.demo.stocklambda.dto.PedidoSQSMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.stocklambda.dto.ItemPedidoDTO;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

public class StockCheckerLambda implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final SqsClient sqsClient = SqsClient.builder().build();

    private final String cancelQueueUrl = System.getenv("CANCEL_QUEUE_URL");
    private final String confirmQueueUrl = System.getenv("CONFIRM_QUEUE_URL");

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            if (message.getBody() == null || message.getBody().isEmpty()) {
                System.err.println("Mensaje vacío o null recibido. Saltando procesamiento.");
                continue; // Salta este mensaje
            } else {
                System.err.println("Mensaje recibido: "+message.getBody());
            }

            PedidoSQSMessageDTO pedido = parseMessage(message.getBody());
            if (pedido == null || pedido.getItems() == null || pedido.getItems().isEmpty()) {
                System.err.println("Error: El mensaje no contiene un pedido válido o los items están vacíos. Saltando procesamiento.");
                continue; // Salta este mensaje
            }

            boolean stockSuficiente = verificarStock(pedido);

            if (stockSuficiente) {
                System.out.println("Hay stock suficiente");
                actualizarStock(pedido);  // Actualizar el stock en la base de datos
                actualizarEstadoPedido(pedido, "COMPLETADO");  // Actualizar el estado a COMPLETADO
                enviarMensaje(pedido, confirmQueueUrl);  // Enviar mensaje a la cola de pedidos confirmados
            } else {
                System.out.println("No hay stock suficiente");
                actualizarEstadoPedido(pedido, "CANCELADO");  // Actualizar el estado a CANCELADO
                enviarMensaje(pedido, cancelQueueUrl);  // Enviar mensaje a la cola de pedidos cancelados
            }
        }
        return null;
    }

    private PedidoSQSMessageDTO parseMessage(String body) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(body, PedidoSQSMessageDTO.class);
        } catch (Exception e) {
            System.err.println("Error al parsear el mensaje: " + e.getMessage());
            return null; // Devuelve null si falla el parseo
        }
    }

    private boolean verificarStock(PedidoSQSMessageDTO pedido) {
        for (ItemPedidoDTO item : pedido.getItems()) {
            Map<String, AttributeValue> key = Map.of(
                "id", AttributeValue.builder().s(item.getProductoId()).build()
            );

            GetItemRequest request = GetItemRequest.builder()
                    .tableName("Productos")
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            if (response.item() == null || Integer.parseInt(response.item().get("stock").n()) < item.getCantidad()) {
                return false;
            }
        }
        return true;
    }

    private void actualizarStock(PedidoSQSMessageDTO pedido) {
        for (ItemPedidoDTO item : pedido.getItems()) {
            Map<String, AttributeValue> key = Map.of(
                "id", AttributeValue.builder().s(item.getProductoId()).build()
            );

            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("Productos")
                .key(key)
                .updateExpression("SET stock = stock - :cantidad")
                .expressionAttributeValues(Map.of(":cantidad", AttributeValue.builder().n(String.valueOf(item.getCantidad())).build()))
                .build();

            dynamoDbClient.updateItem(updateRequest);
        }
    }

    private void actualizarEstadoPedido(PedidoSQSMessageDTO pedido, String nuevoEstado) {
        Map<String, AttributeValue> key = Map.of(
            "id", AttributeValue.builder().s(pedido.getPedidoId()).build()
        );

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
            .tableName("Pedidos")  // Nombre de la tabla de pedidos
            .key(key)
            .updateExpression("SET estado = :nuevoEstado")
            .expressionAttributeValues(Map.of(":nuevoEstado", AttributeValue.builder().s(nuevoEstado).build()))
            .build();

        dynamoDbClient.updateItem(updateRequest);
    }

    private void enviarMensaje(PedidoSQSMessageDTO pedido, String queueUrl) {
        String mensaje = convertPedidoToJson(pedido);
        if (mensaje != null) {
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(mensaje)
                    .build();
            sqsClient.sendMessage(sendMsgRequest);
        }
    }

    private String convertPedidoToJson(PedidoSQSMessageDTO pedido) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(pedido);
        } catch (Exception e) {
            System.err.println("Error al convertir el pedido a JSON: " + e.getMessage());
            return null;
        }
    }
}
