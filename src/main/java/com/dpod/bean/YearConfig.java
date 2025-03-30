package com.dpod.bean;

import java.util.function.BiConsumer;
import java.util.function.Function;

public record YearConfig(String fileWithRatings,
                          int year,
                          BiConsumer<School, Double> rankingSetter,
                          Function<School, Double> rankingGetter){

}