package com.sagas.meta;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sagas.meta.model.*;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Map;

@Singleton
public class MetaManager {
    private Map<String, MetaEntity> entities= Maps.newConcurrentMap();
    private Map<String, MetaService> services=Maps.newConcurrentMap();

    private LocalDispatcher dispatcher;
    private GenericDelegator delegator;

    @Inject
    MetaManager(GenericDelegator delegator, LocalDispatcher dispatcher){
        this.delegator=delegator;
        this.dispatcher=dispatcher;
    }

    private String ensure(String val){
        if(val==null){
            return "";
        }
        return val;
    }
    private String toStringVal(Object val){
        if(val==null){
            return "";
        }
        return val.toString();
    }

    public MetaService getMetaService(String serviceName) throws GenericServiceException {
        MetaService findIt=services.get(serviceName);
        if(findIt!=null){
            return findIt;
        }

        MetaService.Builder meta=MetaService.newBuilder();
        ModelService srv=dispatcher.getDispatchContext().getModelService(serviceName);
        meta.setName(srv.name).setDefinitionLocation(srv.definitionLocation)
                .setDescription(ensure(srv.description))
                .setEngineName(srv.engineName)
                .setNameSpace(ensure(srv.nameSpace))
                .setLocation(ensure(srv.location))
                .setInvoke(ensure(srv.invoke))
                .setDefaultEntityName(ensure(srv.defaultEntityName))
                .setAuth(srv.auth)
                .setExport(srv.export)
                .setDebug(srv.debug)
                .setValidate(srv.validate)
                .setPermissionServiceName(ensure(srv.permissionServiceName))
                .setPermissionMainAction(ensure(srv.permissionMainAction))
                .setPermissionResourceDesc(ensure(srv.permissionResourceDesc));

        // parameters info
        srv.getModelParamList().forEach(param -> {
            MetaParam.Builder metaParam=meta.addParametersBuilder().setName(param.getName())
                    .setDescription(ensure(param.description))
                    .setType(ensure(param.type))
                    .setMode(param.mode)
                    .setFormLabel(ensure(param.formLabel))
                    .setEntityName(ensure(param.entityName))
                    .setFieldName(ensure(param.fieldName))
                    .setDefaultValue(toStringVal(param.getDefaultValue()))
                    .setOptional(param.optional)
                    .setOverrideOptional(param.overrideOptional)
                    .setFormDisplay(param.formDisplay)
                    .setOverrideFormDisplay(param.overrideFormDisplay)
                    .setAllowHtml(ensure(param.allowHtml))
                    .setInternal(param.internal);

        });

        // other info
        srv.overrideParameters.forEach(op->meta.addOverrideParameters(op.getName()));
        srv.implServices.forEach(is -> {
            meta.addImplServicesBuilder().setService(is.getService()).setOptional(is.isOptional());
        });
        srv.permissionGroups.forEach(pg ->{
            MetaPermGroup.Builder metaPg= meta.addPermissionGroupsBuilder().setJoinType(pg.joinType);
            pg.permissions.forEach(p -> {
                metaPg.addPermissionsBuilder().setPermissionType(p.permissionType)
                        .setServiceModel(p.serviceModel.name)
                        .setNameOrRole(ensure(p.nameOrRole))
                        .setAction(ensure(p.action))
                        .setPermissionServiceName(ensure(p.permissionServiceName))
                        .setPermissionResourceDesc(ensure(p.permissionResourceDesc))
                        .setAuth(p.auth)
                        .setClazz(ensure(p.clazz));
            });
        });
        srv.notifications.forEach(noti->{
            meta.addNotificationsBuilder()
                    .setNotificationEvent(ensure(noti.notificationEvent))
                    .setNotificationGroupName(ensure(noti.notificationGroupName))
                    .setNotificationMode(ensure(noti.notificationMode));
        });

        findIt= meta.build();
        return findIt;
    }

    public MetaEntity getMetaEntity(String entityName){
        MetaEntity findIt=entities.get(entityName);
        if(findIt!=null){
            return findIt;
        }

        MetaEntity.Builder meta=MetaEntity.newBuilder();
        ModelEntity ent= delegator.getModelEntity(entityName);
        meta.setEntityName(ent.getEntityName())
                .setPackageName(ent.getPackageName())
                .setDependentOn(ent.getDependentOn())
                .setVersion(ent.getVersion())
                .setDescription(ent.getDescription())
                .setTitle(ent.getTitle())
                .setDefaultResourceName(ensure(ent.getDefaultResourceName()));

        // process fields
        Iterator<ModelField> iterator= ent.getFieldsIterator();
        while(iterator.hasNext()){
            ModelField fld=iterator.next();
            meta.addFieldsBuilder().setName(fld.getName())
                    .setType(fld.getType())
                    .setPk(fld.getIsPk())
                    .setNotNull(fld.getIsNotNull())
                    .setAutoCreatedInternal(fld.getIsAutoCreatedInternal())
                    .setEnableAuditLog(fld.getEnableAuditLog())
                    .setEncrypt(fld.getEncryptMethod().isEncrypted())
                    .addAllValidators(fld.getValidators());
        }

        // process relations
        Iterator<ModelRelation> rels =ent.getRelationsIterator();
        while (rels.hasNext()){
            ModelRelation rel=rels.next();
            meta.addRelations(getRelation(rel));
        }

        // process others
        meta.addAllViewEntities(Lists.newArrayList(ent.getViewConvertorsIterator()));
        meta.addAllPks(ent.getPkFieldNames());
        meta.addAllNopks(ent.getNoPkFieldNames());
        findIt= meta.build();

        // cache it
        this.entities.put(entityName, findIt);
        return findIt;
    }

    private MetaRelation getRelation(ModelRelation rel) {
        MetaRelation.Builder metaRel=MetaRelation.newBuilder();
        metaRel.setTitle(rel.getTitle())
                .setType(rel.getType())
                .setRelEntityName(rel.getRelEntityName())
                .setFkName(rel.getFkName())
                .setAutoRelation(rel.isAutoRelation())
                .setCombinedName(rel.getCombinedName());

        rel.getKeyMaps().forEach((k)->{
            metaRel.addKeyMapsBuilder().setFieldName(k.getFieldName())
                    .setRelFieldName(k.getRelFieldName())
                    .setFullName(k.toString());
        });
        return metaRel.build();
    }
}

