package gr.barber.booking.controller;

import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.AvailabilityResponseDTO;
import gr.barber.booking.dto.DayStatusDTO;
import gr.barber.booking.dto.RescheduleRequestDTO;
import gr.barber.booking.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    // Free days and times for moving this appointment; its own time counts as free.
    @GetMapping("/{id}/days")
    public List<DayStatusDTO> getMoveDays(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          Authentication authentication) {
        return appointmentService.getMoveDays(authentication.getName(), id, from, to);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponseDTO getMoveAvailability(@PathVariable Long id,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                       Authentication authentication) {
        return appointmentService.getMoveAvailability(authentication.getName(), id, date);
    }

    // Takes back a change of time the barber has not answered; the appointment keeps its time.
    @PostMapping("/{id}/withdraw-change")
    public AppointmentResponseDTO withdrawChange(@PathVariable Long id, Authentication authentication) {
        return appointmentService.withdrawChange(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponseDTO cancel(@PathVariable Long id, Authentication authentication) {
        return appointmentService.cancelByCustomer(authentication.getName(), id);
    }

}
