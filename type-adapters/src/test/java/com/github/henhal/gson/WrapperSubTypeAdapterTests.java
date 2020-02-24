package com.github.henhal.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;

class Animal {
}

class Dog extends Animal {
    int postmanHuntCount;

    Dog(int postmanHuntCount) {
        this.postmanHuntCount = postmanHuntCount;
    }
}

class Cat extends Animal {
    boolean hasEvilPlan;

    Cat(boolean hasEvilPlan) {
        this.hasEvilPlan = hasEvilPlan;
    }
}

public class WrapperSubTypeAdapterTests {
    @Test
    public void testSerialization() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Animal.class, new WrapperSubTypeAdapter<Animal>())
                .create();

        Cat cat = new Cat(true);
        String json = gson.toJson(cat, Animal.class);

        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertEquals(Cat.class.getName(), jsonObject.get("type").getAsString());
        Assert.assertEquals(cat.hasEvilPlan, jsonObject.getAsJsonObject("data").get("hasEvilPlan").getAsBoolean());

        Animal animal = gson.fromJson(json, Animal.class);
        Assert.assertEquals(Cat.class, animal.getClass());
        Assert.assertEquals(cat.hasEvilPlan, ((Cat)animal).hasEvilPlan);

        Dog dog = new Dog(42);
        json = gson.toJson(dog, Animal.class);

        jsonObject = gson.fromJson(json, JsonObject.class);
        Assert.assertEquals(Dog.class.getName(), jsonObject.get("type").getAsString());
        Assert.assertEquals(dog.postmanHuntCount, jsonObject.getAsJsonObject("data").get("postmanHuntCount").getAsInt());

        animal = gson.fromJson(json, Animal.class);
        Assert.assertEquals(Dog.class, animal.getClass());
        Assert.assertEquals(dog.postmanHuntCount, ((Dog)animal).postmanHuntCount);


    }

}

