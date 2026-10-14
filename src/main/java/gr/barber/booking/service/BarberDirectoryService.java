package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.ServiceOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The barbers customers browse: approved profiles with a cover photo, starting price and the
 * next free time, so the list can show "Ελεύθερο 17:30" without opening each barber.
 */
@Service
public class BarberDirectoryService {

    private final BarberRepository barberRepository;
    private final BarberPhotoRepository photoRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final BarberService barberService;
    private final AvailabilityService availabilityService;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public BarberDirectoryService(BarberRepository barberRepository,
                                  BarberPhotoRepository photoRepository,
                                  ServiceOfferingRepository serviceRepository,
                                  BarberService barberService,
                                  AvailabilityService availabilityService,
                                  BookingProperties bookingProperties,
                                  Clock clock) {
        this.barberRepository = barberRepository;
        this.photoRepository = photoRepository;
        this.serviceRepository = serviceRepository;
        this.barberService = barberService;
        this.availabilityService = availabilityService;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    /**
     * Barbers free today come first, soonest first; then the rest by their next free time.
     *
     * @param availableToday only barbers with a free time later today
     */
    @Transactional(readOnly = true)
    public List<BarberResponseDTO> list(String city, Long shopId, boolean availableToday) {
        List<Barber> barbers;
        if (shopId != null) {
            barbers = barberRepository.findAllByStatusAndShopId(BarberStatus.APPROVED, shopId);
        } else if (city != null && !city.isBlank()) {
            barbers = barberRepository.findAllByStatusAndCity(BarberStatus.APPROVED, city.trim());
        } else {
            barbers = barberRepository.findAllByStatus(BarberStatus.APPROVED);
        }

        LocalDate today = today();
        return barbers.stream()
                .map(barber -> BarberResponseDTO.from(barber, listing(barber)))
                .filter(dto -> !availableToday
                        || (dto.nextAvailable() != null && dto.nextAvailable().toLocalDate().equals(today)))
                .sorted(Comparator.comparing(BarberResponseDTO::nextAvailable,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BarberResponseDTO get(Long id) {
        Barber barber = barberService.getApprovedById(id);
        return BarberResponseDTO.from(barber, listing(barber));
    }

    private BarberResponseDTO.Listing listing(Barber barber) {
        List<ServiceOffering> services = serviceRepository.findAllByBarberIdAndActiveTrueOrderByName(barber.getId());
        BigDecimal fromPrice = services.stream().map(ServiceOffering::getPrice).min(Comparator.naturalOrder()).orElse(null);
        String coverKey = photoRepository.findTopByBarberIdOrderByCreatedAtDescIdDesc(barber.getId())
                .map(photo -> photo.getImageKey())
                .orElse(null);

        return new BarberResponseDTO.Listing(
                coverKey,
                photoRepository.countByBarberId(barber.getId()),
                fromPrice,
                nextAvailable(barber, services));
    }

    // The first free start for the barber's shortest service, within the booking window.
    private OffsetDateTime nextAvailable(Barber barber, List<ServiceOffering> services) {
        Integer shortest = services.stream().map(ServiceOffering::getDurationMinutes).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        if (shortest == null) {
            return null;
        }
        ZoneId zone = bookingProperties.timeZone();
        LocalDate today = today();
        for (int day = 0; day <= bookingProperties.maxDaysAhead(); day++) {
            LocalDate date = today.plusDays(day);
            List<LocalTime> starts = availabilityService.findAvailableStartTimes(
                    barber.getId(), date, shortest, barber.getSlotStepMinutes());
            if (!starts.isEmpty()) {
                return date.atTime(starts.getFirst()).atZone(zone).toOffsetDateTime();
            }
        }
        return null;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(bookingProperties.timeZone()));
    }
}
