package com.sagas.meta;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actions.RemoteAction;
import com.sagas.generic.ValueHelper;
import com.sagas.meta.model.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.widget.model.*;
import org.apache.ofbiz.widget.renderer.FormRenderer;
import org.apache.ofbiz.widget.renderer.fo.FoFormRenderer;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.sagas.generic.ProtoValueUtil.defInt;
import static com.sagas.generic.ProtoValueUtil.defStr;

@Singleton
public class FormManager {
    public static final String module = FormManager.class.getName();

    public static class FormResult{
        public FormResult(MetaSingleFormData formData, String repr) {
            this.formData = formData;
            this.repr = repr;
        }

        private MetaSingleFormData formData;

        public MetaSingleFormData getFormData() {
            return formData;
        }

        public String getRepr() {
            return repr;
        }

        private String repr;
    }

    private LocalDispatcher dispatcher;
    private GenericDelegator delegator;
    private final String[] defaultResourceMaps = new String[]{
            "SetupUiLabels",
            "WebtoolsUiLabels",
            "CommonUiLabels",
            "ContentUiLabels",
            "PartyUiLabels",
            "ProductUiLabels",
            "AccountingUiLabels"};

    @Inject
    FormManager(GenericDelegator delegator, LocalDispatcher dispatcher) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
    }

    // <include-form name="EditBlog" location="component://content/widget/forum/BlogForms.xml"/>
    public ModelForm getModelForm(String name, String location) throws IOException, SAXException, ParserConfigurationException {
        return FormFactory.getFormFromLocation(location, name, delegator.getModelReader(),
                dispatcher.getDispatchContext());
    }
    public ModelGrid getModelGrid(String name, String location) throws ParserConfigurationException, SAXException, IOException {
        return GridFactory.getGridFromLocation(location, name, delegator.getModelReader(),
                dispatcher.getDispatchContext());
    }

    public Map<String, Object> createContext(Locale locale) {
        Map<String, Object> ctx = Maps.newHashMap();
        if (locale != null) {
            ctx.put("locale", locale);
        }
        ctx.put("delegator", delegator);
        ctx.put("dispatcher", dispatcher);
        /*
        <!-- base/top/specific map first, then more common map added for shared labels -->
            <property-map resource="SetupUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="WebtoolsUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="CommonUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="ContentUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="PartyUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="ProductUiLabels" map-name="uiLabelMap" global="true"/>
            <property-map resource="AccountingUiLabels" map-name="uiLabelMap" global="true"/>
         */
        // PropertiesManager.execPropertyMap(ctx, "ContentUiLabels", "uiLabelMap", true);
        for (String res : defaultResourceMaps) {
            PropertiesManager.execPropertyMap(ctx, res, "uiLabelMap", true);
        }
        return ctx;
    }

    public String renderForm(ModelForm form, Locale locale) throws Exception {
        // FoFormRenderer ffr = new FoFormRenderer();
        MetaSingleFormData.Builder formData=MetaSingleFormData.newBuilder();
        DataFormRenderer ffr = new DataFormRenderer(formData);
        FormRenderer fr = new FormRenderer(form, ffr);
        StringBuilder writer = new StringBuilder();
        Map<String, Object> ctx = createContext(locale);
        fr.render(writer, ctx);
        return (writer.toString());
    }

    public FormResult renderFormData(String formName, String location, String localeName, Map<String, Object> params) throws Exception {
        MetaSingleFormData.Builder formData=MetaSingleFormData.newBuilder();
        DataFormRenderer ffr = new DataFormRenderer(formData);
        ModelForm form=getModelForm(formName, location);
        FormRenderer fr = new FormRenderer(form, ffr);
        StringBuilder writer = new StringBuilder();
        Map<String, Object> ctx = createContext(UtilMisc.ensureLocale(localeName));
        if(params!=null) {
            ctx.putAll(params);
        }
        // ctx.put("product", val);
        fr.render(writer, ctx);
        return new FormResult(formData.build(), writer.toString());
    }

    //
    @RemoteAction
    public ActionResponse renderFormData(ActionRequest request) throws Exception {
        FormDataRequestor requestor = FormDataRequestor.parseFrom(request.getPayload());

        String[] parts = StringUtils.split(requestor.getUri(), ';');
        Debug.logImportant(String.format("Retrieve %s, %s, %s", parts[0], parts[1], parts[2]), module);
        Preconditions.checkArgument(parts.length == 3, "Error uri pattern: %s", requestor.getUri());
        String formName=parts[1];
        String location=parts[0];
        String localeName=parts[2];
        Map<String, Object> valMap = null;
        if(UtilValidate.isNotEmpty(requestor.getJsonParameters())) {
            valMap=ValueHelper.jsonToMap(requestor.getJsonParameters());
        }

        FormResult result=renderFormData(formName, location, localeName, valMap);
        return new ActionResponse(0, result.getFormData().toByteString());
    }

    public String renderFormData(String formName, String location, String localeName) throws Exception{
        return renderFormData(formName, location, localeName, null).repr;
    }

    public Map<String, String> getFieldTitles(ModelForm form, String localeName) {
        Map<String, String> result = Maps.newHashMap();
        List<ModelFormField> flds = form.getFieldList();
        Map<String, Object> ctx = createContext(UtilMisc.ensureLocale(localeName));
        for (ModelFormField fld : flds) {
            result.put(fld.getName(), fld.getTitle(ctx));
        }
        return result;
    }

    // form uri: "component://content/widget/forum/BlogForms.xml;EditBlog;zh_CN"
    // grid uri: "component://webtools/widget/ServiceForms.xml;@JobManagerLockEnable;zh_CN"
    public MetaForm getMetaForm(String uri) throws IOException, SAXException, ParserConfigurationException {
        String[] parts = StringUtils.split(uri, ';');
        Debug.logImportant(String.format("Retrieve %s, %s, %s", parts[0], parts[1], parts[2]), module);
        Preconditions.checkArgument(parts.length == 3, "Error uri pattern: %s", uri);
        return getMetaForm(parts[1], parts[0], parts[2]);
    }

    @RemoteAction
    public ActionResponse getMetaForm(ActionRequest request) throws IOException, GeneralSecurityException, ClassNotFoundException, ConversionException, ParserConfigurationException, SAXException {
        TaFieldValue token=TaFieldValue.parseFrom(request.getPayload());
        MetaForm payload=getMetaForm(token.getStringVal());
        return new ActionResponse(0, payload.toByteString());
    }

    /**
     * Get a form descriptor
     * @param name Will get a grid if the name is starts with '@' symbol
     * @param location
     * @param localeName
     * @return
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public MetaForm getMetaForm(String name, String location, String localeName) throws IOException, SAXException, ParserConfigurationException {
        ModelForm modelForm =null;
        if(name.startsWith("@")){
            modelForm=getModelGrid(name.substring(1), location);
        }else {
            modelForm = getModelForm(name, location);
        }
        MetaForm.Builder metaForm = MetaForm.newBuilder();
        Map<String, Object> ctx = createContext(UtilMisc.ensureLocale(localeName));

        Preconditions.checkNotNull(modelForm, "form %s;%s absent", location, name);
        // Optional.ofNullable(modelForm.getName()).ifPresent(metaForm::setName);

        metaForm.setName(modelForm.getName())
                .setTitle(defStr(modelForm.getTitle()))
                .setTarget(defStr(modelForm.getTarget()))
                .setType(defStr(modelForm.getType()))
                .setDefaultMapName(defStr(modelForm.getDefaultMapName()));
        modelForm.getFieldList().forEach((fld) -> {
            MetaFormField.Builder metaFld = metaForm.addFieldsBuilder().setName(fld.getName())
                    .setTitle(fld.getTitle(ctx))
                    .setTitleOriginal(defStr(fld.getTitle().getOriginal()))
                    .setRequiredField(fld.getRequiredField())
                    .setTooltip(defStr(fld.getTooltip(ctx)));
            FieldInfo fldInfo = fld.getFieldInfo();
            if(fldInfo!=null) {
                metaFld.setFieldTypeValue(defInt(fld.getFieldInfo().getFieldType()));

                switch (fldInfo.getFieldType()) {
                    case FieldInfo.DISPLAY:
                        ModelFormField.DisplayField displayField = (ModelFormField.DisplayField) fldInfo;
                        metaFld.setDisplayField(MetaDisplayField.newBuilder()
                                .setType(displayField.getType()));
                        break;
                    case FieldInfo.TEXT:
                        ModelFormField.TextField textField = (ModelFormField.TextField) fldInfo;
                        metaFld.setTextField(MetaTextField.newBuilder()
                                .setDefaultValue(defStr(textField.getDefaultValue(ctx)))
                                .setMaxLength(defInt(textField.getMaxlength()))
                        );

                        break;
                    case FieldInfo.DATE_TIME:
                        ModelFormField.DateTimeField dateTimeField = (ModelFormField.DateTimeField) fldInfo;
                        metaFld.setDateTimeField(MetaDateTimeField.newBuilder()
                                .setClock(defStr(dateTimeField.getClock()))
                                .setInputMethod(defStr(dateTimeField.getInputMethod()))
                                .setMask(defStr(dateTimeField.getMask()))
                                .setStep(defStr(dateTimeField.getStep()))
                                .setType(defStr(dateTimeField.getType()))
                                .setDefaultValue(defStr(dateTimeField.getDefaultValue(ctx))));
                        break;
                    case FieldInfo.DROP_DOWN:
                        ModelFormField.DropDownField field = (ModelFormField.DropDownField) fldInfo;
                        metaFld.setDropDownField(MetaDropDownField.newBuilder()
                                .setAllowEmpty(field.getAllowEmpty())
                                .setAllowMulti(field.getAllowMulti())
                                .setCurrent(defStr(field.getCurrent()))
                                .setCurrentDescription(defStr(field.getCurrentDescription(ctx)))
                                .setSize(defStr(field.getSize()))
                                .setTextSize(defStr(field.getTextSize()))
                                .setOtherFieldSize(field.getOtherFieldSize())
                        );
                        break;
                    default:
                        break;
                }
            }
        });
        return metaForm.build();
    }
}
