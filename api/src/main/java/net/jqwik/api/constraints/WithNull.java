package net.jqwik.api.constraints;

import java.lang.annotation.*;

import org.apiguardian.api.*;

import static org.apiguardian.api.API.Status.*;

/**
 * Allows jqwik to inject null parameters into generated values.
 *
 * Applies to any parameter which is also annotated with {@code @ForAll}.
 *
 * {@code value} specifies the probability between 0 and 1.0 to use for injecting null values.
 *
 * @see net.jqwik.api.ForAll
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@API(status = MAINTAINED, since = "1.0")
public @interface WithNull {
	double value() default 0.1;
}
