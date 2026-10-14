package gr.barber.booking.config;

import gr.barber.booking.model.Barber;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.service.SlugService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gives barbers created before links existed a link made from their name. */
@Component
public class SlugBackfill implements ApplicationRunner {

    private final BarberRepository barberRepository;
    private final SlugService slugService;

    public SlugBackfill(BarberRepository barberRepository, SlugService slugService) {
        this.barberRepository = barberRepository;
        this.slugService = slugService;
    }

    // One transaction, so each barber's (lazily loaded) user name can be read.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Barber barber : barberRepository.findAllBySlugIsNull()) {
            barber.setSlug(slugService.generate(barber.getUser().getName(), barber.getId()));
            barberRepository.save(barber);
        }
    }
}
