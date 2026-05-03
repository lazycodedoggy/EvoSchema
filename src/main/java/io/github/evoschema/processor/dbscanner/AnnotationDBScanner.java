package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.annotation.DBDML;
import io.github.evoschema.annotation.DBDMLAssert;
import io.github.evoschema.annotation.DBPOSTDDL;
import io.github.evoschema.annotation.DBPREDDL;
import io.github.evoschema.annotation.DBScript;
import io.github.evoschema.annotation.TargetDBTemplate;
import io.github.evoschema.config.DataSourceBeanSelector;
import io.github.evoschema.processor.beanfactory.IBeanFactory;
import io.github.evoschema.processor.exception.EvoSchemaException;
import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Service("annotationDBScanner")
public class AnnotationDBScanner implements IScanner
{
    private static final Logger logger = LoggerFactory.getLogger(AnnotationDBScanner.class);

    @Resource(name = "springBeanFactory")
    private IBeanFactory beanFactory;

    private String componentName;

    private LinkedList<Method> preDDLMethods = new LinkedList<>();

    private LinkedList<Method> preDDLDoneMethods = new LinkedList<>();

    private LinkedList<Method> dmlMethods = new LinkedList<>();

    private LinkedList<Method> dmlConfirmMethods = new LinkedList<>();

    private LinkedList<Method> postDDLMethods = new LinkedList<>();

    private enum TemplatePolicy
    {
        NONE,
        SCRIPT_DML_PLUS_QUERY,
        ASSERT_QUERY_ONLY
    }

