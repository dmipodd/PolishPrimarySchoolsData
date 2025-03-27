package com.dpod;

import com.dpod.bean.School;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateSchoolRating {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        List<School> schools = OBJECT_MAPPER.readValue(UpdateSchoolRating.class.getResource("/schools/Wroclaw/schools.json"), new TypeReference<List<School>>() {
        });
        List<School> schoolWithRatingList = readSchoolRatingList("/Ranking Szkół Podstawowych 2024.html");
        schools.forEach(school -> {
            String name = school.name;
            searchSchoolWithRatingByName(name, schoolWithRatingList).ifPresentOrElse(
                    schoolWithRating -> school.setRating2024(schoolWithRating.rating2024),
                    () -> {
                        System.out.println("Not found school " + name);
                        school.setRating2024(-1);
                    });
        });
        schools = schools.stream().sorted((o1, o2) -> o2.rating2025 - o1.rating2025 > 0 ? 1 : -1).toList();

        String json = OBJECT_MAPPER.writeValueAsString(schools);
        System.out.println(json);
    }

    private static Optional<School> searchSchoolWithRatingByName(String name, List<School> schoolWithRatingList) {
        return schoolWithRatingList.stream()
                .filter(schoolWithRating -> schoolWithRating.name.equals(name))
                .findFirst();
    }

    private static List<School> readSchoolRatingList(String filename) throws IOException, URISyntaxException {
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
            School school = createSchoolRating(text, ratingAsString);
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

    private static School createSchoolRating(String text, String ratingAsString) {
        double rating = Double.parseDouble(ratingAsString);
        School school = new School();
        school.setName(text);
        school.setNumber(getSchoolNumber(text));
        school.setRating2024(rating);
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