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
import com.sagas.meta.FormManager;
import com.sagas.meta.model.BlueMessage;
import com.sagas.meta.model.BlueOffer;
import com.sagas.meta.model.MetaPayload;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.util.List;

public class BlueRepresenter extends BlueActorBase {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    LocalDispatcher dispatcher;
    @Inject
    GenericDelegator delegator;
    @Inject
    EntityRoutines entityRoutines;
    @Inject
    FormManager formManager;

    public BlueRepresenter(List<Class<? extends BlueComponent>> components) {
        super(components);
    }

    static public Props props(ComponentProvider provider,
                              List<Class<? extends BlueComponent>> components) {
        return Props.create(BlueRepresenter.class, () -> provider.inject(new BlueRepresenter(components)));
    }

    @Override
    public void preStart() {
        log.info("[☃] {} started", getSelf().path().name());
    }

    @Override
    protected void onPacket(BluePacket packet) {
        String type = packet.getRequest().getType();
        log.info(".. receive message {}", type);

        //*
        switch (type) {
            case "offer":
                BlueOffer bo = BlueOffer.newBuilder()
                        .setUser(getSelf().path().name())
                        .setOffer(System.currentTimeMillis())
                        .build();
                responser.tell(new BlueAck(packet.getResponse(), bo.toByteArray()), getSelf());
                break;
            case "meta":
                proc(packet, msg -> formManager.getMetaForm(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
            case "form-data":
                proc(packet, msg -> formManager.renderFormData(
                        new ActionRequest(type, packet.getRequest().getBody())
                ));
                break;
        }
        //*/
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

