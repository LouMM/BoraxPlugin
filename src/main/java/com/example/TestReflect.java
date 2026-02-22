package com.example;

import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

public class TestReflect {
    public static void main(String[] args) {
        for (Method m : ItemStack.class.getDeclaredMethods()) {
            if (m.getName().contains("parse")) {
                System.out.println(m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }
}
