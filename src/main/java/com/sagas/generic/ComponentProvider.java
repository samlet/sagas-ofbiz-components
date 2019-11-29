package com.sagas.generic;

public interface ComponentProvider {
    <T> T inject(T object);
}

