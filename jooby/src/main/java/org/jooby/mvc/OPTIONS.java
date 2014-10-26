package org.jooby.mvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP OPTIONS verb for mvc routes.
 * <pre>
 *   class Resources {
 *
 *     &#64;OPTIONS
 *     public void method() {
 *     }
 *   }
 * </pre>
 *
 * @author edgar
 * @since 0.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OPTIONS {
}
