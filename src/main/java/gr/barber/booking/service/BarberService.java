package gr.barber.booking.service;

import gr.barber.booking.dto.BarberRequestDTO;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.model.Barber;
import gr.barber.booking.repository.BarberRepository;
import org.springframework.stereotype.Service;

@Service
public class BarberService {

    private final BarberRepository barberRepository;

    public BarberService(BarberRepository barberRepository) {
        this.barberRepository = barberRepository;
    }

    public BarberResponseDTO saveBarber(BarberRequestDTO request) {

        Barber barber = new Barber(
                request.getShopName(),
                request.getDescription(),
                request.getExperienceYears(),
                request.getSpecialty()
        );

        Barber savedBarber = barberRepository.save(barber);

        return new BarberResponseDTO(
                savedBarber.getId(),
                savedBarber.getShopName(),
                savedBarber.getDescription(),
                savedBarber.getExperienceYears(),
                savedBarber.getSpecialty()
        );
    }

}