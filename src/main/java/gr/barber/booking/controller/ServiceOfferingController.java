package gr.barber.booking.controller;

import gr.barber.booking.dto.ServiceOfferingRequestDTO;
import gr.barber.booking.dto.ServiceOfferingResponseDTO;
import gr.barber.booking.service.ServiceOfferingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/barbers")
public class ServiceOfferingController {

    private final ServiceOfferingService serviceOfferingService;

    public ServiceOfferingController(ServiceOfferingService serviceOfferingService) {
        this.serviceOfferingService = serviceOfferingService;
    }

    @GetMapping("/{barberId}/services")
    public List<ServiceOfferingResponseDTO> getServices(@PathVariable Long barberId) {
        return serviceOfferingService.getActiveServices(barberId);
    }

    @GetMapping("/me/services")
    public List<ServiceOfferingResponseDTO> getMyServices(Authentication authentication) {
        return serviceOfferingService.getMyServices(authentication.getName());
    }

    @PostMapping("/me/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOfferingResponseDTO createService(@Valid @RequestBody ServiceOfferingRequestDTO request,
                                                    Authentication authentication) {
        return serviceOfferingService.createService(authentication.getName(), request);
    }

    @PutMapping("/me/services/{serviceId}")
    public ServiceOfferingResponseDTO updateService(@PathVariable Long serviceId,
                                                    @Valid @RequestBody ServiceOfferingRequestDTO request,
                                                    Authentication authentication) {
        return serviceOfferingService.updateService(authentication.getName(), serviceId, request);
    }

    @DeleteMapping("/me/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateService(@PathVariable Long serviceId, Authentication authentication) {
        serviceOfferingService.deactivateService(authentication.getName(), serviceId);
    }

}
