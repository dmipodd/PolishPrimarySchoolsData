package com.dpod;

import com.dpod.bean.School;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class UpdateSchoolRating {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String jsonPath = "/json/Warsaw/schools.json";
        int year = 2025;
        BiConsumer<School, Double> rankingSetter = School::setRating2025;
        Function<School, Double> rankingGetter = School::getRating2025;

        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<School> schools = OBJECT_MAPPER.readValue(UpdateSchoolRating.class.getResource(jsonPath), new TypeReference<>() {
        });

        schools = RatingUpdater.updateRatingForSchools(year, rankingSetter, rankingGetter, schools);

        String json = OBJECT_MAPPER.writeValueAsString(schools);
        System.out.println(json);
    }
}