package gr.barber.booking.service;

import gr.barber.booking.dto.ServiceOfferingRequestDTO;
import gr.barber.booking.dto.ServiceOfferingResponseDTO;
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
        return serviceOfferingRepository.findAllByBarberIdAndActiveTrueOrderByName(barber.getId()).stream()
                .map(ServiceOfferingResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponseDTO> getMyServices(String email) {
        Barber barber = barberService.getByEmail(email);
        return serviceOfferingRepository.findAllByBarberIdOrderByName(barber.getId()).stream()
                .map(ServiceOfferingResponseDTO::from)
                .toList();
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

    @Transactional
    public void deactivateService(String email, Long serviceId) {
        getOwnedService(email, serviceId).setActive(false);
    }

    private ServiceOffering getOwnedService(String email, Long serviceId) {
        Barber barber = barberService.getByEmail(email);
        return serviceOfferingRepository.findByIdAndBarberId(serviceId, barber.getId())
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
