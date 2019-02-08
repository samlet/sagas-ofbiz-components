package com.sagas.generic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static py4j.GatewayServer.*;

@Singleton
public class SagasBridge implements Container {
    private static final String module = SagasBridge.class.getName();
    protected String configFile = null;

    private GatewayServer gateway;
    private GenericDelegator delegator;
    private GenericAbstractDispatcher dispatcher;
    private String containerName;
    private Stack stack;

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
        this.delegatorProp = cfg.getProperty("delegator-name");

        // check the required delegator-name property
        if (delegatorProp == null || UtilValidate.isEmpty(delegatorProp.value)) {
            throw new ContainerException("Invalid delegator-name defined in container configuration");
        }

        String host = lookupHostProp == null || lookupHostProp.value == null ? "localhost" : lookupHostProp.value;
        String port = lookupPortProp == null || lookupPortProp.value == null ? "1099" : lookupPortProp.value;

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
                    DEFAULT_PORT, defaultAddress,
                    DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, null,
                    new CallbackClient(DEFAULT_PYTHON_PORT, defaultAddress));

            gateway.start();
            System.out.println("Gateway Server Started");

            this.buildContext();
        } catch (UnknownHostException e) {
            throw new ContainerException(e);
        }

        return true;
    }

    private void buildContext(){
        this.injector=Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();
                bind(GenericDelegator.class).toInstance(getDelegator());
                bind(LocalDispatcher.class).toInstance(getDispatcher());
                bind(GatewayServer.class).toInstance(gateway);
            }
        });

        this.registries.put("entity_event_hub", EntityEventHub.class);
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
    }

    @Override
    public String getName() {
        return containerName;
    }
}
