package com.sagas.generic;

import com.google.common.collect.Maps;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.Converter;
import org.apache.ofbiz.base.conversion.Converters;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.GenericEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.ModelService;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ValueHelper {
    public static void setNumericValue(GenericValue value, String attr, Integer intval){
        value.set(attr, new Long(intval));
    }

    public static void setDate(GenericValue value, String attr, String datestr){
        java.sql.Date date=java.sql.Date.valueOf(datestr);
        value.set(attr, date);
    }

    public static String mapToJson(Map<String,Object> map) throws ConversionException, ClassNotFoundException {
        Converter<Map<String,Object>, JSON> converter = UtilGenerics.cast(Converters.getConverter(Map.class, JSON.class));
        JSON json;
        json = converter.convert(map);
        return json.toString();
    }

    public static String entityToJson(GenericEntity entity, Map<String,Object> metaInfos) throws ConversionException, ClassNotFoundException {
        Converter<Map<String,Object>, JSON> converter = UtilGenerics.cast(Converters.getConverter(Map.class, JSON.class));
        JSON json;

        Map<String,Object> map = Maps.newHashMap();
        map.putAll(entity);
        map.putAll(metaInfos);

        json = converter.convert(map);
        return json.toString();
    }

    public static String valueListToJson(List<GenericValue> valueList) throws ClassNotFoundException, ConversionException {
        Converter<List<GenericValue>, JSON> converter = UtilGenerics.cast(Converters.getConverter(List.class, JSON.class));
        JSON json;
        json = converter.convert(valueList);
        return json.toString();
    }

    public static Map<String, Object> jsonToMap(String jsonStr) throws ClassNotFoundException, IOException, ConversionException {
        Converter<JSON, Map<String,Object>> converter = UtilGenerics.cast(Converters.getConverter(JSON.class, Map.class));
        Map<String,Object> convertedMap;
        JSON json = JSON.from(jsonStr);
        convertedMap = UtilGenerics.toMap(converter.convert(json));
        return convertedMap;
    }

    public static String toJsonMap(Object... parameters) {
        try {
            return mapToJson(UtilGenerics.toMap(String.class, parameters));
        } catch (Exception e) {
           return "{'_result':3}";
        }
    }

}
