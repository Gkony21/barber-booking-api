package gr.barber.booking.controller;

import gr.barber.booking.dto.BarberRequestDTO;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.service.BarberService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/barbers")
public class BarberController {

    private final BarberService barberService;

    public BarberController(BarberService barberService) {
        this.barberService = barberService;
    }

    @PostMapping
    public BarberResponseDTO createBarber(@RequestBody BarberRequestDTO request) {
        return barberService.saveBarber(request);
    }

}