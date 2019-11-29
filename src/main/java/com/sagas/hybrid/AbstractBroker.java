package com.sagas.hybrid;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.sagas.actors.bus.RabbitConnector;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractBroker {
    private static final String module = AbstractBroker.class.getName();

    protected LocalDispatcher dispatcher;
    protected GenericDelegator delegator;
    protected Connection connection = null;
    protected Channel channel;
    protected RabbitConnector connector;

    @Inject
    public AbstractBroker(LocalDispatcher dispatcher, GenericDelegator delegator, RabbitConnector connector) {
        this.dispatcher=dispatcher;
        this.delegator=delegator;
        this.connector=connector;

        try {
            connection = connector.getConnection();
            channel = connection.createChannel();

            String queueName= getQueueName();

            channel.queueDeclare(queueName, false, false, false, null);
            channel.queuePurge(queueName);

            channel.basicQos(1);

            System.out.println(" [✔] Awaiting "+getQueueName()+" requests");

            Consumer consumer = getConsumer();

            channel.basicConsume(queueName, false, consumer);
            // Wait and be prepared to consume the message from RPC client.

        } catch (IOException e) {
            Debug.logFatal(e, e.getMessage(), module);
            e.printStackTrace();
        }
    }

    protected abstract String getQueueName();
    protected abstract Consumer getConsumer();
    public void stop() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException _ignore) {
            }
        }
    }
}
