package gr.barber.booking.service;

import gr.barber.booking.dto.AdminBarberResponseDTO;
import gr.barber.booking.dto.BarberRequestDTO;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BarberService {

    private final BarberRepository barberRepository;
    private final UserRepository userRepository;

    public BarberService(BarberRepository barberRepository, UserRepository userRepository) {
        this.barberRepository = barberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BarberResponseDTO createProfile(String email, BarberRequestDTO request) {

        if (barberRepository.existsByUserEmail(email)) {
            throw new ConflictException("Barber profile already exists");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Barber barber = new Barber(user);
        applyProfile(barber, request);

        return BarberResponseDTO.from(barberRepository.save(barber));
    }

    @Transactional(readOnly = true)
    public BarberResponseDTO getMyProfile(String email) {
        return BarberResponseDTO.from(getByEmail(email));
    }

    @Transactional
    public BarberResponseDTO updateMyProfile(String email, BarberRequestDTO request) {
        Barber barber = getByEmail(email);
        applyProfile(barber, request);
        return BarberResponseDTO.from(barber);
    }

    // Oldest first, so requests are handled in the order barbers signed up.
    @Transactional(readOnly = true)
    public List<AdminBarberResponseDTO> getBarbersByStatus(BarberStatus status) {
        return barberRepository.findAllByStatusOrderByCreatedAtAsc(status).stream()
                .map(AdminBarberResponseDTO::from)
                .toList();
    }

    @Transactional
    public AdminBarberResponseDTO updateStatus(Long id, BarberStatus status) {
        Barber barber = barberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barber not found"));
        barber.setStatus(status);
        return AdminBarberResponseDTO.from(barber);
    }

    public Barber getByEmail(String email) {
        return barberRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Barber profile not found"));
    }

    public Barber getApprovedById(Long id) {
        return barberRepository.findByIdAndStatus(id, BarberStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Barber not found"));
    }

    private void applyProfile(Barber barber, BarberRequestDTO request) {
        barber.setBio(request.bio());
        barber.setExperienceYears(request.experienceYears());
        barber.setSpecialty(request.specialty());
        barber.setAddress(request.address());
        barber.setCity(request.city());
        barber.setLatitude(request.latitude());
        barber.setLongitude(request.longitude());
        if (request.autoConfirmBookings() != null) {
            barber.setAutoConfirmBookings(request.autoConfirmBookings());
        }
        if (request.slotStepMinutes() != null) {
            if (request.slotStepMinutes() != 15 && request.slotStepMinutes() != 30) {
                throw new BadRequestException("Appointments can start every 15 or 30 minutes");
            }
            barber.setSlotStepMinutes(request.slotStepMinutes());
        }
    }
}
