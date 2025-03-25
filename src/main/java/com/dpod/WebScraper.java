package com.dpod;

import com.dpod.bean.SchoolRating;
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

public class WebScraper {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<SchoolRating> schoolRatingList = new ArrayList<>();
        // Connect to the webpage and fetch its HTML content
        URL htmlFile = WebScraper.class.getResource("/Ranking Szkół Podstawowych Wrocław 2025.html");
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
            SchoolRating schoolRating = createSchoolRating(text, ratingAsString);
            schoolRatingList.add(schoolRating);

            // print school name and ranking
            System.out.println(text + "," + ratingAsString);
        }

        Map<Boolean, List<SchoolRating>> partitionedList = schoolRatingList.stream()
                .collect(Collectors.partitioningBy(schoolRating -> StringUtils.isNotBlank(schoolRating.number)));
        List<SchoolRating> schoolsWithNumbers = partitionedList.get(true);
        List<SchoolRating> schoolsWithoutNumbers = partitionedList.get(false);

        // print json without coordinates
        String json = OBJECT_MAPPER.writeValueAsString(schoolRatingList);
        System.out.println(json);

        List<String> lines = Files.readAllLines(Path.of(WebScraper.class.getResource("/scholls_coordinates.js").toURI()));
        partitionedList = lines.stream()
                .map(line -> extractValues(line))
                .collect(Collectors.partitioningBy(schoolRating -> StringUtils.isNotBlank(schoolRating.number)));
        List<SchoolRating> schoolsWithNumbersFromJS = partitionedList.get(true);
        List<SchoolRating> schoolsWithoutNumbersFromJS = partitionedList.get(false);
        schoolsWithNumbers.forEach(schoolRating -> {
            System.out.println(schoolsWithNumbersFromJS.stream()
                    .filter(name -> schoolRating.number.equals(name.getNumber()))
                    .map(SchoolRating::getName)
                    .findFirst().orElse("AAAAAAAAAAAA"));
        });
    }

    public static SchoolRating extractValues(String input) {
        Pattern pattern = Pattern.compile("gm_punkt\\('\\d+','(.*?)','(.*?)', \\\"<b>(.*?)</b>");
        Matcher matcher = pattern.matcher(input);
        String name = "";
        if (matcher.find()) {
            String latitude = matcher.group(1);
            String longitude = matcher.group(2);
            name = matcher.group(3);

            System.out.println("Latitude: " + latitude);
            System.out.println("Longitude: " + longitude);
            System.out.println("Name: " + name);

            SchoolRating schoolRating = new SchoolRating();
            schoolRating.setName(name);
            schoolRating.setNumber(getSchoolNumber(name));
            schoolRating.setLatitude(Double.parseDouble(latitude));
            schoolRating.setLongitude(Double.parseDouble(longitude));
            return schoolRating;
        } else {
            System.out.println("No match found.");
        }
        throw new IllegalStateException();
    }

    private static SchoolRating createSchoolRating(String text, String ratingAsString) {
        double rating = Double.parseDouble(ratingAsString);
        SchoolRating schoolRating = new SchoolRating();
        schoolRating.setName(text);
        schoolRating.setNumber(getSchoolNumber(text));
        schoolRating.setRating(rating);
        schoolRating.setYear(2024);
        return schoolRating;
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