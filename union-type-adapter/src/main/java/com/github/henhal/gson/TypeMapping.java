package com.github.henhal.gson;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a mapping between a discriminator value and a concrete class.
 * This makes it possible to have union data where one field dictates the type
 * of another field.
 * If required, serializedName may be used to customize the name of the object; otherwise
 * the name of the field will be used.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeMapping {
    /**
     * The discriminator value
     * @return Discriminator value
     */
    String value();

    /**
     * The class mapped to the value. This must be a sub-class of the type of the union field.
     * @return Class
     */
    Class<?> type();

    /**
     * The name of the serialized property. If omitted, the field's name will be used.
     * @return Name
     */
    String serializedName() default "";
}
