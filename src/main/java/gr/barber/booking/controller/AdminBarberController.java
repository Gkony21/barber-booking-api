package gr.barber.booking.controller;

import gr.barber.booking.dto.AdminBarberResponseDTO;
import gr.barber.booking.dto.BarberStatusUpdateDTO;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.service.BarberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/barbers")
public class AdminBarberController {

    private final BarberService barberService;

    public AdminBarberController(BarberService barberService) {
        this.barberService = barberService;
    }

    @GetMapping
    public List<AdminBarberResponseDTO> getBarbers(@RequestParam(defaultValue = "PENDING") BarberStatus status) {
        return barberService.getBarbersByStatus(status);
    }

    @PatchMapping("/{id}/status")
    public AdminBarberResponseDTO updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody BarberStatusUpdateDTO request) {
        return barberService.updateStatus(id, request.status());
    }

}
