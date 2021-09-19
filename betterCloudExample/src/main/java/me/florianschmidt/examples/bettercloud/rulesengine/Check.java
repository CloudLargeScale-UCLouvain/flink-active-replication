package me.florianschmidt.examples.bettercloud.rulesengine;

import java.util.function.BiFunction;

public interface Check extends BiFunction<String, String, Boolean> {
}
