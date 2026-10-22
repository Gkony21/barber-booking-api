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

    // The main service shown with its price on the barber's card in the list.
    @PostMapping("/me/services/{serviceId}/feature")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void featureService(@PathVariable Long serviceId, Authentication authentication) {
        serviceOfferingService.featureService(authentication.getName(), serviceId);
    }

    // Hides the service from customers; saving it again (PUT) shows it again.
    @PostMapping("/me/services/{serviceId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideService(@PathVariable Long serviceId, Authentication authentication) {
        serviceOfferingService.hideService(authentication.getName(), serviceId);
    }

    // Removes it for good; appointments already booked with it are kept unchanged.
    @DeleteMapping("/me/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long serviceId, Authentication authentication) {
        serviceOfferingService.deleteService(authentication.getName(), serviceId);
    }

}
