package io.github.evoschema.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import io.github.evoschema.annotation.TargetDBTemplate;
import io.github.evoschema.processor.beanfactory.IBeanFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
public class DataSourceBeanSelector
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceBeanSelector.class);

    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String MYSQL_XA_DATASOURCE = "com.mysql.cj.jdbc.MysqlXADataSource";

    private static final String TX_MANAGER = "transactionManager";

    private static String componentName;

    private final Environment environment;

    @Value("${evoschema.tx.useAtomikos:true}")
    private boolean useAtomikos;

    @Resource(name = "springBeanFactory")
    private IBeanFactory beanFactory;

    public DataSourceBeanSelector(Environment environment)
    {
        this.environment = environment;
    }

    public static void setComponentName(String componentName)
    {
        DataSourceBeanSelector.componentName = componentName;
    }

    public static String getDataSourceBeanName(String dataSourceKey)
    {
        return dataSourceKey + "DataSource";
    }

    public static String getJdbcTemplateBeanName(String dataSourceKey)
    {
        return dataSourceKey + "JdbcTemplate";
    }

    @PostConstruct
    public void init()
    {
        if (StringUtils.isBlank(componentName)) {
            throw new IllegalArgumentException("componentName is blank");
        }
        registerBeans();
    }

    private void registerBeans()
    {
        LOGGER.info("start register datasource");
        Object bean = beanFactory.getBean(componentName, Object.class);
        Set<TemplateBinding> bindings = collectBindings(bean.getClass());

        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("No datasource binding found in component: " + componentName);
        }

        boolean needAtomikos = bindings.size() > 1 && useAtomikos;
        for (TemplateBinding binding : bindings) {
            DBProperties properties = loadDbProperties(binding.getDataSourceKey());
            if (needAtomikos) {
                registerAtomikosDatasourceBean(properties, binding.getDataSourceBeanName());
            } else {
                registerNormalDatasourceBean(properties, binding.getDataSourceBeanName());
            }
            registerJdbcTemplateBean(binding.getDataSourceBeanName(), binding.getTemplateBeanName());
        }

        if (needAtomikos) {
            registerAtomikosTxManager();
        } else {
            registerNormalTxManager(bindings.iterator().next().getDataSourceBeanName());
        }
        LOGGER.info("finish register datasource");
    }

    private Set<TemplateBinding> collectBindings(Class<?> clazz)
    {
        Set<TemplateBinding> bindings = new LinkedHashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            TargetDBTemplate methodBinding = AnnotatedElementUtils.findMergedAnnotation(method, TargetDBTemplate.class);
            if (methodBinding != null) {
                bindings.add(toBinding(methodBinding));
            }

            for (Annotation[] annotations : method.getParameterAnnotations()) {
                for (Annotation annotation : annotations) {
                    if (annotation instanceof TargetDBTemplate) {
                        bindings.add(toBinding((TargetDBTemplate) annotation));
                    }
                }
            }
        }
        return bindings;
    }

    private TemplateBinding toBinding(TargetDBTemplate targetDBTemplate)
    {
        if (StringUtils.isBlank(targetDBTemplate.dataSource())) {
            throw new IllegalArgumentException("@TargetDBTemplate.dataSource must not be blank");
        }
        String dataSourceKey = targetDBTemplate.dataSource().trim();
        return new TemplateBinding(dataSourceKey, getDataSourceBeanName(dataSourceKey), getJdbcTemplateBeanName(dataSourceKey));
    }

    private DBProperties loadDbProperties(String dataSourceKey)
    {
        String modernPrefix = "evoschema.datasource." + dataSourceKey + ".";
        String legacyPrefix = dataSourceKey + ".jdbc.";

        String url = firstNonBlank(environment.getProperty(modernPrefix + "url"), environment.getProperty(legacyPrefix + "url"));
        String username = firstNonBlank(environment.getProperty(modernPrefix + "username"), environment.getProperty(legacyPrefix + "username"));
        String password = environment.getProperty(modernPrefix + "password");
        if (password == null) {
            password = environment.getProperty(legacyPrefix + "password");
        }
        String driverClassName = firstNonBlank(environment.getProperty(modernPrefix + "driverClassName"),
                environment.getProperty(legacyPrefix + "driverClassName"), DEFAULT_DRIVER);

        if (StringUtils.isBlank(url) || StringUtils.isBlank(username) || password == null) {
            throw new IllegalArgumentException("Datasource config missing for key: " + dataSourceKey);
        }
        return new DBProperties(driverClassName, url, username, password);
    }

    private String firstNonBlank(String... values)
    {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void registerNormalDatasourceBean(DBProperties properties, String beanName)
    {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        beanFactory.registerSingleton(beanName, dataSource);
        LOGGER.info("register normal datasource {} success", beanName);
    }

    private void registerAtomikosDatasourceBean(DBProperties properties, String beanName)
    {
        AtomikosDataSourceBean atomikosDataSourceBean = new AtomikosDataSourceBean();
        atomikosDataSourceBean.setXaDataSourceClassName(resolveXaDataSourceClassName(properties.getDriverClassName()));
        atomikosDataSourceBean.setXaProperties(buildXaProperties(properties));
        atomikosDataSourceBean.setUniqueResourceName(beanName);
        atomikosDataSourceBean.setMaxPoolSize(20);
        beanFactory.registerSingleton(beanName, atomikosDataSourceBean);
        LOGGER.info("register atomikos datasource {} success", beanName);
    }

    private String resolveXaDataSourceClassName(String driverClassName)
    {
        if (driverClassName.contains("mysql")) {
            return MYSQL_XA_DATASOURCE;
        }
        throw new IllegalArgumentException("unsupported XA driverClassName (only mysql is supported): " + driverClassName);
    }

    private Properties buildXaProperties(DBProperties properties)
    {
        Properties xaProperties = new Properties();
        xaProperties.setProperty("url", properties.getUrl());
        xaProperties.setProperty("user", properties.getUsername());
        xaProperties.setProperty("password", properties.getPassword());
        xaProperties.setProperty("pinGlobalTxToPhysicalConnection", "true");
        return xaProperties;
    }

    private void registerJdbcTemplateBean(String dataSourceBeanName, String templateBeanName)
    {
        DataSource dataSource = beanFactory.getBean(dataSourceBeanName, DataSource.class);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        beanFactory.registerSingleton(templateBeanName, jdbcTemplate);
        LOGGER.info("register jdbc template {} success", templateBeanName);
    }

    private void registerNormalTxManager(String dataSourceBeanName)
    {
        DataSource bean = beanFactory.getBean(dataSourceBeanName, DataSource.class);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(bean);
        beanFactory.registerSingleton(TX_MANAGER, manager);
        LOGGER.info("register normal tx manager success");
    }

    private void registerAtomikosTxManager()
    {
        if (beanFactory.containsBean(TX_MANAGER)) {
            LOGGER.info("use existing transactionManager bean in atomikos mode");
            return;
        }
        LOGGER.info("atomikos mode enabled, wait for Spring Boot auto transactionManager");
    }

    private static class DBProperties
    {
        private final String driverClassName;

        private final String url;

        private final String username;

        private final String password;

        private DBProperties(String driverClassName, String url, String username, String password)
        {
            this.driverClassName = driverClassName;
            this.url = url;
            this.username = username;
            this.password = password;
        }

        public String getDriverClassName()
        {
            return driverClassName;
        }

        public String getUrl()
        {
            return url;
        }

        public String getUsername()
        {
            return username;
        }

        public String getPassword()
        {
            return password;
        }
    }

    private static class TemplateBinding
    {
        private final String dataSourceKey;

        private final String dataSourceBeanName;

        private final String templateBeanName;

        private TemplateBinding(String dataSourceKey, String dataSourceBeanName, String templateBeanName)
        {
            this.dataSourceKey = dataSourceKey;
            this.dataSourceBeanName = dataSourceBeanName;
            this.templateBeanName = templateBeanName;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            TemplateBinding other = (TemplateBinding) obj;
            return getDataSourceBeanName().equals(other.getDataSourceBeanName());
        }

        @Override
        public int hashCode()
        {
            return getDataSourceBeanName().hashCode();
        }

        public String getDataSourceKey()
        {
            return dataSourceKey;
        }

        public String getDataSourceBeanName()
        {
            return dataSourceBeanName;
        }

        public String getTemplateBeanName()
        {
            return templateBeanName;
        }
    }
}
