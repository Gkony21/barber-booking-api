package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.ClientCardDTO;
import gr.barber.booking.dto.ClientDTO;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentExtra;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.ClientNote;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.ClientNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A barber's customers, worked out from their appointments: everyone who booked in the app or was
 * entered by hand, one entry per mobile number (see ClientKeys), with visits, no-shows, what they had
 * last time and the barber's own note.
 */
@Service
public class ClientService {

    private final AppointmentRepository appointmentRepository;
    private final ClientNoteRepository noteRepository;
    private final BarberService barberService;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public ClientService(AppointmentRepository appointmentRepository,
                         ClientNoteRepository noteRepository,
                         BarberService barberService,
                         BookingProperties bookingProperties,
                         Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.noteRepository = noteRepository;
        this.barberService = barberService;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    /**
     * The customers whose name or number matches {@code query} (all of them when it is empty), those seen
     * most recently first. A name matches without accents or case; a number by three or more digits.
     */
    @Transactional(readOnly = true)
    public List<ClientDTO> getClients(String barberEmail, String query, int limit) {
        Barber barber = barberService.getByEmail(barberEmail);
        String name = ClientKeys.plain(query);
        String digits = query == null ? "" : query.replaceAll("\\D", "");
        Map<String, String> notes = notes(barber);
        return groups(barber).entrySet().stream()
                .map(group -> summary(group.getKey(), group.getValue(), notes.get(group.getKey())))
                .filter(client -> name.isEmpty()
                        || ClientKeys.plain(client.name()).contains(name)
                        || (digits.length() >= 3 && client.phone() != null && client.phone().replaceAll("\\D", "").contains(digits)))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientCardDTO getClient(String barberEmail, String key) {
        Barber barber = barberService.getByEmail(barberEmail);
        List<Appointment> appointments = group(barber, key);
        ZoneId zone = bookingProperties.timeZone();
        String note = noteRepository.findByBarberIdAndClientKey(barber.getId(), key).map(ClientNote::getNote).orElse(null);
        return new ClientCardDTO(summary(key, appointments, note),
                appointments.stream().map(a -> AppointmentResponseDTO.from(a, zone)).toList());
    }

    /** Saves the barber's note about a customer; an empty note removes it. */
    @Transactional
    public ClientDTO saveNote(String barberEmail, String key, String note) {
        Barber barber = barberService.getByEmail(barberEmail);
        List<Appointment> appointments = group(barber, key);
        String text = note == null ? "" : note.strip();
        ClientNote saved = noteRepository.findByBarberIdAndClientKey(barber.getId(), key).orElse(null);
        if (text.isEmpty()) {
            if (saved != null) {
                noteRepository.delete(saved);
            }
            return summary(key, appointments, null);
        }
        if (saved == null) {
            saved = new ClientNote(barber, key);
        }
        saved.write(text, clock.instant());
        noteRepository.save(saved);
        return summary(key, appointments, text);
    }

    // Each customer's appointments, newest first; customers in order of their newest appointment.
    private Map<String, List<Appointment>> groups(Barber barber) {
        return appointmentRepository.findAllForBarberNewestFirst(barber.getId()).stream()
                .filter(a -> !a.isChange())
                .collect(Collectors.groupingBy(ClientKeys::of, LinkedHashMap::new, Collectors.toList()));
    }

    private List<Appointment> group(Barber barber, String key) {
        List<Appointment> appointments = groups(barber).get(key);
        if (appointments == null) {
            throw new ResourceNotFoundException("Customer not found");
        }
        return appointments;
    }

    private Map<String, String> notes(Barber barber) {
        return noteRepository.findAllByBarberId(barber.getId()).stream()
                .collect(Collectors.toMap(ClientNote::getClientKey, ClientNote::getNote));
    }

    private ClientDTO summary(String key, List<Appointment> appointments, String note) {
        Instant now = clock.instant();
        ZoneId zone = bookingProperties.timeZone();
        Appointment newest = appointments.getFirst();
        String phone = appointments.stream().map(Appointment::getCustomerPhone).filter(Objects::nonNull).findFirst().orElse(null);
        List<Appointment> visits = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED && a.getStartAt().isBefore(now))
                .toList();
        Instant next = appointments.stream()
                .filter(a -> a.getStatus().isActive() && a.getStartAt().isAfter(now))
                .map(Appointment::getStartAt).min(Comparator.naturalOrder()).orElse(null);
        // What they had last time (cancelled or refused bookings do not count), to book the same again.
        Appointment last = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.NO_SHOW
                        || a.getStatus() == AppointmentStatus.PENDING)
                .findFirst().orElse(null);
        return new ClientDTO(
                key,
                newest.getCustomerName(),
                phone,
                appointments.stream().anyMatch(a -> !a.isManual()),
                visits.size(),
                (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count(),
                visits.isEmpty() ? null : visits.getFirst().getStartAt().atZone(zone).toOffsetDateTime(),
                next == null ? null : next.atZone(zone).toOffsetDateTime(),
                last == null ? null : last.getService().getId(),
                last == null ? List.of() : last.getExtras().stream().map(AppointmentExtra::getServiceId).toList(),
                note
        );
    }
}
