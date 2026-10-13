package gr.barber.booking.dto;

public class BarberResponseDTO {

    private Long id;

    private String shopName;

    private String description;

    private Integer experienceYears;

    private String specialty;


    public BarberResponseDTO(Long id,
                             String shopName,
                             String description,
                             Integer experienceYears,
                             String specialty) {
        this.id = id;
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