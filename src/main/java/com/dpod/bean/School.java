package com.dpod.bean;

public class School {

    public String name;
    public String number;
    public double rating2025;
    public double rating2024;
    public double rating2023;
    public double rating2022;
    public Double latitude;
    public Double longitude;

    public double getRating2024() {
        return rating2024;
    }

    public void setRating2024(double rating2024) {
        this.rating2024 = rating2024;
    }

    public double getRating2023() {
        return rating2023;
    }

    public void setRating2023(double rating2023) {
        this.rating2023 = rating2023;
    }

    public double getRating2022() {
        return rating2022;
    }

    public void setRating2022(double rating2022) {
        this.rating2022 = rating2022;
    }

    @Override
    public String toString() {
        return name;
    }

    public void setZeroGeo() {
        latitude = 0d;
        longitude = 0d;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getRating2025() {
        return rating2025;
    }

    public void setRating2025(double rating2025) {
        this.rating2025 = rating2025;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