    @Override
    public void prepareScanner(String componentName)
    {
        logger.info("{}", ExecutionLogHelper.phaseBanner("PREPARE", componentName, "DISCOVERY"));

        if (preDDLMethods.size() > 0 || dmlMethods.size() > 0 || dmlConfirmMethods.size() > 0 || postDDLMethods.size() > 0) {
            throw new IllegalAccessError("The previous scanner is not stopped!");
        }
        this.componentName = componentName;

        Object scannerObj = beanFactory.getBean(componentName, Object.class);

        Class<?> clazz = scannerObj.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(DBScript.class) || method.isAnnotationPresent(DBDML.class)) {
                dmlMethods.add(method);
            } else if (method.isAnnotationPresent(DBPREDDL.class)) {
                preDDLMethods.add(method);
            } else if (method.isAnnotationPresent(DBPOSTDDL.class)) {
                postDDLMethods.add(method);
            } else if (method.isAnnotationPresent(DBDMLAssert.class)) {
                dmlConfirmMethods.add(method);
            }
        }

        dmlMethods.sort(new MethodOrderCmp());
        preDDLMethods.sort(new MethodOrderCmp());
        postDDLMethods.sort(new MethodOrderCmp());
        dmlConfirmMethods.sort(new MethodOrderCmp());
        logger.info(
                "{} preDDL={}, dmlOrScript={}, dmlAssert={}, postDDL={}",
                ExecutionLogHelper.phaseBanner("READY", componentName, "DISCOVERY"),
                preDDLMethods.size(),
                dmlMethods.size(),
                dmlConfirmMethods.size(),
                postDDLMethods.size()
        );
    }

    @Transactional(rollbackFor = { Exception.class, RuntimeException.class })
    public void doDMLScanning()
    {
        logger.info("{}", ExecutionLogHelper.phaseBanner("START", componentName, "DML"));
        for (Method method : dmlMethods) {
            if (method.isAnnotationPresent(DBDML.class)) {
                JdbcTemplate template = getDBTemplateByDataSource(method);
                processDBDMLs(template, method);
            } else if (method.isAnnotationPresent(DBScript.class)) {
                processDBDMLScript(method);
            }
        }
        logger.info("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "DML"));

        logger.info("{}", ExecutionLogHelper.phaseBanner("START", componentName, "DML-ASSERT"));
        while (dmlConfirmMethods.size() > 0) {
            Method confirmMethod = dmlConfirmMethods.pop();
            processDMLConfirm(confirmMethod);
        }
        logger.info("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "DML-ASSERT"));
    }

    @Override
    public void doScanning() throws EvoSchemaException
    {
        try {
            logger.info("{}", ExecutionLogHelper.phaseBanner("START", componentName, "PRE-DDL"));
            for (Method method : preDDLMethods) {
                JdbcTemplate template = getDBTemplateByDataSource(method);
                processDBPreDDL(template, method);
            }
            logger.info("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "PRE-DDL"));

            AnnotationDBScanner owner = beanFactory.getBean("annotationDBScanner", AnnotationDBScanner.class);
            owner.doDMLScanning();
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
            if (e.getReason() != ProcesssError.POST_DLL_ERROR) {
                rollbackPreDDL();
            }
            ExecutionLogHelper.markContext(contextPhase, contextMethod, contextDataSource);
            throw e;
        }

        logger.info("{}", ExecutionLogHelper.phaseBanner("START", componentName, "POST-DDL"));
        for (Method method : postDDLMethods) {
            JdbcTemplate template = getDBTemplateByDataSource(method);
            processDBPostDDL(template, method);
        }
        logger.info("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "POST-DDL"));
    }

    private void processDBDMLs(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        String dataSourceKey = getDataSourceKey(method);
        String methodName = method.getName();
        String currentSql = null;
        ExecutionLogHelper.markContext("DML", methodName, dataSourceKey);
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            logger.info("{}", ExecutionLogHelper.methodEvent("START", "DML", componentName, methodName, dataSourceKey));
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            for (String sql : sqls) {
                currentSql = sql;
                SqlStatementGuard.validateDmlOnly(componentName + "." + method.getName(), sql, ProcesssError.DML_SQL_ERROR);
                logger.info("{}", ExecutionLogHelper.sqlEvent("START", "DML", componentName, methodName, dataSourceKey, sql));
                int affectedRows = template.update(sql);
                logger.info(
                        "{} affectedRows={}",
                        ExecutionLogHelper.sqlEvent("SUCCESS", "DML", componentName, methodName, dataSourceKey, sql),
                        affectedRows
                );
            }
            logger.info("{}", ExecutionLogHelper.methodEvent("SUCCESS", "DML", componentName, methodName, dataSourceKey));
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            logger.error("{}", ExecutionLogHelper.failureEvent("DML", componentName, methodName, dataSourceKey, currentSql, rootCause), rootCause);
            throw new EvoSchemaException(ProcesssError.DML_SQL_ERROR, e);
        }
    }

    private void processDBDMLScript(Method method) throws EvoSchemaException
    {
        String methodName = method.getName();
        String dataSourceSummary = summarizeMethodDataSources(method);
        ExecutionLogHelper.markContext("DBSCRIPT", methodName, dataSourceSummary);
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            logger.info("{}", ExecutionLogHelper.methodEvent("START", "DBSCRIPT", componentName, methodName, dataSourceSummary));

            Object[] arguments = getJdbcTemplateArguments(method, TemplatePolicy.SCRIPT_DML_PLUS_QUERY);
            method.invoke(scannerObj, arguments);
            logger.info("{}", ExecutionLogHelper.methodEvent("SUCCESS", "DBSCRIPT", componentName, methodName, dataSourceSummary));
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            String contextDataSource = ExecutionLogHelper.currentDataSourceOrUnknown();
            logger.error("{}", ExecutionLogHelper.failureEvent("DBSCRIPT", componentName, methodName, contextDataSource, null, rootCause), rootCause);
            throw new EvoSchemaException(ProcesssError.DML_SCRIPT_ERROR, e);
        }
    }

    private void processDBPreDDL(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        String dataSourceKey = getDataSourceKey(method);
        String methodName = method.getName();
        String currentSql = null;
        Object scannerObj = beanFactory.getBean(componentName, Object.class);
        ExecutionLogHelper.markContext("PRE-DDL", methodName, dataSourceKey);
        try {
            logger.info("{}", ExecutionLogHelper.methodEvent("START", "PRE-DDL", componentName, methodName, dataSourceKey));
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            if (sqls.size() != 2) {
                throw new EvoSchemaException(ProcesssError.PRE_DLL_ERROR);
            }
            currentSql = sqls.get(0);
            logger.info("{}", ExecutionLogHelper.sqlEvent("START", "PRE-DDL", componentName, methodName, dataSourceKey, currentSql));
            template.execute(currentSql);
            logger.info("{}", ExecutionLogHelper.sqlEvent("SUCCESS", "PRE-DDL", componentName, methodName, dataSourceKey, currentSql));
            preDDLDoneMethods.add(method);
            logger.info("{}", ExecutionLogHelper.methodEvent("SUCCESS", "PRE-DDL", componentName, methodName, dataSourceKey));
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            logger.error("{}", ExecutionLogHelper.failureEvent("PRE-DDL", componentName, methodName, dataSourceKey, currentSql, rootCause), rootCause);
            throw new EvoSchemaException(ProcesssError.PRE_DLL_ERROR, e);
        }
    }

    private void processDMLConfirm(Method method) throws EvoSchemaException
    {
        String methodName = method.getName();
        Object scannerObj = beanFactory.getBean(componentName, Object.class);
        String dataSourceSummary = summarizeMethodDataSources(method);
        ExecutionLogHelper.markContext("DML-ASSERT", methodName, dataSourceSummary);
        try {
            logger.info("{}", ExecutionLogHelper.methodEvent("START", "DML-ASSERT", componentName, methodName, dataSourceSummary));

            Object[] arguments = getJdbcTemplateArguments(method, TemplatePolicy.ASSERT_QUERY_ONLY);
            method.invoke(scannerObj, arguments);
            logger.info("{}", ExecutionLogHelper.methodEvent("SUCCESS", "DML-ASSERT", componentName, methodName, dataSourceSummary));
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            String contextDataSource = ExecutionLogHelper.currentDataSourceOrUnknown();
            logger.error("{}", ExecutionLogHelper.failureEvent("DML-ASSERT", componentName, methodName, contextDataSource, null, rootCause), rootCause);
            throw new EvoSchemaException(ProcesssError.DML_CONFIRM, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processDBPostDDL(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        String dataSourceKey = getDataSourceKey(method);
        String methodName = method.getName();
        String currentSql = null;
        ExecutionLogHelper.markContext("POST-DDL", methodName, dataSourceKey);
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            logger.info("{}", ExecutionLogHelper.methodEvent("START", "POST-DDL", componentName, methodName, dataSourceKey));
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            for (String sql : sqls) {
                currentSql = sql;
                logger.info("{}", ExecutionLogHelper.sqlEvent("START", "POST-DDL", componentName, methodName, dataSourceKey, sql));
                template.execute(sql);
                logger.info("{}", ExecutionLogHelper.sqlEvent("SUCCESS", "POST-DDL", componentName, methodName, dataSourceKey, sql));
            }
            logger.info("{}", ExecutionLogHelper.methodEvent("SUCCESS", "POST-DDL", componentName, methodName, dataSourceKey));
        } catch (Exception e) {
            Throwable rootCause = ExecutionLogHelper.unwrap(e);
            logger.error("{}", ExecutionLogHelper.failureEvent("POST-DDL", componentName, methodName, dataSourceKey, currentSql, rootCause), rootCause);
            throw new EvoSchemaException(ProcesssError.POST_DLL_ERROR, e);
        }
    }

    private int getOrderFromAnnotation(Method method)
    {
        if (method.isAnnotationPresent(DBScript.class)) {
            return method.getDeclaredAnnotation(DBScript.class).order();
        } else if (method.isAnnotationPresent(DBDML.class)) {
            return method.getDeclaredAnnotation(DBDML.class).order();
        } else if (method.isAnnotationPresent(DBPREDDL.class)) {
            return method.getDeclaredAnnotation(DBPREDDL.class).order();
        } else if (method.isAnnotationPresent(DBPOSTDDL.class)) {
            return method.getDeclaredAnnotation(DBPOSTDDL.class).order();
        }  else if (method.isAnnotationPresent(DBDMLAssert.class)) {
            return method.getDeclaredAnnotation(DBDMLAssert.class).order();
        }
        throw new IllegalArgumentException();
    }

    @SuppressWarnings("unchecked")
    private void rollbackPreDDL()
    {
        Object scannerObj = beanFactory.getBean(componentName, Object.class);
        logger.warn("{}", ExecutionLogHelper.phaseBanner("START", componentName, "PRE-DDL-ROLLBACK"));
        while (preDDLDoneMethods.size() > 0) {
            Method method = preDDLDoneMethods.removeLast();
            String dataSourceKey = getDataSourceKey(method);
            String methodName = method.getName();
            ExecutionLogHelper.markContext("PRE-DDL-ROLLBACK", methodName, dataSourceKey);
            try {
                List<String> sqls = (List<String>) method.invoke(scannerObj);
                String rollbackSql = sqls.get(1);
                JdbcTemplate template = getDBTemplateByDataSource(method);
                if (StringUtils.isBlank(rollbackSql)) {
                    logger.warn("{}", ExecutionLogHelper.rollbackSkip(componentName, methodName, dataSourceKey));
                } else {
                    logger.warn("{}", ExecutionLogHelper.sqlEvent("START", "PRE-DDL-ROLLBACK", componentName, methodName, dataSourceKey, rollbackSql));
                    template.execute(rollbackSql);
                    logger.warn("{}", ExecutionLogHelper.sqlEvent("SUCCESS", "PRE-DDL-ROLLBACK", componentName, methodName, dataSourceKey, rollbackSql));
                }
            } catch (Exception e) {
                Throwable rootCause = ExecutionLogHelper.unwrap(e);
                logger.error("{}", ExecutionLogHelper.failureEvent("PRE-DDL-ROLLBACK", componentName, methodName, dataSourceKey, null, rootCause), rootCause);
            } finally {
                ExecutionLogHelper.clearContext();
            }
        }
        logger.warn("{}", ExecutionLogHelper.phaseBanner("SUCCESS", componentName, "PRE-DDL-ROLLBACK"));
    }

    private JdbcTemplate getDBTemplateByDataSource(AnnotatedElement annotatedElement)
    {
        TargetDBTemplate targetDBTemplate = AnnotatedElementUtils.findMergedAnnotation(annotatedElement, TargetDBTemplate.class);
        if (targetDBTemplate == null) {
            throw new EvoSchemaException(ProcesssError.COMPONENT_ANNOTATION_ERROR);
        }
        if (StringUtils.isBlank(targetDBTemplate.dataSource())) {
            throw new EvoSchemaException(ProcesssError.COMPONENT_ANNOTATION_ERROR, "@TargetDBTemplate.dataSource is blank");
        }
        String dataSourceKey = targetDBTemplate.dataSource().trim();
        String templateBeanName = DataSourceBeanSelector.getJdbcTemplateBeanName(dataSourceKey);
        try {
            return beanFactory.getBean(templateBeanName, JdbcTemplate.class);
        } catch (Exception ex) {
            String dataSourceBeanName = DataSourceBeanSelector.getDataSourceBeanName(dataSourceKey);
            DataSource dataSource = beanFactory.getBean(dataSourceBeanName, DataSource.class);
            logger.warn(
                    "{}",
                    ExecutionLogHelper.templateFallback(
                            componentName,
                            getElementName(annotatedElement),
                            dataSourceKey,
                            templateBeanName,
                            dataSourceBeanName
                    )
            );
            return new JdbcTemplate(dataSource);
        }
    }

    private Object[] getJdbcTemplateArguments(Method method, TemplatePolicy policy)
    {
        Object[] arguments = new Object[method.getParameterCount()];
        Annotation[][] elements = method.getParameterAnnotations();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = getDBTemplateByParamAnnotation(elements[i], policy, method.getName());
        }
        return arguments;
    }

    private JdbcTemplate getDBTemplateByParamAnnotation(Annotation[] annotations, TemplatePolicy policy, String methodName)
    {
        for (Annotation annotation : annotations) {
            if (annotation instanceof TargetDBTemplate) {
                TargetDBTemplate targetDBTemplate = (TargetDBTemplate) annotation;
                if (StringUtils.isBlank(targetDBTemplate.dataSource())) {
                    throw new EvoSchemaException(ProcesssError.COMPONENT_ANNOTATION_ERROR, "@TargetDBTemplate.dataSource is blank");
                }
                String dataSourceKey = targetDBTemplate.dataSource().trim();
                String templateBeanName = DataSourceBeanSelector.getJdbcTemplateBeanName(dataSourceKey);
                logger.info("{}", ExecutionLogHelper.templateBinding(componentName, methodName, dataSourceKey, policy.name()));
                try {
                    JdbcTemplate template = beanFactory.getBean(templateBeanName, JdbcTemplate.class);
                    return wrapTemplate(policy, template, methodName, dataSourceKey);
                } catch (Exception ex) {
                    String dataSourceBeanName = DataSourceBeanSelector.getDataSourceBeanName(dataSourceKey);
                    DataSource dataSource = beanFactory.getBean(dataSourceBeanName, DataSource.class);
                    logger.warn(
                            "{}",
                            ExecutionLogHelper.templateFallback(
                                    componentName,
                                    methodName,
                                    dataSourceKey,
                                    templateBeanName,
                                    dataSourceBeanName
                            )
                    );
                    JdbcTemplate template = new JdbcTemplate(dataSource);
                    return wrapTemplate(policy, template, methodName, dataSourceKey);
                }
            }
        }

        return null;
    }

    private JdbcTemplate wrapTemplate(TemplatePolicy policy, JdbcTemplate template, String methodName, String dataSourceKey)
    {
        if (template == null) {
            return null;
        }
        if (policy == TemplatePolicy.NONE) {
            return template;
        }
        if (policy == TemplatePolicy.SCRIPT_DML_PLUS_QUERY && template instanceof RestrictedJdbcTemplate) {
            return template;
        }
        if (policy == TemplatePolicy.ASSERT_QUERY_ONLY && template instanceof QueryOnlyJdbcTemplate) {
            return template;
        }
        DataSource dataSource = template.getDataSource();
        if (dataSource == null) {
            throw new EvoSchemaException(ProcesssError.DML_SCRIPT_ERROR, "dataSource is null for " + dataSourceKey + " in " + methodName);
        }
        if (policy == TemplatePolicy.ASSERT_QUERY_ONLY) {
            return new QueryOnlyJdbcTemplate(dataSource, componentName + "." + methodName, dataSourceKey);
        }
        return new RestrictedJdbcTemplate(dataSource, componentName + "." + methodName, dataSourceKey);
    }

    private String getDataSourceKey(AnnotatedElement annotatedElement)
    {
        TargetDBTemplate targetDBTemplate = AnnotatedElementUtils.findMergedAnnotation(annotatedElement, TargetDBTemplate.class);
        if (targetDBTemplate == null || StringUtils.isBlank(targetDBTemplate.dataSource())) {
            return "<unknown>";
        }
        return targetDBTemplate.dataSource().trim();
    }

    private String getElementName(AnnotatedElement annotatedElement)
    {
        if (annotatedElement instanceof Method) {
            return ((Method) annotatedElement).getName();
        }
        return annotatedElement.toString();
    }

    private String summarizeMethodDataSources(Method method)
    {
        Set<String> dataSources = new LinkedHashSet<>();
        Annotation[][] parameters = method.getParameterAnnotations();
        for (Annotation[] parameterAnnotations : parameters) {
            for (Annotation annotation : parameterAnnotations) {
                if (annotation instanceof TargetDBTemplate) {
                    String dataSource = ((TargetDBTemplate) annotation).dataSource();
                    if (StringUtils.isNotBlank(dataSource)) {
                        dataSources.add(dataSource.trim());
                    }
                }
            }
        }
        if (dataSources.isEmpty()) {
            return "<unknown>";
        }
        return String.join(",", dataSources);
    }

    private class MethodOrderCmp implements Comparator<Method>
    {
        @Override
        public int compare(Method m1, Method m2)
        {
            int order1 = getOrderFromAnnotation(m1);
            int order2 = getOrderFromAnnotation(m2);
            return order1 - order2;
        }
    }
}
