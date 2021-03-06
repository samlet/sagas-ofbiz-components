package com.sagas.generic;

import com.google.common.collect.Maps;
import com.sagas.SagasConf;
import com.sagas.actors.bus.RabbitEventProvider;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntity;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.eca.EntityEcaHandler;
import org.apache.ofbiz.entityext.eca.DelegatorEcaHandler;
import org.apache.ofbiz.entityext.eca.EntityEcaRule;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
public class EntityEventHub implements EntityEcaHandler<EntityEcaRule> {
    public static final String module = EntityEventHub.class.getName();
    private static EntityEventHub instance;
    @Inject
    Provider<KafkaProvider> kafkaProvider;
    @Inject
    Provider<RabbitEventProvider> rabbitProvider;

    private EntityEcaHandler<EntityEcaRule> defaultHandler;
    private GenericDelegator delegator;

    private Map<String, SubscriberInfo> eventSubscriberTypes = Maps.newConcurrentMap();
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> eventSubscribers = new ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>();
    @Inject
    private EntityEventHub(GenericDelegator delegator) {
        this.delegator = delegator;
        this.defaultHandler = this.delegator.getEntityEcaHandler();
        this.delegator.setEntityEcaHandler(this);
    }

    /*
    public static EntityEventHub getEntityEventHub(GenericDelegator delegator){
        if(instance==null){
            synchronized (EntityEventHub.class) {
                if (instance == null) {
                    instance = new EntityEventHub(delegator);
                }
            }
        }
        return instance;
    }
    */

    @Override
    public void setDelegator(Delegator delegator) {
        defaultHandler.setDelegator(delegator);
    }

    @Override
    public Map<String, List<EntityEcaRule>> getEntityEventMap(String entityName) {
        return defaultHandler.getEntityEventMap(entityName);
    }

    private String createMessage(String operation, String event, GenericEntity value, boolean isError) throws ClassNotFoundException, ConversionException {
        Map<String, Object> meta = Maps.newHashMap();
        meta.put("_operation", operation);
        meta.put("_event", event);
        meta.put("_entity", value.getEntityName());
        meta.put("_timestamp", System.currentTimeMillis());
        meta.put("_error", isError);
        return ValueHelper.entityToJson(value, meta);
    }

    private String createEntityEventTopic(GenericEntity value){
        return "measure.entities."+value.getEntityName();
    }

    @Override
    public void evalRules(String currentOperation, Map<String, List<EntityEcaRule>> eventMap, String event, GenericEntity value, boolean isError) throws GenericEntityException {
        if (SagasConf.trackOn) {
            Debug.logImportant("event hub for entity " + value.getEntityName()
                    + ", operation " + currentOperation
                    + ", event " + event + ".", module);
        }

        if(SagasConf.measureUpdaterOn){
            if(!currentOperation.equalsIgnoreCase("find")) {
                if (event.equals("run")||event.equals("return")) {
                    try {
                        kafkaProvider.get().post("measure.entities",
                                createMessage(currentOperation, event, value, isError));
                        rabbitProvider.get().post(createEntityEventTopic(value),
                                createMessage(currentOperation, event, value, isError));
                    } catch (Exception e) {
                        Debug.logError(e, e.getMessage(), module);
                    }
                }
            }
        }

        // operation: create, store, remove, find
        // event: validate, run, return
        String key = value.getEntityName() + '.' + currentOperation + '.' + event;
        SubscriberInfo subscriberInfo = eventSubscriberTypes.get(key);
        if (subscriberInfo != null) {
            try {
                switch (subscriberInfo.type) {
                    case Queue:
                        ConcurrentLinkedQueue<String> subQ = eventSubscribers.get(key);
                        if (subQ != null) {
                            subQ.add(createMessage(currentOperation, event, value, isError));
                        }
                        break;
                    case Kafka:
                        kafkaProvider.get().post(subscriberInfo.location,
                                createMessage(currentOperation, event, value, isError));
                        break;
                }
            } catch (ConversionException e) {
                Debug.logError(e, e.getMessage(), module);
            } catch (ClassNotFoundException e) {
                Debug.logError(e, e.getMessage(), module);
            }
        }

        defaultHandler.evalRules(currentOperation, eventMap, event, value, isError);
    }

    /*
    while (!queue.isEmpty()) {
        System.out.println(queue.poll());
    }
     */
    public ConcurrentLinkedQueue<String> getEventQueue(String entity, String operation, String event) {
        return this.eventSubscribers.get(entity + '.' + operation + '.' + event);
    }

    public void registerSubscriber(String entity, String operation, String event, String type) {
        String key = entity + '.' + operation + '.' + event;
        if (type.equalsIgnoreCase("queue")) {
            this.eventSubscriberTypes.put(key, new SubscriberInfo(SubscriberType.Queue, ""));
            if (getEventQueue(entity, operation, event) == null) {
                this.eventSubscribers.put(key, new ConcurrentLinkedQueue<String>());
            } else {
                // throw new RuntimeException("Event queue for "+entity + '.' + operation + '.' + event+" is already exists");
                Debug.logWarning("Event queue for " + entity + '.' + operation + '.' + event + " is already exists", module);
            }
        } else if (type.equalsIgnoreCase("kafka")) {
            this.eventSubscriberTypes.put(key, new SubscriberInfo(SubscriberType.Kafka, "event." + entity));
        }else{
            throw new RuntimeException("Don't support subscriber type "+type);
        }
    }

    public enum SubscriberType {
        Queue, Kafka
    }

    public static class SubscriberInfo {
        public SubscriberType type;
        public String location;

        public SubscriberInfo(SubscriberType type, String location) {
            this.type = type;
            this.location = location;
        }

    }
}
