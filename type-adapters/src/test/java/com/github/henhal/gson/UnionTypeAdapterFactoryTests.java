package com.github.henhal.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

enum Type {FOO, BAR}
abstract class Data {}
class Foo extends Data {
    public final String value;

    Foo(String value) {
        this.value = value;
    }
}

class UnionObject {
    Type type;

    @Union(discriminator = "type", mappings = {
            @TypeMapping(value = "FOO", type = Foo.class),
            @TypeMapping(value = "BAR", type = Bar.class)
    })
    Data data;

    UnionObject(Type type, Data data) {
        this.type = type;
        this.data = data;
    }
}

class ConflictingTypesUnionObject extends UnionObject {
    Type type2;

    // Two union fields of the type Data in the same object is not supported
    @Union(discriminator = "type2", mappings = {
            @TypeMapping(value = "FOO", type = Foo.class),
            @TypeMapping(value = "BAR", type = Bar.class)
    })
    Data data2;

    ConflictingTypesUnionObject() {
        super(Type.FOO, new Foo("conflicting types"));
        type2 = Type.BAR;
        data2 = new Bar(Bar.BarType.METAL, new Bar.Metal("aluminium"));
    }
}

class InvalidUnionDiscriminatorObject {
    // Invalid discriminator
    @Union(discriminator = "not_exists", mappings = {
            @TypeMapping(value = "FOO", type = Foo.class),
            @TypeMapping(value = "BAR", type = Bar.class)
    })
    Data data3 = new Foo("invalid discriminator");
}

class InheritedUnionObject extends UnionObject {
    InheritedUnionObject() {
        super(Type.FOO, new Foo("inherited"));
    }
}

class Bar extends Data {
    enum BarType {METAL, CHOCOLATE}
    public static class Data {}
    public static class Metal extends Data {
        final String material;

        public Metal(String material) {
            this.material = material;
        }
    }
    public static class Chocolate extends Data {}

    final BarType barType;

    @Union(discriminator = "barType", mappings = {
            @TypeMapping(value = "METAL", type = Metal.class, serializedName = "metal"),
            @TypeMapping(value = "CHOCOLATE", type = Chocolate.class, serializedName = "chocolate")
    })
    final Data data;

    Bar(BarType barType, Data data) {
        this.barType = barType;
        this.data = data;
    }
}

class InvalidMappingObject {
    String type;

    @Union(discriminator = "type", mappings = {
            @TypeMapping(value = "foo", type = Foo.class),
            @TypeMapping(value = "is", type = InputStream.class) // Not a sub-class of Data
    })
    Data data;
}

public class UnionTypeAdapterFactoryTests {
    private Gson getGson(boolean includeInheritedFields) {
        return new GsonBuilder()
                .registerTypeAdapterFactory(new UnionTypeAdapterFactory(includeInheritedFields))
                .create();
    }

    @Test
    public void testSimple() {
        Gson gson = getGson(false);

        // Serialize
        UnionObject obj = new UnionObject(Type.FOO, new Foo("simple"));
        String json = gson.toJson(obj);
        Assert.assertNotNull(json);
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertEquals("FOO", jsonObject.get("type").getAsString());
        JsonObject jsonFoo = jsonObject.getAsJsonObject("data");
        Assert.assertNotNull(jsonFoo);
        Assert.assertEquals("simple", jsonFoo.get("value").getAsString());

        // Deserialize
        UnionObject o = gson.fromJson(json, UnionObject.class);
        Assert.assertNotNull(o);
        Assert.assertEquals(Type.FOO, o.type);
        Assert.assertEquals(Foo.class, o.data.getClass());
        Foo foo = (Foo)o.data;
        Assert.assertEquals("simple", foo.value);
    }

