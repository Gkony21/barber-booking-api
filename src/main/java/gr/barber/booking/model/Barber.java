package gr.barber.booking.model;

import jakarta.persistence.*;

@Entity
public class Barber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shopName;

    private String description;

    private Integer experienceYears;

    private String specialty;

    public Barber(String shopName,
                  String description,
                  Integer experienceYears,
                  String specialty) {

        this.shopName = shopName;
        this.description = description;
        this.experienceYears = experienceYears;
        this.specialty = specialty;
    }

    public Long getId() {
        return id;
    }

    public String getShopName() {
        return shopName;
    }

    public String getDescription() {
        return description;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public String getSpecialty() {
        return specialty;
    }
}