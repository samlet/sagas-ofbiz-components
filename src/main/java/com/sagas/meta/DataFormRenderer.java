/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.sagas.meta;

import com.sagas.meta.model.*;
import org.apache.ofbiz.base.util.UtilFormatOut;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.widget.WidgetWorker;
import org.apache.ofbiz.widget.model.FieldInfo;
import org.apache.ofbiz.widget.model.ModelForm;
import org.apache.ofbiz.widget.model.ModelFormField;
import org.apache.ofbiz.widget.model.ModelFormField.*;
import org.apache.ofbiz.widget.model.ModelWidget;
import org.apache.ofbiz.widget.renderer.FormStringRenderer;
import org.apache.ofbiz.widget.renderer.html.HtmlWidgetRenderer;
import org.apache.ofbiz.widget.renderer.macro.MacroScreenRenderer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * Widget Library - FO Form Renderer implementation
 *
 */
public class DataFormRenderer extends HtmlWidgetRenderer implements FormStringRenderer {

    public static final String module = DataFormRenderer.class.getName();

    HttpServletRequest request;
    HttpServletResponse response;
    MetaSingleFormData.Builder formData;

    public DataFormRenderer(MetaSingleFormData.Builder formData) {
        this.widgetCommentsEnabled=false;
        this.formData=formData;
    }

    public void renderBeginningBoundaryComment(Appendable writer, String widgetType, ModelWidget modelWidget) throws IOException {

    }
    public void renderEndingBoundaryComment(Appendable writer, String widgetType, ModelWidget modelWidget) throws IOException {}

