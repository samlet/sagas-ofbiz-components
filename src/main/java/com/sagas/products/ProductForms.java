package com.sagas.products;

import com.google.common.collect.Maps;
import com.sagas.generic.EntityRoutines;
import com.sagas.meta.DataFormRenderer;
import com.sagas.meta.FormManager;
import com.sagas.meta.model.MetaSingleFormData;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.widget.model.ModelForm;
import org.apache.ofbiz.widget.renderer.FormRenderer;
import org.apache.ofbiz.widget.renderer.fo.FoFormRenderer;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Map;

public class ProductForms {
    public static final String module = FormManager.class.getName();

    @Inject
    FormManager formManager;
    @Inject
    private LocalDispatcher dispatcher;
    @Inject
    private GenericDelegator delegator;
    @Inject
    EntityRoutines entityRoutines;

    ProductForms(){
    }

    public String renderEditProduct(String productId, String localeName) throws Exception {
        final String entityName="Product";
        GenericValue val=entityRoutines.ref(entityName, productId);

        // FoFormRenderer ffr = new FoFormRenderer();
        MetaSingleFormData.Builder formData=MetaSingleFormData.newBuilder();
        DataFormRenderer ffr = new DataFormRenderer(formData);
        ModelForm form=formManager.getModelForm("EditProduct", "component://product/widget/catalog/ProductForms.xml");
        FormRenderer fr = new FormRenderer(form, ffr);
        StringBuilder writer = new StringBuilder();
        Map<String, Object> ctx = formManager.createContext(UtilMisc.ensureLocale(localeName));
        ctx.put("product", val);
        fr.render(writer, ctx);
        return (writer.toString());
    }

    public String renderForm(String formLoc, String localeName) throws Exception{
        MetaSingleFormData.Builder formData=MetaSingleFormData.newBuilder();
        DataFormRenderer ffr = new DataFormRenderer(formData);
        ModelForm form=formManager.getModelForm(formLoc, "component://product/widget/catalog/ProductForms.xml");
        FormRenderer fr = new FormRenderer(form, ffr);
        StringBuilder writer = new StringBuilder();
        Map<String, Object> ctx = formManager.createContext(UtilMisc.ensureLocale(localeName));
        // ctx.put("product", val);
        fr.render(writer, ctx);
        return (writer.toString());
    }
}
