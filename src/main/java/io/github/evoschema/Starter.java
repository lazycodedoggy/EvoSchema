package io.github.evoschema;

import io.github.evoschema.config.DataSourceBeanSelector;
import io.github.evoschema.processor.Processor;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootApplication()
@Import({ DataSourceBeanSelector.class })
public class Starter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Starter.class);

    public void run(ApplicationContext context, String componentName)
    {
        Processor processor = (Processor) context.getBean("scanProcessor");
        processor.process(componentName);
    }

    public static void main(String[] args)
    {
        String componentName;
        if (args.length > 0) {
            componentName = args[0];
        } else {
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            componentName = sdf.format(date);
        }
        DataSourceBeanSelector.setComponentName(componentName);

        try (AnnotationConfigApplicationContext context = (AnnotationConfigApplicationContext) new SpringApplicationBuilder()
                .web(WebApplicationType.NONE).sources(Starter.class).run(args)) {

            Starter starter = new Starter();
            LOGGER.info("EvoSchema start run:{}", componentName);
            starter.run(context, componentName);
        } catch (Exception e) {
            LOGGER.error("Scanning DB Fatal Error:", e);
            throw new IllegalStateException("failed to start EvoSchema for component: " + componentName, e);
        }
    }
}

