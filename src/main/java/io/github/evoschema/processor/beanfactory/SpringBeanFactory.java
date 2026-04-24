package io.github.evoschema.processor.beanfactory;

import java.lang.reflect.Method;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component("springBeanFactory")
public class SpringBeanFactory implements IBeanFactory, ApplicationContextAware
{
    private static ApplicationContext context;

    @Override
    public <T> T getBean(String beanName, Class<? extends T> clazz, Object... args)
    {
        Object bean;
        if (args == null || args.length == 0) {
            bean = context.getBean(beanName);
        } else {
            bean = context.getBean(beanName, args);
        }
        if (clazz.isAssignableFrom(bean.getClass())) {
            return clazz.cast(bean);
        }
        throw new ClassCastException("can not cast " + beanName + " to " + clazz.getName());
    }

    @Override
    public <T> T getBean(Class<T> clazz, Object... args)
    {
        if (args == null || args.length == 0) {
            return context.getBean(clazz);
        }
        return context.getBean(clazz, args);
    }

    @Override
    public void registerSingleton(String beanName, Object singletonObject)
    {
        if (!(context instanceof ConfigurableApplicationContext)) {
            throw new IllegalStateException("ApplicationContext is not configurable");
        }
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) context).getBeanFactory();
        beanFactory.registerSingleton(beanName, singletonObject);
        registerDestroyCallback(beanFactory, beanName, singletonObject);
    }

    @Override
    public boolean containsBean(String beanName)
    {
        if (!(context instanceof ConfigurableApplicationContext)) {
            return context.containsBean(beanName);
        }
        return ((ConfigurableApplicationContext) context).getBeanFactory().containsBean(beanName);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        SpringBeanFactory.context = applicationContext;
    }

    private void registerDestroyCallback(ConfigurableListableBeanFactory beanFactory, String beanName, Object singletonObject)
    {
        Method destroyMethod = findDestroyMethod(singletonObject);
        if (destroyMethod == null || !(beanFactory instanceof DefaultSingletonBeanRegistry)) {
            return;
        }
        ((DefaultSingletonBeanRegistry) beanFactory).registerDisposableBean(beanName, () -> invokeDestroy(singletonObject, destroyMethod));
    }

    private Method findDestroyMethod(Object singletonObject)
    {
        if (singletonObject instanceof AutoCloseable) {
            try {
                return singletonObject.getClass().getMethod("close");
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
        try {
            return singletonObject.getClass().getMethod("destroy");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void invokeDestroy(Object singletonObject, Method destroyMethod) throws Exception
    {
        destroyMethod.setAccessible(true);
        destroyMethod.invoke(singletonObject);
    }
}

