package com.sagas.meta;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.collections.FlexibleMapAccessor;
import org.apache.ofbiz.base.util.collections.ResourceBundleMapWrapper;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.widget.model.ModelWidget;
import org.w3c.dom.Element;

import java.util.Locale;
import java.util.Map;

public class PropertiesManager {
    public static final String module = PropertiesManager.class.getName();
    public static class PropertyMap {
        private final FlexibleMapAccessor<ResourceBundleMapWrapper> mapNameAcsr;
        private final FlexibleStringExpander resourceExdr;

        boolean global;

        public PropertyMap(String resource, String mapName, boolean global) {
            this.resourceExdr = FlexibleStringExpander.getInstance(resource);
            this.mapNameAcsr = FlexibleMapAccessor.getInstance(mapName);
            this.global=global;
        }

        public void runAction(Map<String, Object> context) {
            // default to false
            // boolean global = "true".equals(globalStr);
            Locale locale = (Locale) context.get("locale");
            String resource = this.resourceExdr.expandString(context, locale);
            ResourceBundleMapWrapper existingPropMap = this.mapNameAcsr.get(context);
            if (existingPropMap == null) {
                this.mapNameAcsr.put(context, UtilProperties.getResourceBundleMap(resource, locale, context));
            } else {
                try {
                    existingPropMap.addBottomResourceBundle(resource);
                } catch (IllegalArgumentException e) {
                    // log the error, but don't let it kill everything just for a typo or bad char in an l10n file
                    Debug.logError(e, "Error adding resource bundle [" + resource + "]: " + e.toString(), module);
                }
            }
            if (global) {
                Map<String, Object> globalCtx = UtilGenerics.checkMap(context.get("globalContext"));
                if (globalCtx != null) {
                    ResourceBundleMapWrapper globalExistingPropMap = this.mapNameAcsr.get(globalCtx);
                    if (globalExistingPropMap == null) {
                        this.mapNameAcsr.put(globalCtx, UtilProperties.getResourceBundleMap(resource, locale, context));
                    } else {
                        // is it the same object? if not add it in here too...
                        if (existingPropMap != globalExistingPropMap) {
                            try {
                                globalExistingPropMap.addBottomResourceBundle(resource);
                            } catch (IllegalArgumentException e) {
                                // log the error, but don't let it kill everything just for a typo or bad char in an l10n file
                                Debug.logError(e, "Error adding resource bundle [" + resource + "]: " + e.toString(), module);
                            }
                        }
                    }
                }
            }
        }

        public FlexibleMapAccessor<ResourceBundleMapWrapper> getMapNameAcsr() {
            return mapNameAcsr;
        }

        public FlexibleStringExpander getResourceExdr() {
            return resourceExdr;
        }
    }

    // property-map resource="ContentUiLabels" map-name="uiLabelMap" global="true"/>
    public static void execPropertyMap(Map<String, Object> context, String resource, String mapName, boolean global){
        PropertyMap propertyMap=new PropertyMap(resource,mapName,global);
        propertyMap.runAction(context);
    }
}
