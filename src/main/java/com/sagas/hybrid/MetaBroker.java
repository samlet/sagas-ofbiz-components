package com.sagas.hybrid;

import com.beust.jcommander.internal.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.sagas.actions.ActionInvoker;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actions.ActionsManager;
import com.sagas.actors.bus.RabbitConnector;
import com.sagas.generic.ServiceInvoker;
import com.sagas.generic.ValueHelper;
import com.sagas.meta.FormManager;
import com.sagas.meta.MetaManager;
import com.sagas.meta.model.*;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;

@Singleton
public class MetaBroker extends AbstractBroker{
    private static final String module = MetaBroker.class.getName();
    private static final String QUEUE_NAME = "meta_queue";
    private MetaManager metaManager;

    @Inject
    private Provider<FormManager> formManager;
    @Inject
    ActionsManager actionsManager;

    @Inject
    public MetaBroker(LocalDispatcher dispatcher, GenericDelegator delegator,
                      RabbitConnector connector,
                      MetaManager metaManager) {
        super(dispatcher, delegator, connector);
        this.metaManager=metaManager;
    }

    protected String getQueueName(){
        return QUEUE_NAME;
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

                MetaQuery query=MetaQuery.parseFrom(body);

                MetaPayload.Builder payload = execute(query);

                byte[] response=payload.build().toByteArray();
                channel.basicPublish("", replyTo, replyProps, response);
                channel.basicAck(envelope.getDeliveryTag(), false);
                Debug.logImportant("invoke end.", module);
            }
        };
    }

    public MetaPayload.Builder execute(MetaQuery query) {
        MetaPayload.Builder payload=MetaPayload.newBuilder();

        if(query.getInfoType().equals("entity")){
            MetaEntity entity=metaManager.getMetaEntity(query.getUri());
            payload.setType(MetaPayloadType.META_ENTITY)
                    .setBody(entity.toByteString());

        }else if(query.getInfoType().equals("service")) {
            try {
                MetaService service = metaManager.getMetaService(query.getUri());
                payload.setType(MetaPayloadType.META_SERVICE)
                        .setBody(service.toByteString());
            }catch(Exception e){
                wrapException(payload, e);
            }
        }else if(query.getInfoType().equals("form")) {
            try {
                MetaForm form=formManager.get().getMetaForm(query.getUri());
                payload.setType(MetaPayloadType.META_FORM)
                        .setBody(form.toByteString());
            }catch(Exception e){
                wrapException(payload, e);
            }
        }else if(query.getInfoType().equals("action")) {
            try{
                ActionRequest req=new ActionRequest(query.getUri(), query.getData());
                ActionInvoker invoker=actionsManager.getInvoker(req.getActionName());
                if(invoker==null){
                    throw new GeneralException("Cannot find action "+req.getActionName());
                }
                ActionResponse resp= invoker.execute(req);
                // if action execute success
                checkResponse(payload, resp);
            }catch(Exception e){
                wrapException(payload, e);
            }
        }else{
            ErrorInfo errorInfo=ErrorInfo.newBuilder()
                    .setErrorType(ErrorType.UNSUPPORT_META)
                    .setMessage("Don't support meta query "+query.getInfoType())
                    .build();
            payload.setType(MetaPayloadType.ERROR_INFO).setBody(errorInfo.toByteString());
        }
        return payload;
    }

    public static void checkResponse(MetaPayload.Builder payload, ActionResponse resp) throws InvalidProtocolBufferException {
        if(resp.getCode()==0) {
            payload.setType(MetaPayloadType.ACTION_RESULT);
            if (resp.getPayload() != null) {
                payload.setBody(resp.getPayload());
            }
        }else{
            ErrorInfo errorInfo = ErrorInfo.parseFrom(resp.getPayload());
            payload.setType(MetaPayloadType.ERROR_INFO).setBody(errorInfo.toByteString());
        }
    }

    public static void wrapException(MetaPayload.Builder payload, Exception e) {
        Debug.logError(e, e.getMessage(), module);

        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                .setErrorType(ErrorType.RETRIEVE_INFO_FAIL)
                .setMessage(e.getMessage())
                .build();
        payload.setType(MetaPayloadType.ERROR_INFO).setBody(errorInfo.toByteString());
    }
}