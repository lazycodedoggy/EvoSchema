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
import java.util.LinkedList;
import java.util.List;

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
        logger.info("scan init scanner: {}", componentName);

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
    }

    @Transactional(rollbackFor = { Exception.class, RuntimeException.class })
    public void doDMLScanning()
    {
        for (Method method : dmlMethods) {
            if (method.isAnnotationPresent(DBDML.class)) {
                JdbcTemplate template = getDBTemplateByDataSource(method);
                processDBDMLs(template, method);
            } else if (method.isAnnotationPresent(DBScript.class)) {
                processDBDMLScript(method);
            }
        }

        while (dmlConfirmMethods.size() > 0) {
            Method confirmMethod = dmlConfirmMethods.pop();
            processDMLConfirm(confirmMethod);
        }
    }

    @Override
    public void doScanning() throws EvoSchemaException
    {
        try {
            for (Method method : preDDLMethods) {
                JdbcTemplate template = getDBTemplateByDataSource(method);
                processDBPreDDL(template, method);
            }

            AnnotationDBScanner owner = beanFactory.getBean("annotationDBScanner", AnnotationDBScanner.class);
            owner.doDMLScanning();
        } catch (EvoSchemaException e) {
            logger.error("scan scan error in phase:" + e.getReason(), e);
            if (e.getReason() != ProcesssError.POST_DLL_ERROR) {
                rollbackPreDDL();
            }
            throw e;
        }

        for (Method method : postDDLMethods) {
            JdbcTemplate template = getDBTemplateByDataSource(method);
            processDBPostDDL(template, method);
        }
    }

    private void processDBDMLs(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            for (String sql : sqls) {
                SqlStatementGuard.validateDmlOnly(componentName + "." + method.getName(), sql, ProcesssError.DML_SQL_ERROR);
                logger.info("scan: execute sql statement \"{}\"", sql);
                template.update(sql);
            }
        } catch (Exception e) {
            logger.error("scan: execute DBDML sql statement error: {}", method.getName(), e);
            throw new EvoSchemaException(ProcesssError.DML_SQL_ERROR, e);
        }
    }

    private void processDBDMLScript(Method method) throws EvoSchemaException
    {
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            logger.info("scan: execute sql script method {}", method.getName());

            Object[] arguments = getJdbcTemplateArguments(method, TemplatePolicy.SCRIPT_DML_PLUS_QUERY);
            method.invoke(scannerObj, arguments);
        } catch (Exception e) {
            logger.error("scan: execute DMLScript sql script method error: {}", method.getName(), e);
            throw new EvoSchemaException(ProcesssError.DML_SCRIPT_ERROR, e);
        }
    }

    private void processDBPreDDL(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        Object scannerObj = beanFactory.getBean(componentName, Object.class);
        try {
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            if (sqls.size() != 2) {
                throw new EvoSchemaException(ProcesssError.PRE_DLL_ERROR);
            }
            logger.info("scan: execute sql statement \"{}\"", sqls.get(0));
            template.execute(sqls.get(0));
            preDDLDoneMethods.add(method);
        } catch (Exception e) {
            logger.error("scan: execute PreDDL sql statement error: {}", method.getName(), e);
            throw new EvoSchemaException(ProcesssError.PRE_DLL_ERROR, e);
        }
    }

    private void processDMLConfirm(Method method) throws EvoSchemaException
    {
        Object scannerObj = beanFactory.getBean(componentName, Object.class);
        try {
            logger.info("scan: execute dml confirm {}", method.getName());

            Object[] arguments = getJdbcTemplateArguments(method, TemplatePolicy.ASSERT_QUERY_ONLY);
            method.invoke(scannerObj, arguments);
        } catch (Exception e) {
            logger.error("scan: execute dml confirm error:{}", method.getName(), e);
            throw new EvoSchemaException(ProcesssError.DML_CONFIRM, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processDBPostDDL(JdbcTemplate template, Method method) throws EvoSchemaException
    {
        try {
            Object scannerObj = beanFactory.getBean(componentName, Object.class);
            List<String> sqls = (List<String>) method.invoke(scannerObj);
            for (String sql : sqls) {
                logger.info("scan: execute sql statement \"{}\"", sql);
                template.execute(sql);
            }
        } catch (Exception e) {
            logger.error("scan: execute DBPostDDL sql statement error:{}", method.getName(), e);
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
        while (preDDLDoneMethods.size() > 0) {
            Method method = preDDLDoneMethods.removeLast();
            try {
                List<String> sqls = (List<String>) method.invoke(scannerObj);
                logger.info("scan: execute sql rollback statement \"{}\"", sqls.get(1));
                JdbcTemplate template = getDBTemplateByDataSource(method);
                if (!"".equals(sqls.get(1))) {
                    template.execute(sqls.get(1));
                }
            } catch (Exception e) {
                logger.error("scan fail to rollback method " + method.getName(), e);
            }
        }
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
            logger.warn("scan: jdbcTemplate bean {} not found, fallback to datasource {}", templateBeanName, dataSourceBeanName);
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
                try {
                    JdbcTemplate template = beanFactory.getBean(templateBeanName, JdbcTemplate.class);
                    return wrapTemplate(policy, template, methodName, dataSourceKey);
                } catch (Exception ex) {
                    String dataSourceBeanName = DataSourceBeanSelector.getDataSourceBeanName(dataSourceKey);
                    DataSource dataSource = beanFactory.getBean(dataSourceBeanName, DataSource.class);
                    logger.warn("scan: jdbcTemplate bean {} not found, fallback to datasource {}", templateBeanName, dataSourceBeanName);
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
            return new QueryOnlyJdbcTemplate(dataSource, componentName + "." + methodName);
        }
        return new RestrictedJdbcTemplate(dataSource, componentName + "." + methodName);
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

