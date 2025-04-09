package com.dpod;

import com.dpod.bean.School;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * todo refactor everything
 * current state of code is worse then the worst but it works :-D
 * To get full data for some particular schools - use UpdateSchoolRatingForSnippet.
 */
public class CreateSchoolRatings {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        var city = "Gdynia";
        var year = 2025;

        String scriptFile = String.format("/js/script_with_geo_%s.js", city);
        String htmlFile = String.format("/html/%s/Ranking Szkół Podstawowych %s %s.html", city, city, year);

        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<School> schoolWithRatingList = new ArrayList<>();
        URL htmlFileUrl = CreateSchoolRatings.class.getResource(htmlFile);
        Document doc = Jsoup.parse(new File(htmlFileUrl.toURI()), StandardCharsets.UTF_8.name());

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
            School school = createSchoolRating(text, ratingAsString);
            schoolWithRatingList.add(school);

            // print school name and ranking
            System.out.println(text + "," + ratingAsString);
        }

        Map<Boolean, List<School>> partitionedList = schoolWithRatingList.stream()
                .collect(Collectors.partitioningBy(school -> StringUtils.isNotBlank(school.number)));
        List<School> schoolsWithNumbers = partitionedList.get(true);
        List<School> schoolsWithoutNumbers = partitionedList.get(false);

        // print json without coordinates
        List<String> lines = Files.readAllLines(Path.of(CreateSchoolRatings.class.getResource(scriptFile).toURI()));
        List<School> schoolWithGeodataList = lines.stream()
                .map(name -> extractValues(name, city))
                .toList();


        Map<String, School> mapByNumber = schoolWithRatingList.stream().collect(Collectors.toMap(school -> {
            if (StringUtils.isNotBlank(school.number)) {
                return school.number;
            }
            return school.name;
        }, school -> school));
        List<School> schoolsWithFullData = new ArrayList<>();
        List<School> schoolsWithoutRating = new ArrayList<>();
        for (var schoolWithGeodata: schoolWithGeodataList) {
            String key = schoolWithGeodata.number;
            School found = mapByNumber.get(key);
            if (found != null) {
                found.longitude = schoolWithGeodata.longitude;
                found.latitude = schoolWithGeodata.latitude;
                schoolsWithFullData.add(found);
            } else {
                key = schoolWithGeodata.name;
                found = mapByNumber.get(key);
                if (found != null) {
                    schoolsWithFullData.add(found);
                } else {
                    schoolsWithoutRating.add(schoolWithGeodata);
                }
            }
            mapByNumber.remove(key);
        }
        List<School> schoolsWithoutGeoData = mapByNumber.values().stream().peek(School::setZeroGeo).toList();

        List.of(
                Triple.<Integer, BiConsumer<School, Double>, Function<School, Double>>of(2024, School::setRating2024, School::getRating2024),
                Triple.<Integer, BiConsumer<School, Double>, Function<School, Double>>of(2023, School::setRating2023, School::getRating2023),
                Triple.<Integer, BiConsumer<School, Double>, Function<School, Double>>of(2022, School::setRating2022, School::getRating2022)
        ).forEach(triple -> RatingUpdater.updateRatingForSchools(triple.getLeft(), triple.getMiddle(), triple.getRight(), schoolsWithFullData));

        String json = OBJECT_MAPPER.writeValueAsString(schoolsWithFullData);
        System.out.println("\n----- The result JSON with schools having both GEO and rating data -----\n");
        System.out.println(json);
        System.out.println("------------------------------------");
        System.out.println("\n----- Schools with rating but without GEO data -----\n");
        schoolsWithoutGeoData.forEach(school -> System.out.println(school.name));
        System.out.println("\n----- Schools with GEO data but without rating -----\n");
        schoolsWithoutRating.forEach(school -> System.out.println(school.name));
    }

    public static School extractValues(String input, String city) {
        Pattern pattern = Pattern.compile("gm_punkt\\('\\d+',\\s?'(.*?)',\\s?'(.*?)',\\s?\"<b>(.*?)</b>");
        Matcher matcher = pattern.matcher(input);
        String name = "";
        if (matcher.find()) {
            String latitude = matcher.group(1);
            String longitude = matcher.group(2);
            name = matcher.group(3);

            name += " " + city;

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

    private static School createSchoolRating(String text, String ratingAsString) {
        double rating = Double.parseDouble(ratingAsString);
        School school = new School();
        school.setName(text);
        school.setNumber(getSchoolNumber(text));
        school.setRating2025(rating);
        return school;
    }

    private static String getSchoolNumber(String text) {
        Pattern pattern = Pattern.compile("Szkoła Podstawowa nr (.*?) .*");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String number = matcher.group(1);
            if (text.contains("Społeczna Szkoła Podstawowa")) {
                return "ssp_" + number;
            }
            return number;
        }
        return null;
    }
}