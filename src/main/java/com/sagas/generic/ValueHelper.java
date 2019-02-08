package com.sagas.generic;

import com.google.common.collect.Maps;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.Converter;
import org.apache.ofbiz.base.conversion.Converters;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.GenericEntity;
import org.apache.ofbiz.entity.GenericValue;

import java.util.Map;

public class ValueHelper {
    public static void setNumericValue(GenericValue value, String attr, Integer intval){
        value.set(attr, new Long(intval));
    }

    public static void setDate(GenericValue value, String attr, String datestr){
        java.sql.Date date=java.sql.Date.valueOf(datestr);
        value.set(attr, date);
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
}
