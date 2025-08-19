package com.example.akthosidle.data.loaders;

public final class SchemaValidator {
    private SchemaValidator() {}
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
