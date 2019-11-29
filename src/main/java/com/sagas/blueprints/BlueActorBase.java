package com.sagas.blueprints;

import akka.actor.ActorRef;
import com.sagas.actions.ActionResponse;
import com.sagas.actors.base.BlueBaseObject;
import com.sagas.actors.base.BlueComponent;
import com.sagas.actors.bus.BlueAck;
import com.sagas.actors.bus.BluePacket;
import com.sagas.hybrid.MetaBroker;
import com.sagas.meta.model.BlueMessage;
import com.sagas.meta.model.MetaPayload;

import java.util.List;

public abstract class BlueActorBase extends BlueBaseObject {
    protected final ActorRef responser;
    public BlueActorBase(List<Class<? extends BlueComponent>> components) {
        super(components);
        this.responser = getContext().actorFor("akka://default/user/responserActor");
    }


    protected void proc(BluePacket packet, Functions.ActonApply<BlueMessage> f) {
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

    @Override
    public void preStart() {
        getLogger().info("[☃] {} started", getSelf().path());
    }

    protected abstract void onPacket(BluePacket packet);

    @Override
    public Receive createReceive() {
        return initReceiver()
                .match(BluePacket.class, this::onPacket)
                .build();
    }
}
