package io.github.evoschema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(value ={ ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
public @interface TargetDBTemplate
{
    String dataSource() default "";
}