    public DataFormRenderer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.request = request;
        this.response = response;
    }

    private void makeBlockString(Appendable writer, String widgetStyle, String text) throws IOException {
        writer.append(" ☌");
        writer.append(UtilFormatOut.encodeXmlValue(text));
    }

    public void renderDisplayField(Appendable writer, Map<String, Object> context, DisplayField displayField) throws IOException {
        ModelFormField modelFormField = displayField.getModelFormField();
        writer.append(String.format("\n+display-field %s", modelFormField.getName()));

        this.makeBlockString(writer, modelFormField.getWidgetStyle(),
                displayField.getDescription(context));
        appendWhitespace(writer);
    }

    public void renderHyperlinkField(Appendable writer, Map<String, Object> context, HyperlinkField hyperlinkField) throws IOException {
        ModelFormField modelFormField = hyperlinkField.getModelFormField();
        writer.append(String.format("\n+hyperlink-field %s", modelFormField.getName()));

        String val=hyperlinkField.getDescription(context);
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("hyperlink")
                .build());
    }

    public void renderMenuField(Appendable writer, Map<String, Object> context, MenuField menuField) throws IOException {
        menuField.renderFieldString(writer, context, null);
    }

    public void renderTextField(Appendable writer, Map<String, Object> context, TextField textField) throws IOException {
        ModelFormField modelFormField = textField.getModelFormField();
        writer.append(String.format("\n+text-field %s", modelFormField.getName()));

        String val=modelFormField.getEntry(context, textField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(),
                val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("text")
                .build());
    }

    public void renderTextareaField(Appendable writer, Map<String, Object> context, TextareaField textareaField) throws IOException {
        ModelFormField modelFormField = textareaField.getModelFormField();
        writer.append(String.format("\n+textarea-field %s", modelFormField.getName()));

        String val=modelFormField.getEntry(context, textareaField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(),
                val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("textarea")
                .build());
    }

    public void renderDateTimeField(Appendable writer, Map<String, Object> context, DateTimeField dateTimeField) throws IOException {
        ModelFormField modelFormField = dateTimeField.getModelFormField();
        writer.append(String.format("\n+date-time-field %s", modelFormField.getName()));

        String val=modelFormField.getEntry(context, dateTimeField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("date-time")
                .build());
    }

    public void renderDropDownField(Appendable writer, Map<String, Object> context, DropDownField dropDownField) throws IOException {
        ModelFormField modelFormField = dropDownField.getModelFormField();
        String currentValue = modelFormField.getEntry(context);

        writer.append(String.format("\n+drop-down-field %s\n", modelFormField.getName()));

        MetaFieldData.Builder fieldData=MetaFieldData.newBuilder()
                .setWidgetType("drop-down")
                .setFieldName(modelFormField.getName());
        MetaListValues.Builder values=MetaListValues.newBuilder();

        List<ModelFormField.OptionValue> allOptionValues = dropDownField.getAllOptionValues(context, WidgetWorker.getDelegator(context));
        // if the current value should go first, display it
        if (UtilValidate.isNotEmpty(currentValue) && "first-in-list".equals(dropDownField.getCurrent())) {
            String explicitDescription = dropDownField.getCurrentDescription(context);
            if (UtilValidate.isNotEmpty(explicitDescription)) {
                this.makeBlockString(writer, modelFormField.getWidgetStyle(),
                        explicitDescription);
                writer.append(String.format("\n\t- current %s", explicitDescription));
                fieldData.setValue(explicitDescription);

            } else {
                String val=FieldInfoWithOptions.getDescriptionForOptionKey(currentValue, allOptionValues);
                this.makeBlockString(writer, modelFormField.getWidgetStyle(),
                        val);
                fieldData.setValue(val);
            }

            //+
            for (ModelFormField.OptionValue optionValue : allOptionValues) {
                writer.append(String.format("\n\t- option %s / %s", optionValue.getKey(), optionValue.getDescription()));
                values.addRows(MetaRow.newBuilder().addValues(optionValue.getKey())
                        .addValues(optionValue.getDescription())
                );
            }
            //+
        } else {
            boolean optionSelected = false;
            for (ModelFormField.OptionValue optionValue : allOptionValues) {
                //+
                writer.append(String.format("\n\t- option %s / %s", optionValue.getKey(), optionValue.getDescription()));
                values.addRows(MetaRow.newBuilder().addValues(optionValue.getKey())
                        .addValues(optionValue.getDescription())
                );

                String noCurrentSelectedKey = dropDownField.getNoCurrentSelectedKey(context);
                fieldData.setValue(noCurrentSelectedKey);
                if ((UtilValidate.isNotEmpty(currentValue) && currentValue.equals(optionValue.getKey()) && "selected".equals(dropDownField.getCurrent())) ||
                        (UtilValidate.isEmpty(currentValue) && noCurrentSelectedKey != null && noCurrentSelectedKey.equals(optionValue.getKey()))) {
                    this.makeBlockString(writer, modelFormField.getWidgetStyle(), optionValue.getDescription());
                    optionSelected = true;
                    //+
                    fieldData.setValue(optionValue.getDescription());
                    break;
                }
            }
            if (!optionSelected) {
                this.makeBlockString(writer, null, "");
                fieldData.setValue("");
            }
        }
        appendWhitespace(writer);

        this.formData.addFields(fieldData.setListValues(values).build());
    }

    public void renderCheckField(Appendable writer, Map<String, Object> context, CheckField checkField) throws IOException {
        ModelFormField modelFormField = checkField.getModelFormField();
        writer.append(String.format("\n+check-field %s", modelFormField.getName()));

        this.makeBlockString(writer, null, "");

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("")
                .setWidgetType("check")
                .build());
    }

    public void renderRadioField(Appendable writer, Map<String, Object> context, RadioField radioField) throws IOException {
        ModelFormField modelFormField = radioField.getModelFormField();
        writer.append(String.format("\n+radio-field %s", modelFormField.getName()));

        this.makeBlockString(writer, null, "");

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("")
                .setWidgetType("radio")
                .build());
    }

    public void renderSubmitField(Appendable writer, Map<String, Object> context, SubmitField submitField) throws IOException {
        ModelFormField modelFormField = submitField.getModelFormField();
        writer.append(String.format("\n+submit-field %s", modelFormField.getName()));

        this.makeBlockString(writer, null, "");

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("Submit")
                .setWidgetType("submit")
                .build());
    }

    public void renderResetField(Appendable writer, Map<String, Object> context, ResetField resetField) throws IOException {
        ModelFormField modelFormField = resetField.getModelFormField();
        writer.append(String.format("\n+reset-field %s", modelFormField.getName()));

        this.makeBlockString(writer, null, "");

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("Reset")
                .setWidgetType("reset")
                .build());
    }

    public void renderHiddenField(Appendable writer, Map<String, Object> context, HiddenField hiddenField) throws IOException {
        ModelFormField modelFormField = hiddenField.getModelFormField();
        writer.append(String.format("\n+hidden-field %s", modelFormField.getName()));

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("")
                .setWidgetType("hidden")
                .build());
    }

    public void renderHiddenField(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, String value) throws IOException {
        writer.append(String.format("\n+hidden-field %s -> %s", modelFormField.getName(), value));
        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(value)
                .setWidgetType("hidden")
                .build());
    }

    public void renderIgnoredField(Appendable writer, Map<String, Object> context, IgnoredField ignoredField) throws IOException {
        ModelFormField modelFormField = ignoredField.getModelFormField();
        writer.append(String.format("\n+ignore-field %s", modelFormField.getName()));
        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("")
                .setWidgetType("ignore")
                .build());
    }

    public void renderFieldTitle(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        String tempTitleText = modelFormField.getTitle(context);
        //+
        writer.append("\n❐ ");
        writer.append(tempTitleText);
    }

    public void renderSingleFormFieldTitle(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        renderFieldTitle(writer, context, modelFormField);
    }

    public void renderFormOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        this.widgetCommentsEnabled = ModelWidget.widgetBoundaryCommentsEnabled(context);
        renderBeginningBoundaryComment(writer, "Form Widget", modelForm);
    }

    public void renderFormClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        renderEndingBoundaryComment(writer, "Form Widget", modelForm);
    }

    public void renderMultiFormClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        renderEndingBoundaryComment(writer, "Form Widget", modelForm);
    }

    public void renderFormatListWrapperOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        writer.append("\n[");
        List<ModelFormField> childFieldList = modelForm.getFieldList();
        for (ModelFormField childField : childFieldList) {
            //+
            writer.append(String.format("\n- child-field %s\n", childField.getName()));

            int childFieldType = childField.getFieldInfo().getFieldType();
            if (childFieldType == FieldInfo.HIDDEN || childFieldType == FieldInfo.IGNORED) {
                writer.append("\n\t- hidden %s\n");
            }
        }
        appendWhitespace(writer);
    }

    public void renderFormatListWrapperClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        writer.append("]\n");
    }
    
    public void renderFormatHeaderOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }
        
    public void renderFormatHeaderClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }

    public void renderFormatHeaderRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
        // appendWhitespace(writer);
    }

    public void renderFormatHeaderRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
        // appendWhitespace(writer);
    }

    public void renderFormatHeaderRowCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, int positionSpan) throws IOException {
        // writer.append("[");
    }

    public void renderFormatHeaderRowCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField) throws IOException {
        // writer.append("]");
    }

    public void renderFormatHeaderRowFormCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }

    public void renderFormatHeaderRowFormCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }

    public void renderFormatHeaderRowFormCellTitleSeparator(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, boolean isLast) throws IOException {
    }

    public void renderFormatItemRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }

    public void renderFormatItemRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }

    public void renderFormatItemRowCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, int positionSpan) throws IOException {
        // writer.append("[");
    }

    public void renderFormatItemRowCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField) throws IOException {
        // writer.append("]");
    }

    public void renderFormatItemRowFormCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }

    public void renderFormatItemRowFormCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }

    // TODO: multi columns (position attribute) in single forms are still not implemented
    public void renderFormatSingleWrapperOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }
    public void renderFormatSingleWrapperClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }

    public void renderFormatFieldRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("[");
    }

    public void renderFormatFieldRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // writer.append("]");
    }


    public void renderFormatFieldRowTitleCellOpen(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        // writer.append("[");
    }

    public void renderFormatFieldRowTitleCellClose(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        // writer.append("]");
    }

    public void renderFormatFieldRowSpacerCell(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
    }

    public void renderFormatFieldRowWidgetCellOpen(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, int positions, int positionSpan, Integer nextPositionInRow) throws IOException {
        // writer.append("[");
    }

    public void renderFormatFieldRowWidgetCellClose(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, int positions, int positionSpan, Integer nextPositionInRow) throws IOException {
        // writer.append("]");
    }

    public void renderFormatEmptySpace(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // TODO
    }

    public void renderTextFindField(Appendable writer, Map<String, Object> context, TextFindField textFindField) throws IOException {
        ModelFormField modelFormField = textFindField.getModelFormField();
        writer.append(String.format("\n+text-find-field %s ", modelFormField.getName()));
        String val=textFindField.getDefaultValue(context);
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), modelFormField.getEntry(context, val));
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("text-find")
                .build());
    }

    public void renderRangeFindField(Appendable writer, Map<String, Object> context, RangeFindField rangeFindField) throws IOException {
        ModelFormField modelFormField = rangeFindField.getModelFormField();
        writer.append(String.format("\n+range-find-field %s ", modelFormField.getName()));
        String val=modelFormField.getEntry(context, rangeFindField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("range-find")
                .build());
    }

    public void renderDateFindField(Appendable writer, Map<String, Object> context, DateFindField dateFindField) throws IOException {
        ModelFormField modelFormField = dateFindField.getModelFormField();
        writer.append(String.format("\n+date-find-field %s ", modelFormField.getName()));
        String val=modelFormField.getEntry(context, dateFindField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("date-find")
                .build());
    }

    public void renderLookupField(Appendable writer, Map<String, Object> context, LookupField lookupField) throws IOException {
        ModelFormField modelFormField = lookupField.getModelFormField();
        writer.append(String.format("\n+lookup-field %s ", modelFormField.getName()));
        String val=modelFormField.getEntry(context, lookupField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("lookup")
                .build());
    }

    public void renderNextPrev(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
    }

    public void renderFileField(Appendable writer, Map<String, Object> context, FileField textField) throws IOException {
        ModelFormField modelFormField = textField.getModelFormField();
        writer.append(String.format("\n+file-field %s ", modelFormField.getName()));
        String val=modelFormField.getEntry(context, textField.getDefaultValue(context));
        this.makeBlockString(writer, modelFormField.getWidgetStyle(), val);
        appendWhitespace(writer);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(val)
                .setWidgetType("file")
                .build());
    }

    public void renderPasswordField(Appendable writer, Map<String, Object> context, PasswordField passwordField) throws IOException {
        ModelFormField modelFormField = passwordField.getModelFormField();
        writer.append(String.format("\n+password-field %s ", modelFormField.getName()));
        this.makeBlockString(writer, null, "");

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue("")
                .setWidgetType("password")
                .build());
    }

    public void renderImageField(Appendable writer, Map<String, Object> context, ImageField imageField) throws IOException {
        ModelFormField modelFormField = imageField.getModelFormField();
        writer.append(String.format("\n+image-field %s ", modelFormField.getName()));

        String value = modelFormField.getEntry(context, imageField.getValue(context));
        this.makeBlockString(writer, null, value);

        this.formData.addFields(MetaFieldData.newBuilder()
                .setFieldName(modelFormField.getName())
                .setValue(value)
                .setWidgetType("image")
                .build());
    }

    public void renderFieldGroupOpen(Appendable writer, Map<String, Object> context, ModelForm.FieldGroup fieldGroup) throws IOException {
        // TODO
    }

    public void renderFieldGroupClose(Appendable writer, Map<String, Object> context, ModelForm.FieldGroup fieldGroup) throws IOException {
        // TODO
    }

    public void renderBanner(Appendable writer, Map<String, Object> context, ModelForm.Banner banner) throws IOException {
        // TODO
        this.makeBlockString(writer, null, "");
    }

    public void renderHyperlinkTitle(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, String titleText) throws IOException {
    }

    public void renderContainerFindField(Appendable writer, Map<String, Object> context, ContainerField containerField) throws IOException {
    }
    public void renderEmptyFormDataMessage(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        // TODO
    }
}
