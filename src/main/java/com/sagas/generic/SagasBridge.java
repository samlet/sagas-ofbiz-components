package com.sagas.generic;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sagas.actions.ActionsManager;
import com.sagas.actors.bus.BlueSrv;
import com.sagas.blueprints.BlueprintManager;
import com.sagas.blueprints.HttpServerActorInteraction;
import com.sagas.hybrid.MetaBroker;
import com.sagas.hybrid.ServiceBroker;
import com.sagas.meta.FormManager;
import com.sagas.meta.MetaManager;
import com.sagas.products.ProductForms;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerConfig;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.*;
import py4j.CallbackClient;
import py4j.GatewayServer;

import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static py4j.GatewayServer.*;

@Singleton
public class SagasBridge implements Container, ComponentProvider {
    private static final String module = SagasBridge.class.getName();
    protected String configFile = null;

    private GatewayServer gateway;
    private GenericDelegator delegator;
    private GenericAbstractDispatcher dispatcher;
    private String containerName;
    private Stack stack;

    // akka
    ActorSystem actorSystem;
    Materializer materializer;

    private Injector injector; // don't direct references this
    private Map<String, Class<?>> registries= Maps.newConcurrentMap();

    ContainerConfig.Configuration.Property delegatorProp;

    public SagasBridge() {
        stack = new Stack();
        stack.push("Initial Item");
    }

    public GenericDelegator getDelegator() {
        if(delegator==null){
            delegator = (GenericDelegator)DelegatorFactory.getDelegator(delegatorProp.value);
        }
        return delegator;
    }

    public GenericAbstractDispatcher getDispatcher() {
        if(dispatcher==null){
            delegator=getDelegator();
            this.dispatcher = (GenericAbstractDispatcher)ServiceContainer.getLocalDispatcher(delegator.getDelegatorName(),
                    delegator);
        }
        return dispatcher;
    }

    public Stack getStack() {
        return stack;
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    public GenericValue getUserLogin() throws GenericEntityException {
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
        return userLogin;
    }

    public Map<String, Object> sudo(String serviceName, Map params) throws GenericServiceException, GenericEntityException {
        params.put("userLogin", getUserLogin());
        Map<String, Object> result = dispatcher.runSync(serviceName, params);
        return result;
    }

    private List<GenericValue> getCompleteList(Map<String, Object> context) {
        List<GenericValue> foundElements = new LinkedList<GenericValue>();
        try (EntityListIterator listIt = (EntityListIterator) context.get("listIt")) {
            if (listIt != null) {
                foundElements = listIt.getCompleteList();
            }
        } catch (GenericEntityException e) {
            Debug.logError(" Failed to extract values from EntityListIterator after a performFind service", module);
        }
        return foundElements;
    }

    public List<GenericValue> find(Map params) throws GenericEntityException, GenericServiceException {
        params.put("userLogin", getUserLogin());
        Map<String, Object> result = dispatcher.runSync("performFind", params);
        if(ServiceUtil.isSuccess(result)) {
            List<GenericValue> foundElements = getCompleteList(result);
            return foundElements;
        }else{
            Debug.logError("Error in refund payment: " + ServiceUtil.getErrorMessage(result), module);
            return Lists.newArrayList();
        }
    }

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.containerName = name;
        this.configFile = configFile;
    }

    @Override
    public boolean start() throws ContainerException {
        // get the container config
        ContainerConfig.Configuration cfg = ContainerConfig.getConfiguration(containerName, configFile);
        ContainerConfig.Configuration.Property lookupHostProp = cfg.getProperty("bound-host");
        ContainerConfig.Configuration.Property lookupPortProp = cfg.getProperty("bound-port");
        ContainerConfig.Configuration.Property callbackPortProp = cfg.getProperty("callback-port");
        this.delegatorProp = cfg.getProperty("delegator-name");

        // check the required delegator-name property
        if (delegatorProp == null || UtilValidate.isEmpty(delegatorProp.value)) {
            throw new ContainerException("Invalid delegator-name defined in container configuration");
        }

        String host = lookupHostProp == null || lookupHostProp.value == null ? "localhost" : lookupHostProp.value;
        int port = lookupPortProp == null || lookupPortProp.value == null ? DEFAULT_PORT : Integer.parseInt(lookupPortProp.value);
        int callbackPort = callbackPortProp == null || callbackPortProp.value == null ? DEFAULT_PYTHON_PORT : Integer.parseInt(callbackPortProp.value);

        // get the delegator for this container
        this.delegator = (GenericDelegator)DelegatorFactory.getDelegator(delegatorProp.value);

        if(this.delegator!=null) {
            // create the LocalDispatcher
            this.dispatcher = getDispatcher();
        }

        InetAddress defaultAddress= null;
        try {
            defaultAddress = InetAddress.getByName("0.0.0.0");
            this.gateway = new GatewayServer(
                    this,
                    port, defaultAddress,
                    DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, null,
                    new CallbackClient(callbackPort, defaultAddress));

            gateway.start();
            System.out.println(String.format(" [✔] Gateway Server Started on port %d, callback %d", port, callbackPort));

            this.buildContext();
        } catch (UnknownHostException e) {
            throw new ContainerException(e);
        } catch (IOException e) {
            throw new ContainerException(e.getMessage(), e);
        }

        return true;
    }

    private void buildContext() throws IOException {
        actorSystem = ActorSystem.create();
        materializer = ActorMaterializer.create(actorSystem);

        this.injector=Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();

                bind(ComponentProvider.class).toInstance(SagasBridge.this);

                bind(GenericDelegator.class).toInstance(getDelegator());
                bind(LocalDispatcher.class).toInstance(getDispatcher());
                bind(GatewayServer.class).toInstance(gateway);

                bind(EntityEventHub.class).asEagerSingleton();
                bind(ServiceBroker.class).asEagerSingleton();
                bind(MetaBroker.class).asEagerSingleton();

                // for akka
                bind(ActorSystem.class).toInstance(actorSystem);
                bind(Materializer.class).toInstance(materializer);
            }
        });

        this.registries.put("entity_event_hub", EntityEventHub.class);
        this.registries.put("service_invoker", ServiceInvoker.class);
        this.registries.put("meta_mgr", MetaManager.class);
        this.registries.put("form_mgr", FormManager.class);
        this.registries.put("product_forms", ProductForms.class);

        ActionsManager actionsManager=injector.getInstance(ActionsManager.class);
        actionsManager.initializeActions(injector);
        System.out.println( actionsManager.getActionNames());

        // akka actors
        injector.getInstance(HttpServerActorInteraction.class).start();
        injector.getInstance(BlueSrv.class).start();
        injector.getInstance(BlueprintManager.class).start();

        // grpc
        injector.getInstance(GenericRoutines.class).start();
    }

    public Object getComponent(String name){
        Class<?> clazz=this.registries.get(name);
        if(clazz==null){
            throw new RuntimeException("Cannot find component "+name);
        }
        return injector.getInstance(clazz);
    }

    @Override
    public void stop() throws ContainerException {
        this.gateway.shutdown();
        this.actorSystem.terminate();
    }

    @Override
    public String getName() {
        return containerName;
    }

    @Override
    public <T> T inject(T object) {
        this.injector.injectMembers(object);
        return object;
    }
}
