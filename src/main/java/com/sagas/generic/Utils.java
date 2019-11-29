package com.sagas.generic;

import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelViewEntity;

public class Utils {
    public static boolean isViewEntity(ModelEntity entity){
        return entity instanceof ModelViewEntity;
    }
}

