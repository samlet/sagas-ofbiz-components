package com.sagas.blueprints;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;
import com.sagas.actors.base.BlueComponent;
import com.sagas.actors.base.BlueObject;
import com.sagas.actors.bus.BluePacket;
import com.sagas.generic.ComponentProvider;

import javax.inject.Inject;
import java.util.List;

public class BlueUser extends BlueObject {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    ComponentProvider componentProvider;

    ActorRef persister;
    ActorRef representer;
    ActorRef artifacts;

    public BlueUser(String groupId, List<Class<? extends BlueComponent>> components) {
        super(groupId, components);
    }

    public static Props props(String groupId, ComponentProvider provider, List<Class<? extends BlueComponent>> components) {
        return Props.create(BlueUser.class, () -> provider.inject(new BlueUser(groupId, components)));
    }

    @Override
    public void preStart() {
        log.info("Object {} started", self().path().name());
        // persister
        persister = getContext()
                        .actorOf(BluePersister.props(componentProvider,
                                Lists.newArrayList()), "persister");
        getContext().watch(persister);

        // representer: provider forms
        representer= getContext()
                .actorOf(BlueRepresenter.props(componentProvider,
                        Lists.newArrayList()), "representer");
        getContext().watch(representer);

        // artifacts
        artifacts= getContext()
                .actorOf(BlueArtifacts.props(componentProvider,
                        Lists.newArrayList()), "artifacts");
        getContext().watch(artifacts);
    }

    @Override
    protected void onPacket(BluePacket packet) {
        super.onPacket(packet);

    }

}


