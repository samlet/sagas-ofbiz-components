package com.sagas.blueprints;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actors.base.BlueBaseObject;
import com.sagas.actors.base.BlueComponent;
import com.sagas.actors.bus.BlueAck;
import com.sagas.actors.bus.BlueGreeter;
import com.sagas.actors.bus.BluePacket;
import com.sagas.generic.ComponentProvider;
import com.sagas.generic.EntityRoutines;
import com.sagas.hybrid.MetaBroker;
import com.sagas.meta.model.BlueMessage;
import com.sagas.meta.model.BlueOffer;
import com.sagas.meta.model.MetaPayload;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.util.List;

public class BluePersister extends BlueBaseObject {
    private final ActorRef responser;
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    LocalDispatcher dispatcher;
    @Inject
    GenericDelegator delegator;
    @Inject
    EntityRoutines entityRoutines;

    public BluePersister(List<Class<? extends BlueComponent>> components) {
        super(components);
        this.responser = getContext().actorFor("akka://default/user/responserActor");
        ;
    }

    static public Props props(ComponentProvider provider,
                              List<Class<? extends BlueComponent>> components) {
        return Props.create(BluePersister.class, () -> provider.inject(new BluePersister(components)));
    }

    @Override
    public void preStart() {
        log.info("[☃] Blue Persister started");
    }

    private void proc(BluePacket packet, Functions.ActonApply<BlueMessage> f) {
        BlueAck ack = packet.getResponse();
        MetaPayload.Builder payload = MetaPayload.newBuilder();
        try {
            ActionResponse response = f.apply(packet.getRequest());
            MetaBroker.checkResponse(payload, response);
        } catch (Exception e) {
            MetaBroker.wrapException(payload, e);
        } finally {
            responser.tell(new BlueAck(ack, payload.build().toByteArray()), getSelf());
        }
    }

    private void onPacket(BluePacket packet) {
        String type = packet.getRequest().getType();
        log.info(".. persister receive message {}", type);

        switch (type) {
            case "offer":
                BlueOffer bo = BlueOffer.newBuilder()
                        .setUser(getSelf().path().name())
                        .setOffer(System.currentTimeMillis())
                        .build();
                responser.tell(new BlueAck(packet.getResponse(), bo.toByteArray()), getSelf());
                break;
            case "store":
                proc(packet, msg -> entityRoutines.storeAll(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
            case "storeJson":
                proc(packet, msg -> entityRoutines.storeJsonEntities(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
            case "get":
                proc(packet, msg -> entityRoutines.get(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
        }
    }

    @Override
    public Receive createReceive() {
        return initReceiver()
                .match(BluePacket.class, this::onPacket)
                .build();
    }

    @Override
    public LoggingAdapter getLogger() {
        return log;
    }
}

