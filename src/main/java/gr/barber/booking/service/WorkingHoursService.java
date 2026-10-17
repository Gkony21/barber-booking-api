package gr.barber.booking.service;

import gr.barber.booking.dto.WorkingHoursDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.WorkingHours;
import gr.barber.booking.repository.WorkingHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class WorkingHoursService {

    private static final Comparator<WorkingHoursDTO> SCHEDULE_ORDER =
            Comparator.comparing(WorkingHoursDTO::dayOfWeek).thenComparing(WorkingHoursDTO::startTime);

    private final WorkingHoursRepository workingHoursRepository;
    private final BarberService barberService;

    public WorkingHoursService(WorkingHoursRepository workingHoursRepository,
                               BarberService barberService) {
        this.workingHoursRepository = workingHoursRepository;
        this.barberService = barberService;
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursDTO> getWorkingHours(Long barberId) {
        Barber barber = barberService.getApprovedById(barberId);
        return findForBarber(barber.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursDTO> getMyWorkingHours(String email) {
        return findForBarber(barberService.getByEmail(email).getId());
    }

    // Replaces the whole weekly schedule, which keeps the client simple (one form, one request).
    @Transactional
    public List<WorkingHoursDTO> replaceMyWorkingHours(String email, List<WorkingHoursDTO> request) {

        validate(request);

        Barber barber = barberService.getByEmail(email);
        workingHoursRepository.deleteAllByBarberId(barber.getId());

        List<WorkingHours> hours = request.stream()
                .map(slot -> new WorkingHours(barber, slot.dayOfWeek(), slot.startTime(), slot.endTime()))
                .toList();
        workingHoursRepository.saveAll(hours);

        return findForBarber(barber.getId());
    }

    private List<WorkingHoursDTO> findForBarber(Long barberId) {
        // Sorted in Java: the day is stored as text, so SQL ordering would be alphabetical.
        return workingHoursRepository.findAllByBarberId(barberId).stream()
                .map(WorkingHoursDTO::from)
                .sorted(SCHEDULE_ORDER)
                .toList();
    }

    private void validate(List<WorkingHoursDTO> slots) {

        for (WorkingHoursDTO slot : slots) {
            if (!slot.startTime().isBefore(slot.endTime())) {
                throw new BadRequestException("INVALID_HOURS", "Start time must be before end time on " + slot.dayOfWeek());
            }
        }

        List<WorkingHoursDTO> sorted = slots.stream().sorted(SCHEDULE_ORDER).toList();

        for (int i = 1; i < sorted.size(); i++) {
            WorkingHoursDTO previous = sorted.get(i - 1);
            WorkingHoursDTO current = sorted.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek()
                    && current.startTime().isBefore(previous.endTime())) {
                throw new BadRequestException("OVERLAPPING_HOURS", "Overlapping working hours on " + current.dayOfWeek());
            }
        }
    }
}
