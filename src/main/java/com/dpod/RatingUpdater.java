package com.dpod;

import com.dpod.bean.School;
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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RatingUpdater {

    public static List<School> updateRatingForSchools(int year, BiConsumer<School, Double> rankingSetter, Function<School, Double> rankingGetter, List<School> schools) {
        try {
            List<School> schoolWithRatingList = readSchoolRatingList("/html/Poland/Ranking Szkół Podstawowych " + year + ".html", rankingSetter);
            schools.forEach(school -> {
                String name = school.name;
                searchSchoolWithRatingByName(name, schoolWithRatingList).ifPresentOrElse(
                        schoolWithRating -> rankingSetter.accept(school, rankingGetter.apply(schoolWithRating)),
                        () -> {
                            System.out.println("Not found school " + name);
                            rankingSetter.accept(school, -1d);
                        });
            });

            calculateAndSetAverageRatingForEachSchool(schools);

            // always sort by the latest year
            var schoolsUpdated = schools.stream()
                    .sorted(Comparator
                            .comparing(School::getRating2025).reversed()
                            .thenComparing(School::getName))
                    .toList();
            schools.clear();
            schools.addAll(schoolsUpdated);
            return schools;
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
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
            school.setAverage(ratingSum == 0 ? -1 : round(ratingSum / numberOfPresentAnnualRatings, 2));
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

    private static List<School> readSchoolRatingList(String filename, BiConsumer<School, Double> rankingSetter) throws IOException, URISyntaxException {
        List<School> schoolWithRatingList = new ArrayList<>();
        // Connect to the webpage and fetch its HTML content
        URL htmlFile = UpdateSchoolRating.class.getResource(filename);
        Document doc = Jsoup.parse(new File(htmlFile.toURI()), StandardCharsets.UTF_8.name());

        // Select all <td> elements
        Elements trElements = doc.select("tr");

        // Loop through each <td> element
        for (Element tr : trElements) {
            // Select all <a> elements nested within the current <td> element
            Elements tdElements = tr.select("td");

            if (tdElements.size() < 4) {
                continue;
            }

            // Loop through each <a> element
            Element tdWithA = tdElements.get(2);
            Elements aElements = tdWithA.select("a");
            if (aElements.isEmpty()) {
                continue;
            }
            String text = aElements.get(0).text();
            Element tdRanking = tdElements.get(3);
            String ratingAsString = tdRanking.text().split(" ")[0];
            School school = createSchoolRating(text, ratingAsString, rankingSetter);
            schoolWithRatingList.add(school);

            // print school name and ranking
            System.out.println(text + "," + ratingAsString);
        }
        return schoolWithRatingList;
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
