package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.ServiceOfferingRepository;
import gr.barber.booking.repository.WorkingHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The barbers customers browse: approved profiles with a cover photo, starting price and the
 * next free time, so the list can show "Ελεύθερο 17:30" without opening each barber.
 */
@Service
public class BarberDirectoryService {

    private final BarberRepository barberRepository;
    private final BarberPhotoRepository photoRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final BarberService barberService;
    private final AvailabilityService availabilityService;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public BarberDirectoryService(BarberRepository barberRepository,
                                  BarberPhotoRepository photoRepository,
                                  ServiceOfferingRepository serviceRepository,
                                  WorkingHoursRepository workingHoursRepository,
                                  BarberService barberService,
                                  AvailabilityService availabilityService,
                                  BookingProperties bookingProperties,
                                  Clock clock) {
        this.barberRepository = barberRepository;
        this.photoRepository = photoRepository;
        this.serviceRepository = serviceRepository;
        this.workingHoursRepository = workingHoursRepository;
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
        if (shopId == null) {
            barbers = bookableOnly(barbers);
        }

        LocalDate today = today();
        Map<Long, BarberResponseDTO.Listing> listings = listings(barbers);
        return barbers.stream()
                .map(barber -> BarberResponseDTO.from(barber, listings.get(barber.getId())))
                .filter(dto -> !availableToday
                        || (dto.nextAvailable() != null && dto.nextAvailable().toLocalDate().equals(today)))
                .sorted(Comparator.comparing(BarberResponseDTO::nextAvailable,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // Customers browsing only see barbers they can book: with a main service and working hours.
    // Their own link (and a shop's member list) still shows them.
    private List<Barber> bookableOnly(List<Barber> barbers) {
        List<Long> ids = barbers.stream().map(Barber::getId).toList();
        if (ids.isEmpty()) {
            return barbers;
        }
        Set<Long> withServices = new HashSet<>(serviceRepository.findBarberIdsWithMainServices(ids));
        Set<Long> withHours = new HashSet<>(workingHoursRepository.findBarberIdsWithHours(ids));
        return barbers.stream().filter(b -> withServices.contains(b.getId()) && withHours.contains(b.getId())).toList();
    }

    @Transactional(readOnly = true)
    public BarberResponseDTO getBySlug(String slug) {
        Barber barber = barberRepository.findBySlugAndStatus(slug.toLowerCase(Locale.ROOT), BarberStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Barber not found"));
        return BarberResponseDTO.from(barber, listings(List.of(barber)).get(barber.getId()));
    }

    @Transactional(readOnly = true)
    public BarberResponseDTO get(Long id) {
        Barber barber = barberService.getApprovedById(id);
        return BarberResponseDTO.from(barber, listings(List.of(barber)).get(barber.getId()));
    }

    // Cover, photo count, starting price and next free time for every barber, in a handful of
    // queries in total rather than dozens per barber.
    private Map<Long, BarberResponseDTO.Listing> listings(List<Barber> barbers) {
        List<Long> ids = barbers.stream().map(Barber::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Extras are add-ons: the starting price and next free time come from the main services.
        Map<Long, List<ServiceOffering>> services = serviceRepository.findAllByBarberIdInAndActiveTrue(ids).stream()
                .filter(ServiceOffering::isMain)
                .collect(Collectors.groupingBy(service -> service.getBarber().getId()));
        Map<Long, List<String>> photoKeys = new HashMap<>();
        for (Object[] row : photoRepository.findKeysNewestFirst(ids)) {
            photoKeys.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }

        // Next free time for the barber's shortest service.
        Map<Long, Integer> shortest = new HashMap<>();
        services.forEach((barberId, offered) -> offered.stream().map(ServiceOffering::getDurationMinutes)
                .filter(Objects::nonNull).min(Comparator.naturalOrder())
                .ifPresent(minutes -> shortest.put(barberId, minutes)));
        Map<Long, OffsetDateTime> nextAvailable = availabilityService.nextAvailable(barbers, shortest);

        Map<Long, BarberResponseDTO.Listing> listings = new HashMap<>();
        for (Long id : ids) {
            List<String> keys = photoKeys.getOrDefault(id, List.of());
            // Free extras (e.g. a 0 € wash) would make every barber read "από 0 €".
            BigDecimal fromPrice = services.getOrDefault(id, List.of()).stream().map(ServiceOffering::getPrice)
                    .filter(price -> price.signum() > 0)
                    .min(Comparator.naturalOrder()).orElse(null);
            listings.put(id, new BarberResponseDTO.Listing(
                    keys.isEmpty() ? null : keys.getFirst(), keys.size(), fromPrice, nextAvailable.get(id)));
        }
        return listings;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(bookingProperties.timeZone()));
    }
}
