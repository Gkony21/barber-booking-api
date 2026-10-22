package gr.barber.booking.service;

import gr.barber.booking.dto.ServiceOfferingRequestDTO;
import gr.barber.booking.dto.ServiceOfferingResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.repository.ServiceOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BarberService barberService;

    public ServiceOfferingService(ServiceOfferingRepository serviceOfferingRepository,
                                  BarberService barberService) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.barberService = barberService;
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponseDTO> getActiveServices(Long barberId) {
        Barber barber = barberService.getApprovedById(barberId);
        return withHeadline(serviceOfferingRepository.findAllByBarberIdAndActiveTrueOrderByName(barber.getId()));
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponseDTO> getMyServices(String email) {
        Barber barber = barberService.getByEmail(email);
        return withHeadline(serviceOfferingRepository.findAllByBarberIdAndDeletedFalseOrderByName(barber.getId()));
    }

    private static List<ServiceOfferingResponseDTO> withHeadline(List<ServiceOffering> services) {
        Long headline = ServiceOffering.headline(services).map(ServiceOffering::getId).orElse(null);
        return services.stream()
                .map(service -> ServiceOfferingResponseDTO.from(service, service.getId().equals(headline)))
                .toList();
    }

    /** Shows this main service with its price on the barber's card, instead of any chosen before. */
    @Transactional
    public void featureService(String email, Long serviceId) {
        ServiceOffering service = getOwnedService(email, serviceId);
        if (!service.isMain()) {
            throw new BadRequestException("NOT_A_MAIN_SERVICE", "Only a main service can be shown on the card");
        }
        serviceOfferingRepository.findAllByBarberIdAndDeletedFalseOrderByName(service.getBarber().getId())
                .forEach(other -> other.setFeatured(false));
        serviceOfferingRepository.flush(); // one featured service per barber, also in the database
        service.setFeatured(true);
    }

    @Transactional
    public ServiceOfferingResponseDTO createService(String email, ServiceOfferingRequestDTO request) {
        Barber barber = barberService.getByEmail(email);
        ServiceOffering service = new ServiceOffering(barber);
        applyDetails(service, request);
        return ServiceOfferingResponseDTO.from(serviceOfferingRepository.save(service));
    }

    @Transactional
    public ServiceOfferingResponseDTO updateService(String email, Long serviceId, ServiceOfferingRequestDTO request) {
        ServiceOffering service = getOwnedService(email, serviceId);
        applyDetails(service, request);
        service.setActive(true);
        return ServiceOfferingResponseDTO.from(service);
    }

    // Hidden from customers for now; the barber can show it again.
    @Transactional
    public void hideService(String email, Long serviceId) {
        getOwnedService(email, serviceId).setActive(false);
    }

    @Transactional
    public void deleteService(String email, Long serviceId) {
        getOwnedService(email, serviceId).delete();
    }

    private ServiceOffering getOwnedService(String email, Long serviceId) {
        Barber barber = barberService.getByEmail(email);
        return serviceOfferingRepository.findByIdAndBarberId(serviceId, barber.getId())
                .filter(service -> !service.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    private void applyDetails(ServiceOffering service, ServiceOfferingRequestDTO request) {
        service.setName(request.name());
        service.setDescription(request.description());
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price());
        if (request.kind() != null) {
            service.setKind(request.kind());
        }
    }
}
