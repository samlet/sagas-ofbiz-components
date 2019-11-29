package com.sagas.generic;

import com.google.protobuf.ByteString;
import com.sagas.meta.model.TaFieldValue;
import com.sagas.meta.model.TaJsonEntity;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.entity.GenericValue;

public class ProtoValueUtil {
    public static ByteString toPayload(String val){
        ByteString payload= TaFieldValue.newBuilder().setStringVal(val).build().toByteString();
        return payload;
    }
    public static ByteString toPayload(int val){
        ByteString payload= TaFieldValue.newBuilder().setIntVal(val).build().toByteString();
        return payload;
    }
    public static ByteString toPayload(double val){
        ByteString payload= TaFieldValue.newBuilder().setDoubleVal(val).build().toByteString();
        return payload;
    }
    public static ByteString toPayload(byte[] val){
        ByteString input=ByteString.copyFrom(val);
        ByteString payload= TaFieldValue.newBuilder().setBlob(input).build().toByteString();
        return payload;
    }
    public static ByteString toPayload(GenericValue val) throws ClassNotFoundException, ConversionException {
        ByteString payload= TaJsonEntity.newBuilder()
                .setEntityName(val.getEntityName())
                .setJson(ValueHelper.mapToJson(val))
                .build().toByteString();
        return payload;
    }

    public static String defStr(String val){
        if(val==null){
            return "";
        }
        return val;
    }

    public static int defInt(Integer val){
        if(val==null){
            return 0;
        }
        return val;
    }
}
