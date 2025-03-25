package com.dpod;

import org.apache.commons.text.similarity.JaroWinklerDistance;

public class SchoolNameMatcher {
    public static void main(String[] args) {
        testSchoolNameMatching();
    }

    public static void testSchoolNameMatching() {
        String[] testCases = {
                "Szkoła Podstawowa nr 9 im. Wincentego Pola",
                "Szkoła Podstawowa nr 9 patrona W. Pola",
                "Szkoła Podstawowa nr 9",
                "9 im. Wincentego Pola",
                "Szkoła Podstawowa nr 90 im. prof. Stanisława Tołpy",
                "Szkoła Podstawowa nr 1 im. Mikołaja Kopernika Wrocław"
        };

        String referenceName = "Szkoła nr 9 im. Wincentego Pola";
        JaroWinklerDistance distance = new JaroWinklerDistance();

        for (String test : testCases) {
            Double similarity = distance.apply(referenceName, test);
            System.out.println("Comparing: " + referenceName + " <-> " + test + " -> Similarity: " + similarity);
        }
    }
}
