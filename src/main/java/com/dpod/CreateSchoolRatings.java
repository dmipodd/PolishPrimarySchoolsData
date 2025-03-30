package com.dpod;

import com.dpod.bean.School;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * todo refactor everything
 * current state of code is worse then the worst but it works :-D
 * To add a new city - use CreateSchoolRatings. To update existing city rating - run UpdateSchoolRating.
 * To get full data for some particular schools - use UpdateSchoolRatingForSnippet.
 */
public class CreateSchoolRatings {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<School> schoolWithRatingList = new ArrayList<>();
        // Connect to the webpage and fetch its HTML content
        URL htmlFile = CreateSchoolRatings.class.getResource("/Ranking Szkół Podstawowych Poznań 2025.html");
        // taken from https://wielkopolskie.szkolypodstawowe.edubaza.pl/mapa.php?pok=17689&pod=2&c1m=286&c1=15&c2=425
        String scrptFile = "/script_with_geo_poznan.js";
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
        List<String> lines = Files.readAllLines(Path.of(CreateSchoolRatings.class.getResource(scrptFile).toURI()));
        List<School> schoolWithGeodataList = lines.stream()
                .map(CreateSchoolRatings::extractValues)
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

        String json = OBJECT_MAPPER.writeValueAsString(schoolsWithFullData);
        System.out.println(json);
        System.out.println("-----------------------------------------");
        json = OBJECT_MAPPER.writeValueAsString(schoolsWithoutGeoData);
        System.out.println(json);
        System.out.println("-----------------------------------------");
        //.collect(Collectors.partitioningBy(school -> StringUtils.isNotBlank(school.number)));
//        List<School> schoolsWithNumbersFromJS = partitionedList.get(true);
//        List<School> schoolsWithoutNumbersFromJS = partitionedList.get(false);


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