package com.sagas.blueprints;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;
import com.sagas.actors.base.BlueManager;
import com.sagas.actors.base.BlueMonitor;
import com.sagas.generic.ComponentProvider;

import javax.inject.Inject;

public class BlueUserManager extends BlueManager {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Inject
    ComponentProvider componentProvider;

    public static Props props(ComponentProvider provider) {
        return Props.create(BlueUserManager.class, () -> provider.inject(new BlueUserManager()));
    }

    @Override
    public void preStart() {
        log.info("Object {} started", self().path().name());
        String userId="system";
        ActorRef defaultUser =
                getContext()
                        .actorOf(BlueUser.props(userId,  componentProvider, Lists.newArrayList()), userId);
        getContext().watch(defaultUser);
    }
}
