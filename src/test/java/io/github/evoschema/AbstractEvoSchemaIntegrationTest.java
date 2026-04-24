package io.github.evoschema;

import com.atomikos.datasource.RecoverableResource;
import com.atomikos.icatch.config.Configuration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public abstract class AbstractEvoSchemaIntegrationTest
{
    private static final String TEST_DB_PROPERTIES = "unittest/db.properties";
    private static Properties cachedProperties;

    @Before
    public void beforeEach()
    {
        cleanupAtomikosResources();
    }

    @After
    public void afterEach()
    {
        cleanupAtomikosResources();
    }

    protected static void prepareTestEnvironment()
    {
        System.setProperty("profiles.prefixpath", "unittest");
        System.setProperty("spring.profiles.active", "unittest");
        System.setProperty("evoschema.tx.useAtomikos", "true");
    }

    private static void cleanupAtomikosResources()
    {
        try {
            Collection<RecoverableResource> resources = Configuration.getResources();
            if (resources == null || resources.isEmpty()) {
                return;
            }
            for (RecoverableResource resource : new ArrayList<>(resources)) {
                if (resource == null) {
                    continue;
                }
                String name = resource.getName();
                if (name != null && !name.isBlank()) {
                    Configuration.removeResource(name);
                }
                try {
                    resource.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
    }

    protected static Properties loadTestDbProperties()
    {
        if (cachedProperties != null) {
            return cachedProperties;
        }
        Properties properties = new Properties();
        try {
            properties.load(new ClassPathResource(TEST_DB_PROPERTIES).getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("failed to load unittest db.properties", e);
        }
        cachedProperties = properties;
        return cachedProperties;
    }

    protected static void initSchemaForDataSource(String key, String schemaPath)
    {
        DriverManagerDataSource dataSource = buildDataSource(key);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(schemaPath));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    protected static DriverManagerDataSource buildDataSource(String key)
    {
        Properties properties = loadTestDbProperties();
        String prefix = "evoschema.datasource." + key + ".";
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getProperty(prefix + "driverClassName"));
        dataSource.setUrl(properties.getProperty(prefix + "url"));
        dataSource.setUsername(properties.getProperty(prefix + "username"));
        dataSource.setPassword(properties.getProperty(prefix + "password"));
        return dataSource;
    }

    protected JdbcTemplate jdbcTemplate(String key)
    {
        return new JdbcTemplate(buildDataSource(key));
    }

    protected boolean tableExists(String key, String tableName)
    {
        Long count = jdbcTemplate(key).queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Long.class,
                tableName
        );
        return count != null && count > 0L;
    }

    protected boolean columnExists(String key, String tableName, String columnName)
    {
        Long count = jdbcTemplate(key).queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class,
                tableName,
                columnName
        );
        return count != null && count > 0L;
    }
}
