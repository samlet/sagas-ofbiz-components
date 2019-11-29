package com.sagas.generic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actions.RemoteAction;
import com.sagas.meta.model.*;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericPK;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.util.EntityFindOptions;
import org.apache.ofbiz.entity.util.EntitySaxReader;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityRoutines {
    public static final String module = EntityRoutines.class.getName();
    LocalDispatcher dispatcher;
    GenericDelegator delegator;

    @Inject
    public EntityRoutines(LocalDispatcher dispatcher, GenericDelegator delegator) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
    }

    public Names getEntityNames() throws GenericEntityException {
        Names.Builder names=Names.newBuilder();
        names.addAllName(delegator.getModelReader().getEntityNames());
        return names.build();
    }

    public TaEntityValues find(String uri, TaFindOptions options) throws GenericEntityException {
        EntityFindOptions findOptions = new EntityFindOptions();
        findOptions.setLimit(options.getLimit());
        findOptions.setOffset(options.getOffset());
        List<GenericValue> values = delegator.findList(uri, null, null, null, findOptions, options.getUseCache());
        return convert(values);
    }

    private TaEntityValues convert(List<GenericValue> values) {
        TaEntityValues.Builder result = TaEntityValues.newBuilder();
        values.forEach(val -> {
            TaEntityValue.Builder rec = result.addValuesBuilder().setEntityName(val.getEntityName()).setMutable(val.isMutable());
            val.forEach((k, v) -> {
                // ...
            });
        });
        return result.build();
    }

    public GenericValue ref(String entityName, String idval) throws GenericEntityException {
        ModelEntity model = delegator.getModelEntity(entityName);
        ModelField pk = model.getOnlyPk();
        Map<String, Object> ctx = Maps.newHashMap();
        ctx.put(pk.getName(), idval);
        return delegator.findOne(entityName, ctx, false);
    }

    public GenericValue createValue(String entityName, Map<String, String> stringMap) throws GenericEntityException {
        ModelEntity model = delegator.getModelEntity(entityName);
        GenericValue currentValue = delegator.makeValue(entityName);
        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            try {
                // treat empty strings as nulls, but do NOT ignore them, instead set as null and update
                if (value != null) {
                    if (currentValue.getModelEntity().isField(name)) {
                        String valueString = (value.length() > 0 ? value : null);
                        currentValue.setString(name, valueString);
                    } else {
                        Debug.logWarning("Ignoring invalid field name [" + name + "] found for the entity: " + currentValue.getEntityName() + " with value=" + value, module);
                    }
                }
            } catch (Exception e) {
                String msg = "Could not set field " + entityName + "." + name + " to the value " + value;
                Debug.logError(e, msg, module);
                throw new GenericEntityException(msg, e);
            }
        }
        return currentValue;
    }

    public void storeAll(List<GenericValue> values) throws GenericEntityException {
        delegator.storeAll(values);
    }

    @RemoteAction
    public ActionResponse storeAll(ActionRequest request) throws InvalidProtocolBufferException, GenericEntityException {
        TaStringEntriesBatch batch = TaStringEntriesBatch.parseFrom(request.getPayload());
        int total = storeAll(batch);
        return new ActionResponse(0, ProtoValueUtil.toPayload(total));
    }

    public int storeAll(TaStringEntriesBatch batch) throws GenericEntityException {
        List<GenericValue> values = Lists.newArrayList();
        for (TaStringEntries entries : batch.getRecordsList()) {
            GenericValue value = createValue(entries.getEntityName(), entries.getValuesMap());
            values.add(value);
        }
        return delegator.storeAll(values);
    }

    @RemoteAction
    public ActionResponse storeJsonEntities(ActionRequest request) throws IOException, GenericEntityException, ConversionException, ClassNotFoundException {
        TaJsonEntities batch = TaJsonEntities.parseFrom(request.getPayload());
        List<GenericValue> values = Lists.newArrayList();
        for (TaJsonEntity ent : batch.getEntitiesList()) {
            Map<String, Object> valMap = ValueHelper.jsonToMap(ent.getJson());
            GenericValue value = delegator.makeValue(ent.getEntityName(), valMap);
            values.add(value);
        }
        int total = delegator.storeAll(values);
        return new ActionResponse(0, ProtoValueUtil.toPayload(total));
    }

    @RemoteAction
    public ActionResponse get(ActionRequest request) throws GenericEntityException, InvalidProtocolBufferException, ConversionException, ClassNotFoundException {
        TaStringEntries query=TaStringEntries.parseFrom(request.getPayload());
        ModelEntity model = delegator.getModelEntity(query.getEntityName());
        // Set<String> pkNames= Sets.newHashSet();
        Map<String, String> pkValues=Maps.newHashMap();
        Map<String, String> qValues=query.getValuesMap();
        model.getPkFieldNames().forEach(f -> {
            // pkNames.add(f);
            pkValues.put(f, qValues.get(f));
        });

        // GenericPK pk= delegator.makePK(query.getEntityName(), pkValues);
        // GenericValue result=delegator.findByPrimaryKeyPartial(pk, pkNames);
        GenericValue result=delegator.findOne(query.getEntityName(), pkValues, false);
        return new ActionResponse(0, ProtoValueUtil.toPayload(result));
    }
}
