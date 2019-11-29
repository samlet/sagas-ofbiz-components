package com.sagas.hybrid;

import com.beust.jcommander.internal.Maps;
import com.rabbitmq.client.*;
import com.sagas.actors.bus.RabbitConnector;
import com.sagas.generic.ServiceInvoker;
import com.sagas.generic.ValueHelper;
import com.sagas.security.SecurityManager;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Singleton
public class ServiceBroker extends AbstractBroker{
    private static final String module = ServiceBroker.class.getName();
    private static final String RPC_QUEUE_NAME = "rpc_queue";

    private SecurityManager securityManager;

    @Inject
    public ServiceBroker(LocalDispatcher dispatcher, GenericDelegator delegator, SecurityManager securityManager,
                         RabbitConnector connector) {
        super(dispatcher, delegator, connector);
        this.securityManager=securityManager;
    }

    protected String getQueueName(){
        return RPC_QUEUE_NAME;
    }

    protected Consumer getConsumer(){
        return new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                        .Builder()
                        .correlationId(properties.getCorrelationId())
                        .build();
                String replyTo=properties.getReplyTo();

                String response = "";
                try {
                    String message = new String(body, StandardCharsets.UTF_8);
                    // Map values=gson.fromJson(message, Map.class);
                    ServiceInvoker invoker = new ServiceInvoker(dispatcher, delegator, securityManager,null, message);

                    Debug.logImportant(" [.] invoke(" + message + ")", module);
                    // response += fib(n);
                    ServiceInvoker.ErrCode result = invoker.invoke();
                    if(result!=ServiceInvoker.ErrCode.Success){
                        Map errResp=Maps.newHashMap("_result", result.getID(), "messages", invoker.getErrorMessages());
                        response=ValueHelper.mapToJson(errResp);
                    }else {
                        response = invoker.getJsonResult();
                    }
                } catch (RuntimeException e) {
                    System.out.println(" [.] " + e.toString());
                    Debug.logError(e, e.getMessage(), module);
                    response=ValueHelper.toJsonMap("_result", ServiceInvoker.ErrCode.ERR_OTHER.getID(),
                            "message", e.getMessage());
                } catch (Exception e) {
                    Debug.logError(e, e.getMessage(), module);
                    response=ValueHelper.toJsonMap("_result", ServiceInvoker.ErrCode.ERR_OTHER.getID(),
                            "message", e.getMessage());
                } finally {
                    channel.basicPublish("", replyTo, replyProps, response.getBytes("UTF-8"));
                    channel.basicAck(envelope.getDeliveryTag(), false);
                    Debug.logImportant("invoke end.", module);
                }
            }
        };
    }
}