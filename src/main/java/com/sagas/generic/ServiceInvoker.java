package com.sagas.generic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sagas.security.SecurityManager;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.*;
import org.apache.ofbiz.webapp.event.EventHandlerException;

import javax.inject.Inject;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ServiceInvoker {
    public static final String module = ServiceInvoker.class.getName();
    private String jsonParameters;
    private String serviceName;

    private LocalDispatcher dispatcher;
    private GenericDelegator delegator;

    private List<Object> errorMessages = Lists.newArrayList();
    private Locale locale=Locale.getDefault();
    private TimeZone timeZone = TimeZone.getDefault();
    private Map<String, Object> result = null;
    private String jsonResult=null;

    private SecurityManager securityManager;

    public ServiceInvoker(LocalDispatcher dispatcher, GenericDelegator delegator, SecurityManager securityManager,
                          String serviceName, String jsonParameters){
        this.dispatcher=dispatcher;
        this.delegator=delegator;
        this.securityManager=securityManager;
        this.jsonParameters=jsonParameters;
        this.serviceName=serviceName;
    }

    public String getJsonResult() {
        return jsonResult;
    }

    public List<Object> getErrorMessages() {
        return errorMessages;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public GenericValue getUserLogin(String loginId) throws GenericEntityException {
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", loginId).cache().queryOne();
        return userLogin;
    }

    public ErrCode invoke() {
        ModelService model=null;
        Map<String, Object> serviceContext=null;
        // prepare the context
        try {
            serviceContext = ValueHelper.jsonToMap(this.jsonParameters);
            if(this.serviceName==null){
                this.serviceName=(String)serviceContext.get("_service");
            }

            model=dispatcher.getDispatchContext().getModelService(this.serviceName);

            GenericValue user = withUserLogin(serviceContext);
            if(user!=null) {
                serviceContext.put("userLogin", user);
            }

            serviceContext.keySet().removeIf(o -> o.startsWith("_"));
            serviceContext = model.makeValid(serviceContext, ModelService.IN_PARAM, true, errorMessages, timeZone, locale);
            if (errorMessages.size() > 0) {
                // uh-oh, had some problems...
                return ErrCode.ERR_VALIDATE;
            }
        }catch (Exception e){
            Debug.logError(e, "Service context validate fail", module);
            // throw new EventHandlerException("Service invocation error", e);
            return ErrCode.ERR_VALIDATE;
        }

        // invoke the service
        try {
            result = dispatcher.runSync(serviceName, serviceContext);
        } catch (ServiceAuthException e) {
            // not logging since the service engine already did
            errorMessages.add(e.getNonNestedMessage());
            return ErrCode.ERR_INVOKE;
        } catch (ServiceValidationException e) {
            // not logging since the service engine already did
            errorMessages.add("serviceValidationException: "+ e.getMessage());
            if (e.getMessageList() != null) {
                errorMessages.addAll(e.getMessageList());
            } else {
                errorMessages.add(e.getNonNestedMessage());
            }
            return ErrCode.ERR_INVOKE;
        } catch (GenericServiceException e) {
            Debug.logError(e, "Service invocation error", module);
            // throw new EventHandlerException("Service invocation error", e.getNested());
            return ErrCode.ERR_INVOKE;
        }

        try {
            result.put("_result", ErrCode.Success.getID());
            this.jsonResult=ValueHelper.mapToJson(result);
        }catch (Exception e){
            Debug.logError(e, "Service response cannot jsonify", module);
            // throw new EventHandlerException("Service response cannot jsonify", e);
            return ErrCode.ERR_JSONIFY;
        }

        return ErrCode.Success;
    }

    private GenericValue withUserLogin(Map<String, Object> serviceContext) throws GenericEntityException, GeneralSecurityException {
        GenericValue user = null;
        String token=(String)serviceContext.get("_token");
        if(token!=null) {
            user=securityManager.verifyUser(token);
            Debug.logImportant("invoke service with user "+user.get("userLoginId")
                +", token "+token, module);
            return user;
        }

        if(SystemConstants.DEV_MODE) {
            String loginId = (String) serviceContext.get("_loginId");
            if (loginId == null) {
                loginId = "system";
            }
            user = getUserLogin(loginId);
        }
        return user;
    }

    // ErrCode.ERR_VALIDATE.getID() returns 1
    public enum ErrCode {
        Success(0), ERR_VALIDATE(1), ERR_INVOKE(2), ERR_JSONIFY(3), ERR_OTHER(4);

        private int id;

        ErrCode(int id) {
            this.id = id;
        }

        public int getID() {
            return id;
        }
    }
}
