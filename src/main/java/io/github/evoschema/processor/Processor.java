package io.github.evoschema.processor;

import jakarta.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.github.evoschema.processor.dbscanner.IScanner;
import io.github.evoschema.processor.exception.EvoSchemaException;

@Service("scanProcessor")
public class Processor
{
    private static final Logger logger = LoggerFactory.getLogger(Processor.class);

    @Resource(name = "annotationDBScanner")
    private IScanner scanner;

    public void process(String componentName)
    {
        logger.info("scan start process {}", componentName);
        scanner.prepareScanner(componentName);

        try {
            scanner.doScanning();
        } catch (EvoSchemaException e) {
            logger.error("scan scan error in phase:" + e.getReason(), e);
        } catch (Exception e) {
            logger.error("scan fatal error", e);
        }
    }
}

