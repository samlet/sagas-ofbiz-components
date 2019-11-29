package com.sagas.blueprints;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actors.base.BlueBaseObject;
import com.sagas.actors.base.BlueComponent;
import com.sagas.actors.bus.BlueAck;
import com.sagas.actors.bus.BluePacket;
import com.sagas.generic.ComponentProvider;
import com.sagas.generic.EntityRoutines;
import com.sagas.hybrid.MetaBroker;
import com.sagas.meta.model.BlueMessage;
import com.sagas.meta.model.BlueOffer;
import com.sagas.meta.model.MetaPayload;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.util.List;

public class BlueActorTemplate extends BlueActorBase {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    LocalDispatcher dispatcher;
    @Inject
    GenericDelegator delegator;
    @Inject
    EntityRoutines entityRoutines;

    public BlueActorTemplate(List<Class<? extends BlueComponent>> components) {
        super(components);
    }

    static public Props props(ComponentProvider provider,
                              List<Class<? extends BlueComponent>> components) {
        return Props.create(BlueActorTemplate.class, () -> provider.inject(new BlueActorTemplate(components)));
    }

    @Override
    protected void onPacket(BluePacket packet) {
        String type = packet.getRequest().getType();
        log.info(".. receive message {}", type);

        switch (type) {
            case "offer":
                BlueOffer bo = BlueOffer.newBuilder()
                        .setUser(getSelf().path().name())
                        .setOffer(System.currentTimeMillis())
                        .build();
                responser.tell(new BlueAck(packet.getResponse(), bo.toByteArray()), getSelf());
                break;
            /*
            case "store":
                proc(packet, msg -> entityRoutines.storeAll(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
            */
        }

    }

    @Override
    public LoggingAdapter getLogger() {
        return log;
    }
}

