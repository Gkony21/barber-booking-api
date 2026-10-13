package gr.barber.booking.dto;

public class BarberRequestDTO {

    private String shopName;

    private String description;

    private Integer experienceYears;

    private String specialty;


    public BarberRequestDTO() {
    }


    public BarberRequestDTO(String shopName,
                            String description,
                            Integer experienceYears,
                            String specialty) {
        this.shopName = shopName;
        this.description = description;
        this.experienceYears = experienceYears;
        this.specialty = specialty;
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