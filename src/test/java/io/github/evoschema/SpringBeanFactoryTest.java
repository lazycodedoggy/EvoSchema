package io.github.evoschema;

import io.github.evoschema.processor.beanfactory.SpringBeanFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SpringBeanFactoryTest
{
    @Test
    public void shouldReturnRegisteredSingletonByName()
    {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        SpringBeanFactory beanFactory = new SpringBeanFactory();
        beanFactory.setApplicationContext(context);

        TestCloseableBean bean = new TestCloseableBean();
        beanFactory.registerSingleton("closeableBean", bean);

        TestCloseableBean loadedBean = beanFactory.getBean("closeableBean", TestCloseableBean.class);
        Assert.assertSame(bean, loadedBean);

        context.close();
    }

    @Test
    public void shouldInvokeCloseMethodWhenContextCloses()
    {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        SpringBeanFactory beanFactory = new SpringBeanFactory();
        beanFactory.setApplicationContext(context);

        TestCloseableBean bean = new TestCloseableBean();
        beanFactory.registerSingleton("closeableBean", bean);

        context.close();

        Assert.assertTrue(bean.isClosed());
    }

    @Test
    public void shouldInvokeDestroyMethodWhenContextCloses()
    {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        SpringBeanFactory beanFactory = new SpringBeanFactory();
        beanFactory.setApplicationContext(context);

        TestDestroyBean bean = new TestDestroyBean();
        beanFactory.registerSingleton("destroyBean", bean);

        context.close();

        Assert.assertTrue(bean.isDestroyed());
    }

    private static class TestCloseableBean implements AutoCloseable
    {
        private boolean closed;

        @Override
        public void close()
        {
            closed = true;
        }

        public boolean isClosed()
        {
            return closed;
        }
    }

    private static class TestDestroyBean
    {
        private boolean destroyed;

        public void destroy()
        {
            destroyed = true;
        }

        public boolean isDestroyed()
        {
            return destroyed;
        }
    }
}
