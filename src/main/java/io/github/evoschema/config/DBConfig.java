package io.github.evoschema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration("dbConfig")
@EnableTransactionManagement(proxyTargetClass = true)
@PropertySource("classpath:/${profiles.prefixpath}/db.properties")
public class DBConfig
{
}