    @Test
    public void testInheritedUnionFields() {
        Gson gson = getGson(true);

        String json = gson.toJson(new InheritedUnionObject());
        Assert.assertEquals("{\"type\":\"FOO\",\"data\":{\"value\":\"inherited\"}}", json);

        InheritedUnionObject u = gson.fromJson(json, InheritedUnionObject.class);
        Assert.assertEquals(Foo.class, u.data.getClass());
    }

    @Test
    public void testDuplicateUnionTypes() {
        Gson gson = getGson(true);
        String json = gson.toJson(new ConflictingTypesUnionObject());

        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("FOO", jsonObject.get("type").getAsString());
        Assert.assertEquals("BAR", jsonObject.get("type2").getAsString());
        JsonObject data = jsonObject.getAsJsonObject("data");
        Assert.assertNotNull(data);
        Assert.assertEquals("conflicting types", data.get("value").getAsString());
        JsonObject data2 = jsonObject.getAsJsonObject("data2");
        Assert.assertNotNull(data2);
        Assert.assertEquals("METAL", data2.get("barType").getAsString());
        JsonObject metal = data2.getAsJsonObject("metal");
        Assert.assertNotNull(metal);
        Assert.assertEquals("aluminium", metal.get("material").getAsString());
    }

    @Test
    public void testInvalidDiscriminatorUnion() {
        try {
            String json = getGson(false).toJson(new InvalidUnionDiscriminatorObject());
            Assert.fail("Exception not thrown with invalid Union object");
            Assert.assertNotNull(json);
        } catch (Exception e) {
            Assert.assertTrue("Unexpected exception message for invalid Union object",
                    e.getMessage().contains("invalid discriminator"));
        }
    }

    @Test
    public void testCustomSerializedName() {
        Gson gson = getGson(false);

        // Serialize
        Bar metalBar = new Bar(Bar.BarType.METAL, new Bar.Metal("iron"));
        String json = gson.toJson(metalBar);
        Assert.assertNotNull(json);
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("METAL", jsonObject.get("barType").getAsString());
        JsonObject jsonMetal = jsonObject.getAsJsonObject("metal");
        Assert.assertNotNull(jsonMetal);

        // Deserialize
        Bar bar = gson.fromJson(json, Bar.class);
        Assert.assertEquals(Bar.BarType.METAL, bar.barType);
        Assert.assertEquals(Bar.Metal.class, bar.data.getClass());
        Assert.assertEquals("iron", ((Bar.Metal)bar.data).material);
    }

    @Test
    public void testNestedUnion() {
        Gson gson = getGson(false);

        // Serialize
        UnionObject obj = new UnionObject(Type.BAR, new Bar(
                Bar.BarType.METAL,
                new Bar.Metal("iron")));
        String json = gson.toJson(obj);
        Assert.assertNotNull(json);
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertEquals("BAR", jsonObject.get("type").getAsString());
        JsonObject jsonBar = jsonObject.getAsJsonObject("data");
        Assert.assertNotNull(jsonBar);
        Assert.assertEquals("METAL", jsonBar.get("barType").getAsString());
        JsonObject jsonMetal = jsonBar.getAsJsonObject("metal");
        Assert.assertNotNull(jsonMetal);

        // Deserialize
        UnionObject o = gson.fromJson(json, UnionObject.class);
        Assert.assertNotNull(o);
        Assert.assertEquals(Type.BAR, o.type);
        Assert.assertEquals(Bar.class, o.data.getClass());
        Bar bar = (Bar)o.data;
        Assert.assertEquals(Bar.BarType.METAL, bar.barType);
        Assert.assertEquals(Bar.Metal.class, bar.data.getClass());
        Assert.assertEquals("iron", ((Bar.Metal)bar.data).material);
    }

    @Test
    public void testInvalidMapping() {
        Gson gson = getGson(false);

        try {
            InvalidMappingObject obj = new InvalidMappingObject();
            gson.toJson(obj);
            Assert.assertTrue("Exception not thrown with invalid mapping object", false);
        } catch (Exception e) {
        }
    }
}
