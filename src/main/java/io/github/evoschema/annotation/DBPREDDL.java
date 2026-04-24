package io.github.evoschema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@TargetDBTemplate
@Retention(RetentionPolicy.RUNTIME)
@Target(value ={ElementType.METHOD})
public @interface DBPREDDL
{
    int order() default 0;

    @AliasFor(annotation = TargetDBTemplate.class, attribute = "dataSource")
    String dataSource() default "";
}
