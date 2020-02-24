package com.github.henhal.gson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This defines a data field in an object which is effectively a union -
 * it will typically be declared with an abstract type, and its actual class
 * is determined by looking at the discriminator field.
 * The discriminator must be a field in the same class, and each mapping
 * must map a possible value of the discriminator to a concrete class.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Union {
    /**
     * The name of the field acting as discriminator for this union field.
     * @return Discriminator field name
     */
    String discriminator() default "type";

    /**
     * The list of mappings
     * @return Mappings
     */
    TypeMapping[] mappings();
}
