package com.sagas.blueprints;

import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.sagas.actions.ActionRequest;
import com.sagas.actors.base.BlueComponent;
import com.sagas.actors.bus.BlueAck;
import com.sagas.actors.bus.BluePacket;
import com.sagas.generic.ComponentProvider;
import com.sagas.generic.EntityRoutines;
import com.sagas.meta.model.BlueOffer;
import com.sagas.meta.model.PingResponse;
import com.sagas.remote.ArtifactServant;
import com.sagas.remote.ArtifactsClient;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.util.List;

public class BlueArtifacts extends BlueActorBase {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    LocalDispatcher dispatcher;
    @Inject
    GenericDelegator delegator;
    @Inject
    EntityRoutines entityRoutines;
    @Inject
    ArtifactServant servant;

    public BlueArtifacts(List<Class<? extends BlueComponent>> components) {
        super(components);
    }

    static public Props props(ComponentProvider provider,
                              List<Class<? extends BlueComponent>> components) {
        return Props.create(BlueArtifacts.class, () -> provider.inject(new BlueArtifacts(components)));
    }

    private String getParentName(){
        return getContext().getParent().path().name();
    }

    @Override
    public void preStart() {
        log.info("[☃] {} started, parent is {}",
                getSelf().path().name(), getParentName());
    }

    @Override
    protected void onPacket(BluePacket packet) {
        String type = packet.getRequest().getType();
        log.info(".. receive message {}", type);

        switch (type) {
            case "offer":
                // call remote service
                String respName=getSelf().path().name();
                try {
                    PingResponse response=ArtifactsClient.once(getSelf().path().name());
                    respName=response.getResponse();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //
                BlueOffer bo = BlueOffer.newBuilder()
                        .setUser(respName)
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
            case "talk":
                // extract request message, then invoke rpc_artifacts.talk
                proc(packet, msg -> servant.talk(
                        new ActionRequest(type, packet.getRequest().getBody())));
                break;
        }

    }

    @Override
    public LoggingAdapter getLogger() {
        return log;
    }
}

