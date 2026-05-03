package io.github.evoschema.processor;

import jakarta.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.github.evoschema.processor.dbscanner.IScanner;
import io.github.evoschema.processor.dbscanner.ExecutionLogHelper;
import io.github.evoschema.processor.exception.EvoSchemaException;

@Service("scanProcessor")
public class Processor
{
    private static final Logger logger = LoggerFactory.getLogger(Processor.class);

    @Resource(name = "annotationDBScanner")
    private IScanner scanner;

    public void process(String componentName)
    {
        logger.info("{}", ExecutionLogHelper.phaseBanner("START", componentName, "PROCESS"));
        scanner.prepareScanner(componentName);

        try {
            scanner.doScanning();
            logger.info("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "PROCESS"));
        } catch (EvoSchemaException e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            String contextPhase = ExecutionLogHelper.currentPhaseOrUnknown();
            String contextMethod = ExecutionLogHelper.currentMethodOrUnknown();
            String contextDataSource = ExecutionLogHelper.currentDataSourceOrUnknown();
            logger.error(
                    "{} reason=\"{}\"",
                    ExecutionLogHelper.methodEvent("FAIL", contextPhase, componentName, contextMethod, contextDataSource),
                    ExecutionLogHelper.summarizeError(rootCause),
                    rootCause
            );
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            String contextPhase = ExecutionLogHelper.currentPhaseOrUnknown();
            String contextMethod = ExecutionLogHelper.currentMethodOrUnknown();
            String contextDataSource = ExecutionLogHelper.currentDataSourceOrUnknown();
            logger.error(
                    "{} reason=\"{}\"",
                    ExecutionLogHelper.methodEvent("FAIL", contextPhase, componentName, contextMethod, contextDataSource),
                    ExecutionLogHelper.summarizeError(rootCause),
                    rootCause
            );
        } finally {
            ExecutionLogHelper.clearContext();
        }
    }
}
