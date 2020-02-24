package com.github.henhal.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * A GSON type adapter factory supporting classes with union fields. A union field is a field
 * whose type is determined by another field, e.g.:
 * {
 *   "type": "FOO",
 *   "data": { ... a Foo object or a Bar object or a Baz object ... }
 * }
 *
 * By annotating a field of a class with {@link Union} and supply the name of the discriminator
 * field and the mappings between discriminator values and data classes, this adapter can
 * serialize and deserialize union objects. Optionally the name of the data field may also be
 * different for different discriminator values, e.g.:
 * {
 *     "type": "FOO",
 *     "foo": { ... }
 * }
 *
 *  {
 *     "type": "BAR",
 *     "bar": { ... }
 *  }
 *
 */
public class UnionTypeAdapterFactory implements TypeAdapterFactory {
    protected final WrapperSubTypeAdapter<Object> mWrapperAdapter;
    private final boolean mIncludeInheritedFields;

    private String getSerializedName(Field unionField, TypeMapping mapping) {
        String serializedName = mapping.serializedName();

        return serializedName.isEmpty() ? unionField.getName() : serializedName;
    }

    private TypeMapping getMapping(JsonObject tree, Field unionField) {
        Union union = unionField.getAnnotation(Union.class);
        String typeValue = tree.get(union.discriminator()).getAsString();

        for (TypeMapping mapping : union.mappings()) {
            if (mapping.value().equals(typeValue)) {
                return mapping;
            }
        }

        return null;
    }

    private void wrapElement(JsonObject tree, Field unionField, TypeMapping mapping) {
        // Wrap an element with an object containing that element + the class name of the element
        JsonElement element = tree.remove(getSerializedName(unionField, mapping));
        JsonObject wrapper = mWrapperAdapter.wrapJsonElement(
                new WrapperSubTypeAdapter.TypedData(
                        mapping.type().getName(),
                        element));

        tree.add(unionField.getName(), wrapper);
    }

    private void renameTreeProperty(JsonObject tree, String oldName, String newName) {
        if (!oldName.equals(newName)) {
            JsonElement element = tree.get(oldName);
            tree.add(newName, element);
            tree.remove(oldName);
        }
    }

    private Set<Field> getAnnotatedFields(Set<Field> out,
                                          Class<?> clazz,
                                          Class<? extends Annotation> annotationClass) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotationClass)) {
                out.add(field);
            }
        }

        Class<?> sup = clazz.getSuperclass();

        if (!mIncludeInheritedFields || sup == null) {
            return out;
        }

        return getAnnotatedFields(out, sup, annotationClass);
    }

    private boolean hasField(Class<?> clazz, String name) {
        try {
            clazz.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
        }

        Class<?> sup = clazz.getSuperclass();

        if (!mIncludeInheritedFields || sup == null) {
            return false;
        }

        return hasField(sup, name);
    }

    private void validateUnionFields(Class<?> clazz, Set<Field> unionFields) {
        for (Field field : unionFields) {
            Union union = field.getAnnotation(Union.class);

            if (!hasField(clazz, union.discriminator())) {
                throw new RuntimeException(clazz.getName() +
                        " has @Union field '" + field.getName() + "'" +
                        " with invalid discriminator field '" + union.discriminator() + "'");

            }

            for (TypeMapping mapping : union.mappings()) {
                if (!field.getType().isAssignableFrom(mapping.type())) {
                    throw new RuntimeException(clazz.getName() +
                            " has @Union field '" + field.getName() + "'" +
                            " of type " + field.getType() +
                            " with invalid mapping type " + mapping.type());
                }
            }
        }
    }

    /**
     * Create a union type adapter factory.
     * @param includeInheritedFields If true, inherited union fields are supported
     *                               (impacts general GSON performance)
     */
    public UnionTypeAdapterFactory(boolean includeInheritedFields) {
        mIncludeInheritedFields = includeInheritedFields;
        mWrapperAdapter = new WrapperSubTypeAdapter<>();
    }

    /**
     * Create a union type adapter factory that does not support inherited union fields.
     */
    public UnionTypeAdapterFactory() {
        this(false);
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final Class<? super T> clazz = type.getRawType();
        final Set<Field> unionFields = getAnnotatedFields(new HashSet<>(), clazz, Union.class);

        if (unionFields.isEmpty()) {
            return null;
        }

        validateUnionFields(clazz, unionFields);

        final TypeAdapter<JsonElement> treeAdapter = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                JsonObject tree = gson
                        .getDelegateAdapter(UnionTypeAdapterFactory.this, type)
                        .toJsonTree(value).getAsJsonObject();

                for (Field unionField : unionFields) {
                    TypeMapping mapping = getMapping(tree, unionField);

                    if (mapping != null) {
                        renameTreeProperty(
                                tree,
                                unionField.getName(),
                                getSerializedName(unionField, mapping));
                    }
                }

                treeAdapter.write(out, tree);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                JsonObject tree = treeAdapter.read(in).getAsJsonObject();
                GsonBuilder gb = gson.newBuilder();

                for (Field unionField : unionFields) {
                    TypeMapping mapping = getMapping(tree, unionField);

                    if (mapping != null) {
                        wrapElement(
                                tree,
                                unionField,
                                mapping);

                        // Register temporary type adapter for the data class, which unwraps
                        // the element and deserializes it using the supplied type -
                        // for this deserialization session only.
                        gb.registerTypeAdapter(unionField.getType(), mWrapperAdapter);
                    }
                }

                return gb.create()
                        .getDelegateAdapter(UnionTypeAdapterFactory.this, type)
                        .fromJsonTree(tree);
            }
        };
    }
}