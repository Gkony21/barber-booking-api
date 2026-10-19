package gr.barber.booking.controller;

import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.RescheduleRequestDTO;
import gr.barber.booking.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponseDTO book(@Valid @RequestBody AppointmentRequestDTO request,
                                       Authentication authentication) {
        return appointmentService.book(authentication.getName(), request);
    }

    @GetMapping("/me")
    public List<AppointmentResponseDTO> getMyAppointments(Authentication authentication) {
        return appointmentService.getCustomerAppointments(authentication.getName());
    }

    @PostMapping("/{id}/reschedule")
    public AppointmentResponseDTO reschedule(@PathVariable Long id,
                                             @Valid @RequestBody RescheduleRequestDTO request,
                                             Authentication authentication) {
        return appointmentService.rescheduleByCustomer(authentication.getName(), id, request.startTime());
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponseDTO cancel(@PathVariable Long id, Authentication authentication) {
        return appointmentService.cancelByCustomer(authentication.getName(), id);
    }

}
