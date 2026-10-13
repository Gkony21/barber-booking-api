package gr.barber.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopRequestDTO(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 30) String phoneNumber,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude
) {
}
