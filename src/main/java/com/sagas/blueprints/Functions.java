package com.sagas.blueprints;

import com.sagas.actions.ActionResponse;

public class Functions {
    public static interface ActonApply<I> {
        /**
         * The application to perform.
         *
         * @param i an instance that the application is performed on
         */
        public ActionResponse apply(I i) throws Exception;
    }
}
