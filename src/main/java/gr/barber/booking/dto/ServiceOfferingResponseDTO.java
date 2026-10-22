package gr.barber.booking.dto;

import gr.barber.booking.model.ServiceKind;
import gr.barber.booking.model.ServiceOffering;

import java.math.BigDecimal;

public record ServiceOfferingResponseDTO(
        Long id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        ServiceKind kind,
        boolean active,
        // Shown on the barber's card in the list (chosen, or the first main service by default).
        boolean featured
) {

    public static ServiceOfferingResponseDTO from(ServiceOffering service) {
        return from(service, false);
    }

    public static ServiceOfferingResponseDTO from(ServiceOffering service, boolean featured) {
        return new ServiceOfferingResponseDTO(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getDurationMinutes(),
                service.getPrice(),
                service.getKind(),
                service.isActive(),
                featured
        );
    }
}
