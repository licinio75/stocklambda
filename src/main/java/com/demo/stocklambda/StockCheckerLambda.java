package com.demo.stocklambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.demo.stocklambda.dto.PedidoSQSMessageDTO;
import com.demo.stocklambda.dto.ItemPedidoDTO;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;


import java.util.Map;
import java.util.HashMap;

public class StockCheckerLambda implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final SqsClient sqsClient = SqsClient.builder().build();

    private final String cancelationQueueUrl = System.getenv("CANCELATION_QUEUE_URL");
    private final String invoiceQueueUrl = System.getenv("INVOICE_QUEUE_URL");

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            PedidoSQSMessageDTO pedido = parseMessage(message.getBody());
            boolean stockSuficiente = verificarStock(pedido);

            if (stockSuficiente) {
                enviarMensaje(pedido, invoiceQueueUrl);
            } else {
                enviarMensaje(pedido, cancelationQueueUrl);
            }
        }
        return null;
    }

    private PedidoSQSMessageDTO parseMessage(String body) {
        return null; // Implementa aquí el parseo del mensaje
    }

    private boolean verificarStock(PedidoSQSMessageDTO pedido) {
        for (ItemPedidoDTO item : pedido.getItems()) {
            Map<String, AttributeValue> key = Map.of(
                "productoId", AttributeValue.builder().s(item.getProductoId()).build() // productoId es de tipo String
            );

            // Crear la solicitud para obtener el ítem en DynamoDB
            GetItemRequest request = GetItemRequest.builder()
                    .tableName("Productos")   // Nombre de la tabla
                    .key(key)                 // Usar la clave en el formato correcto
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            if (response.item() == null || Integer.parseInt(response.item().get("stock").n()) < item.getCantidad()) {
                return false;
            }
        }
        return true;
    }

    private void enviarMensaje(PedidoSQSMessageDTO pedido, String queueUrl) {

        String mensaje = convertPedidoToJson(pedido);

        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(mensaje)
                .build();

        sqsClient.sendMessage(sendMsgRequest);
    }

    private String convertPedidoToJson(PedidoSQSMessageDTO pedido) {
        return null; // Implementa aquí la conversión a JSON
    }
}
