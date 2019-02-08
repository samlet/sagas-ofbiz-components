package com.sagas.generic;

import com.google.common.collect.Maps;
import groovy.lang.Script;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.service.*;
import org.codehaus.groovy.runtime.InvokerHelper;

import javax.script.ScriptContext;
import java.util.*;

import static org.apache.ofbiz.base.util.UtilGenerics.cast;

public class GroovyDirector {
    private static final String module = GroovyDirector.class.getName();
    GroovyManager groovyManager;
    LocalDispatcher dispatcher;
    private static final Set<String> protectedKeys = createProtectedKeys();
    private static final Object[] EMPTY_ARGS = {};

    private static Set<String> createProtectedKeys() {
        Set<String> newSet = new HashSet<>();
        /* Commenting out for now because some scripts write to the parameters Map - which should not be allowed.
        newSet.add(ScriptUtil.PARAMETERS_KEY);
        */
        newSet.add("dctx");
        newSet.add("dispatcher");
        newSet.add("delegator");
        newSet.add("visualTheme");
        return Collections.unmodifiableSet(newSet);
    }

    public GroovyDirector(GroovyManager groovyManager, LocalDispatcher dispatcher){
        this.groovyManager=groovyManager;
        this.dispatcher=dispatcher;
    }

    public Map<String, Object> exec(String location, String invoke, Map<String, Object> context, Set<String> resultNames) throws GenericServiceException {
        if (UtilValidate.isEmpty(location)) {
            throw new GenericServiceException("Cannot run Groovy service with empty location");
        }
        Map<String, Object> params = new HashMap<>();
        params.putAll(context);

        Map<String, Object> gContext = new HashMap<>();
        gContext.putAll(context);
        gContext.put(ScriptUtil.PARAMETERS_KEY, params);

        DispatchContext dctx = dispatcher.getDispatchContext();
        gContext.put("dctx", dctx);
        gContext.put("security", dctx.getSecurity());
        gContext.put("dispatcher", dctx.getDispatcher());
        gContext.put("delegator", dispatcher.getDelegator());
        try {
            ScriptContext scriptContext = ScriptUtil.createScriptContext(gContext, protectedKeys);
            ScriptHelper scriptHelper = (ScriptHelper)scriptContext.getAttribute(ScriptUtil.SCRIPT_HELPER_KEY);
            if (scriptHelper != null) {
                gContext.put(ScriptUtil.SCRIPT_HELPER_KEY, scriptHelper);
            }
            Script script = InvokerHelper.createScript(groovyManager.getScriptClassFromLocation(location),
                    GroovyUtil.getBinding(gContext));
            Object resultObj = null;
            if (UtilValidate.isEmpty(invoke)) {
                resultObj = script.run();
            } else {
                resultObj = script.invokeMethod(invoke, EMPTY_ARGS);
            }
            if (resultObj == null) {
                resultObj = scriptContext.getAttribute(ScriptUtil.RESULT_KEY);
            }
            if (resultObj != null && resultObj instanceof Map<?, ?>) {
                return cast(resultObj);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.putAll(makeValid(scriptContext.getBindings(ScriptContext.ENGINE_SCOPE), resultNames));
            return result;
        } catch (GeneralException ge) {
            throw new GenericServiceException(ge);
        } catch (Exception e) {
            // detailMessage can be null.  If it is null, the exception won't be properly returned and logged, and that will
            // make spotting problems very difficult.  Disabling this for now in favor of returning a proper exception.
            throw new GenericServiceException("Error running Groovy method [" + invoke + "] in Groovy source [" + location + "]: ", e);
        }
    }

    private Map<String, Object> makeValid(Map<String, ? extends Object> source, Set<String> resultNames){
        Map<String, Object> resultMap= Maps.newHashMap();
        for (String name:resultNames) {
            if(source.containsKey(name)){
                resultMap.put(name, source.get(name));
            }
        }
        return resultMap;
    }
}
