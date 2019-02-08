package com.sagas.generic;

import com.google.common.collect.Maps;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.apache.ofbiz.base.location.FlexibleLocation;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;

import javax.script.ScriptContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GroovyManager {

    public static final String module = GroovyManager.class.getName();

    private final Map<String, Class<?>> parsedScripts = Maps.newHashMap();
    GroovyClassLoader groovyScriptClassLoader;
    private Map<String, String> sources= Maps.newHashMap();
    private static final String scriptBaseClass = "com.sagas.generic.GroovyBaseScript";
    public GroovyManager(){
        GroovyClassLoader groovyClassLoader = null;
        CompilerConfiguration conf = new CompilerConfiguration();
        conf.setScriptBaseClass(scriptBaseClass);
        groovyClassLoader = new GroovyClassLoader(GroovyManager.class.getClassLoader(), conf);

        groovyScriptClassLoader = new GroovyClassLoader(groovyClassLoader);
        /*
         *  With the implementation of @BaseScript annotations (introduced with Groovy 2.3.0) something was broken
         *  in the CompilerConfiguration.setScriptBaseClass method and an error is thrown when our scripts are executed;
         *  the workaround is to execute at startup a script containing the @BaseScript annotation.
         */
        try {
            runScriptAtLocation("component://sagas/config/GroovyInit.groovy", null, null);
        } catch (Exception e) {
            Debug.logWarning("The following error occurred during the initialization of Groovy: " + e.getMessage(), module);
        }
    }

    public void addSource(String location, String source){
        this.parsedScripts.remove(location);
        this.sources.put(location, source);
    }
    public void addFile(String filename) throws IOException {
        InputStream in=new FileInputStream(filename);
        addSource(filename, UtilIO.readString(in));
    }

    public Class<?> getScriptClassFromLocation(String location) throws GeneralException {
        try {
            Class<?> scriptClass = parsedScripts.get(location);
            if (scriptClass == null) {
                String source=null;

                if(location.startsWith("component://")){
                    URL scriptUrl = FlexibleLocation.resolveLocation(location);
                    if (scriptUrl == null) {
                        throw new GeneralException("Script not found at location [" + location + "]");
                    }
                    source = UtilIO.readString(scriptUrl.openStream());
                }else {
                    source = this.sources.get(location);
                    if (source == null) {
                        throw new GeneralException("Script source not found [" + location + "]");
                    }
                }
                //*
                if (groovyScriptClassLoader != null) {
                    Debug.logImportant("use groovy class loader", module);
                    scriptClass = groovyScriptClassLoader.parseClass(source, location);
                } else {
                    scriptClass = parseClass(source, location);
                }
                //*/
                // scriptClass = parseClass(source, location);

                Class<?> scriptClassCached = parsedScripts.putIfAbsent(location, scriptClass);
                if (scriptClassCached == null) { // putIfAbsent returns null if the class is added to the cache
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Cached Groovy script at: " + location, module);
                    }
                } else {
                    // the newly parsed script is discarded and the one found in the cache (that has been created by a concurrent thread in the meantime) is used
                    scriptClass = scriptClassCached;
                }
            }
            return scriptClass;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Error loading Groovy script at [" + location + "]: ", e);
        }
    }

    public Class<?> parseClass(String source, String location) throws IOException {
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
        Class<?> classLoader = groovyClassLoader.parseClass(source, location);
        groovyClassLoader.close();
        return classLoader;
    }

    public void clearCachedClasses(){
        parsedScripts.clear();
    }

    public Object runScriptAtLocation(String location, String methodName, Map<String, Object> context) throws GeneralException {
        Script script = InvokerHelper.createScript(getScriptClassFromLocation(location), getBinding(context));
        Object result = null;
        if (UtilValidate.isEmpty(methodName)) {
            result = script.run();
        } else {
            result = script.invokeMethod(methodName, new Object[] { context });
        }
        return result;
    }

    /** Returns a <code>Binding</code> instance initialized with the
     * variables contained in <code>context</code>. If <code>context</code>
     * is <code>null</code>, an empty <code>Binding</code> is returned.
     * <p>The expression is parsed to initiate non existing variable
     * in <code>Binding</code> to null for GroovyShell evaluation.
     * <p>The <code>context Map</code> is added to the <code>Binding</code>
     * as a variable called "context" so that variables can be passed
     * back to the caller. Any variables that are created in the script
     * are lost when the script ends unless they are copied to the
     * "context" <code>Map</code>.</p>
     *
     * @param context A <code>Map</code> containing initial variables
     * @return A <code>Binding</code> instance
     */
    public Binding getBinding(Map<String, Object> context, String expression) {
        Map<String, Object> vars = new HashMap<>();
        if (context != null) {
            vars.putAll(context);
            if (UtilValidate.isNotEmpty(expression)) {
                //analyse expression to find variables by split non alpha, ignoring "_" to allow my_variable usage
                String [] variables = expression.split("[\\P{Alpha}&&[^_]]+");
                for (String variable: variables) {
                    if(!vars.containsKey(variable)) {
                        vars.put(variable, null);
                    }
                }
            }
            vars.put("context", context);
            if (vars.get(ScriptUtil.SCRIPT_HELPER_KEY) == null) {
                ScriptContext scriptContext = ScriptUtil.createScriptContext(context);
                ScriptHelper scriptHelper = (ScriptHelper)scriptContext.getAttribute(ScriptUtil.SCRIPT_HELPER_KEY);
                if (scriptHelper != null) {
                    vars.put(ScriptUtil.SCRIPT_HELPER_KEY, scriptHelper);
                }
            }
        }
        return new Binding(vars);
    }

    public Binding getBinding(Map<String, Object> context) {
        return getBinding(context, null);
    }
}
