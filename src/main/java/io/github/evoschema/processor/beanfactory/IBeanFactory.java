package io.github.evoschema.processor.beanfactory;

public interface IBeanFactory
{
    <T> T getBean(String beanName, Class<? extends T> clazz, Object... args);

    <T> T getBean(Class<T> clazz, Object... args);

    void registerSingleton(String beanName, Object singletonObject);

    boolean containsBean(String beanName);
}

