package gr.barber.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import gr.barber.booking.model.ServiceKind;

import java.math.BigDecimal;

public record ServiceOfferingRequestDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull @Min(5) @Max(480) Integer durationMinutes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal price,
        // MAIN (a service or package) or EXTRA (added on top); null means MAIN for a new service
        // and keeps the current kind when editing.
        ServiceKind kind
) {
}
