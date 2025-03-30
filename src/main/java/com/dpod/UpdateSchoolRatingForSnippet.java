package com.dpod;

import com.dpod.bean.School;
import com.dpod.bean.YearConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateSchoolRatingForSnippet {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String jsonPath = "/schools/snippet.json";

        List<YearConfig> yearConfigList = List.of(
                new YearConfig(
                        "Ranking Szkół Podstawowych 2025.html", 2025, School::setRating2025, School::getRating2025
                ),
                new YearConfig(
                        "Ranking Szkół Podstawowych 2024.html", 2024, School::setRating2024, School::getRating2024
                ),
                new YearConfig(
                        "Ranking Szkół Podstawowych 2023.html", 2023, School::setRating2023, School::getRating2023
                ),
                new YearConfig(
                        "Ranking Szkół Podstawowych 2022.html", 2022, School::setRating2022, School::getRating2022
                ),
                new YearConfig(
                        "Ranking Szkół Podstawowych Poznań 2021.html", 2021, School::setRating2021, School::getRating2021
                ),
                new YearConfig(
                        "Ranking Szkół Podstawowych Poznań 2020.html", 2020, School::setRating2020, School::getRating2020
                )
        );

        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<School> schools = OBJECT_MAPPER.readValue(UpdateSchoolRatingForSnippet.class.getResource(jsonPath), new TypeReference<>() {
        });

        for (var yearConfig: yearConfigList) {
            var rankingSetter = yearConfig.rankingSetter();
            var rankingGetter = yearConfig.rankingGetter();
            List<School> schoolWithRatingList = readSchoolRatingList(yearConfig.year(), "/" + yearConfig.fileWithRatings(), rankingSetter);
            schools.forEach(school -> {
                String name = school.name;
                searchSchoolWithRatingByName(name, schoolWithRatingList).ifPresentOrElse(
                        schoolWithRating -> rankingSetter.accept(school, rankingGetter.apply(schoolWithRating)),
                        () -> {
                            System.out.println("Not found school " + name);
                            rankingSetter.accept(school, -1d);
                        });
            });
        }

        calculateAndSetAverageRatingForEachSchool(schools);

        // always sort by the latest year
        schools = schools.stream()
                .sorted(Comparator
                        .comparing(School::getRating2025).reversed()
                        .thenComparing(School::getName))
                .toList();

        String json = OBJECT_MAPPER.writeValueAsString(schools);
        System.out.println(json);
    }

    private static void calculateAndSetAverageRatingForEachSchool(List<School> schools) {
        schools.forEach(school -> {
            double ratingSum = 0;
            int numberOfPresentAnnualRatings = 0;
            if (school.getRating2025() != -1) {
                ratingSum += school.getRating2025();
                numberOfPresentAnnualRatings++;
            }
            if (school.getRating2024() != -1) {
                ratingSum += school.getRating2024();
                numberOfPresentAnnualRatings++;
            }
            if (school.getRating2023() != -1) {
                ratingSum += school.getRating2023();
                numberOfPresentAnnualRatings++;
            }
            if (school.getRating2022() != -1) {
                ratingSum += school.getRating2022();
                numberOfPresentAnnualRatings++;
            }
            if (school.getRating2021() != -1) {
                ratingSum += school.getRating2021();
                numberOfPresentAnnualRatings++;
            }
            if (school.getRating2020() != -1) {
                ratingSum += school.getRating2020();
                numberOfPresentAnnualRatings++;
            }
            school.setNumberOfPresentAnnualRatings(numberOfPresentAnnualRatings);
            school.setAverage(ratingSum == 0 ? -1 : round(ratingSum/numberOfPresentAnnualRatings, 2));
        });
    }

    public static double round(double value, int places) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static Optional<School> searchSchoolWithRatingByName(String name, List<School> schoolWithRatingList) {
        return schoolWithRatingList.stream()
                .filter(schoolWithRating -> schoolWithRating.name.equals(name))
                .findFirst();
    }

    private static List<School> readSchoolRatingList(int year, String filename, BiConsumer<School, Double> rankingSetter) throws IOException, URISyntaxException {
        List<School> schoolWithRatingList = new ArrayList<>();
        // Connect to the webpage and fetch its HTML content
        URL htmlFile = UpdateSchoolRatingForSnippet.class.getResource(filename);
        Document doc = Jsoup.parse(new File(htmlFile.toURI()), StandardCharsets.UTF_8.name());

        // Select all <td> elements
        Elements trElements = doc.select("tr");


        for (Element tr : trElements) {
            Elements tdElements = tr.select("td");

            int maxTdCount = year > 2021 ? 4 : 3;
            int tdIndexForRanking = year > 2021 ? 3 : 2;
            int tdIndexForLink = year > 2021 ? 2 : 1;

            if (tdElements.size() < maxTdCount) {
                continue;
            }

            Element tdWithA = tdElements.get(tdIndexForLink);
            Elements aElements = tdWithA.select("a");
            if (aElements.isEmpty()) {
                continue;
            }
            String text = aElements.get(0).text();
            Element tdRanking = tdElements.get(tdIndexForRanking);
            String ratingAsString = tdRanking.text().split(" ")[0];
            School school = createSchoolRating(text, ratingAsString, rankingSetter);
            schoolWithRatingList.add(school);

            // print school name and ranking
            System.out.println(text + "," + ratingAsString);
        }
        return schoolWithRatingList;
    }


    public static School extractValues(String input) {
        Pattern pattern = Pattern.compile("gm_punkt\\('\\d+','(.*?)','(.*?)', \\\"<b>(.*?)</b>");
        Matcher matcher = pattern.matcher(input);
        String name = "";
        if (matcher.find()) {
            String latitude = matcher.group(1);
            String longitude = matcher.group(2);
            name = matcher.group(3);

            name += " Wrocław";

            System.out.println("Latitude: " + latitude);
            System.out.println("Longitude: " + longitude);
            System.out.println("Name: " + name);

            School school = new School();
            school.setName(name);
            school.setNumber(getSchoolNumber(name));
            school.setLatitude(Double.parseDouble(latitude));
            school.setLongitude(Double.parseDouble(longitude));
            return school;
        } else {
            System.out.println("No match found.");
        }
        throw new IllegalStateException();
    }

    private static School createSchoolRating(String text, String ratingAsString, BiConsumer<School, Double> rankingSetter) {
        double rating = Double.parseDouble(ratingAsString);
        School school = new School();
        school.setName(text);
        school.setNumber(getSchoolNumber(text));
        rankingSetter.accept(school, rating);
        return school;
    }

    private static String getSchoolNumber(String text) {
        Pattern pattern = Pattern.compile("Szkoła Podstawowa nr (.*?) .*");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}