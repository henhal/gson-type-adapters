package com.github.henhal.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * This type adapter enables serializing and deserializing of sub-classed objects.
 * The rendered JSON will have a type field designating the type of sub-class, and a data field
 * containing the object itself.
 * Upon deserialization, the type field will be read in order to construct the correct sub-class,
 * and the data field will then be read using the proper sub-class as the deserialization type.
 *
 * The class may be sub-classed to override how classes are converted to/from strings, e.g.
 * using enums. The default implementation uses Class.forName / class.getName() and does not
 * support parameterized types.
 *
 * The names of the type and data fields may be customized by using the
 * {@link WrapperSubTypeAdapter#WrapperSubTypeAdapter(String, String)} constructor.
 * @param <T> Common super-type of the objects serialized by this adapter
 */
@SuppressWarnings("WeakerAccess")
public class WrapperSubTypeAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {
    static class TypedData {
        final String type;
        final JsonElement element;

        TypedData(String type, JsonElement element) {
            this.type = type;
            this.element = element;
        }
    }

    private static final String DEFAULT_PROPERTY_TYPE = "type";
    private static final String DEFAULT_PROPERTY_DATA = "data";

    protected final String mTypeFieldName;
    protected final String mDataFieldName;

    /**
     * Constructor taking custom field names
     * @param typeFieldName Name of the type field
     * @param dataFieldName Name of the data field
     */
    public WrapperSubTypeAdapter(String typeFieldName, String dataFieldName) {
        this.mTypeFieldName = typeFieldName;
        this.mDataFieldName = dataFieldName;
    }

    /**
     * Constructor using default field names
     */
    public WrapperSubTypeAdapter() {
        this(DEFAULT_PROPERTY_TYPE, DEFAULT_PROPERTY_DATA);
    }

    JsonObject wrapJsonElement(TypedData data) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty(DEFAULT_PROPERTY_TYPE, data.type);
        wrapper.add(DEFAULT_PROPERTY_DATA, data.element);

        return wrapper;
    }

    TypedData unwrapJsonElement(JsonObject jsonObj) {
        return new TypedData(
                jsonObj.get(mTypeFieldName).getAsString(),
                jsonObj.get(mDataFieldName)
        );
    }

    /**
     * Serialize the given type into a string representation
     * @param type Type
     * @return Type name
     * @throws JsonParseException If the type could not be serialized
     */
    @SuppressWarnings("unchecked")
    protected String serializeType(Type type) throws JsonParseException {
        return ((Class<? extends T>)type).getName();
    }

    /**
     * Deserialize the given type name into a type
     * @param typeName String representation of the type
     * @return Type
     * @throws JsonParseException If the type could not be deserialized
     */
    protected Type deserializeType(String typeName) throws JsonParseException {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException(e);
        }
    }

    @Override
    public final JsonElement serialize(T object,
                                       Type type,
                                       JsonSerializationContext jsonSerializationContext) {
        return wrapJsonElement(new TypedData(
                serializeType(object.getClass()),
                jsonSerializationContext.serialize(
                        object,
                        object.getClass())
        ));
    }

    @Override
    public T deserialize(JsonElement jsonElement,
                         Type type,
                         JsonDeserializationContext jsonDeserializationContext) {
        TypedData data = unwrapJsonElement(jsonElement.getAsJsonObject());

        return jsonDeserializationContext.deserialize(
                data.element,
                deserializeType(data.type));
    }
}
