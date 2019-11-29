package com.sagas.blueprints;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.common.collect.Lists;
import com.sagas.actors.base.BlueManager;
import com.sagas.actors.bus.BlueGreeter;
import com.sagas.generic.ComponentProvider;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BlueprintManager {
    @Inject
    ActorSystem system;
    @Inject
    private LocalDispatcher dispatcher;
    @Inject
    private GenericDelegator delegator;
    @Inject
    ComponentProvider componentProvider;

    /// Actors
    ActorRef responser;
    ActorRef userManager;

    public BlueprintManager(){

    }

    public void start(){
        // [akka://default/user/responserActor]
        this.responser=system.actorFor("akka://default/user/responserActor");
        userManager=system.actorOf(BlueUserManager.props(componentProvider), "logins");
    }
}

