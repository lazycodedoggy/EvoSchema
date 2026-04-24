package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException;

public interface IScanner
{
    void prepareScanner(String componentName);
    void doScanning() throws EvoSchemaException;
}
