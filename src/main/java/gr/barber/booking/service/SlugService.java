package gr.barber.booking.service;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.repository.BarberRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Short public links for barbers, e.g. /giorgos, made from their name and editable by them. */
@Service
public class SlugService {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MIN = 3;
    private static final int MAX = 30;

    // Paths the web app already uses, plus words that would look official.
    private static final Set<String> RESERVED = Set.of(
            "login", "logout", "register", "account", "admin", "barber", "barbers", "appointments", "terms",
            "privacy", "verify-email", "check-email", "forgot-password", "reset-password", "api", "media",
            "assets", "app", "help", "support", "about", "fade", "settings", "shops", "shop", "static");

    // Greek to Latin, close to ELOT 743; two-letter combinations first.
    private static final Map<String, String> PAIRS = Map.of("ου", "ou", "αι", "ai", "ει", "ei", "οι", "oi", "γγ", "ng", "μπ", "mp", "ντ", "nt");
    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('α', "a"), Map.entry('β', "v"), Map.entry('γ', "g"), Map.entry('δ', "d"), Map.entry('ε', "e"),
            Map.entry('ζ', "z"), Map.entry('η', "i"), Map.entry('θ', "th"), Map.entry('ι', "i"), Map.entry('κ', "k"),
            Map.entry('λ', "l"), Map.entry('μ', "m"), Map.entry('ν', "n"), Map.entry('ξ', "x"), Map.entry('ο', "o"),
            Map.entry('π', "p"), Map.entry('ρ', "r"), Map.entry('σ', "s"), Map.entry('ς', "s"), Map.entry('τ', "t"),
            Map.entry('υ', "y"), Map.entry('φ', "f"), Map.entry('χ', "ch"), Map.entry('ψ', "ps"), Map.entry('ω', "o"));

    private final BarberRepository barberRepository;

    public SlugService(BarberRepository barberRepository) {
        this.barberRepository = barberRepository;
    }

    /** A free link made from a name: "Γιώργος Π." → "giorgos-p", then "giorgos-p-2" if taken. */
    public String generate(String name, Long barberId) {
        String base = slugify(name);
        if (base.length() < MIN || RESERVED.contains(base)) {
            base = (base.isEmpty() ? "barber" : base + "-barber");
        }
        base = trim(base);
        String candidate = base;
        for (int i = 2; isTaken(candidate, barberId); i++) {
            candidate = trim(base, ("-" + i).length()) + "-" + i;
        }
        return candidate;
    }

    /** Checks a link the barber typed; returns it normalised. */
    public String validate(String requested, Long barberId) {
        String slug = requested.strip().toLowerCase(Locale.ROOT);
        if (slug.length() < MIN || slug.length() > MAX || !VALID.matcher(slug).matches()) {
            throw new BadRequestException("Links use 3-30 Latin letters, digits and single dashes");
        }
        if (RESERVED.contains(slug)) {
            throw new ConflictException("This link is not available");
        }
        if (isTaken(slug, barberId)) {
            throw new ConflictException("This link is already taken");
        }
        return slug;
    }

    static String slugify(String name) {
        String lower = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.forLanguageTag("el"));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            if (i + 1 < lower.length() && PAIRS.containsKey(lower.substring(i, i + 2))) {
                out.append(PAIRS.get(lower.substring(i, i + 2)));
                i++;
                continue;
            }
            char c = lower.charAt(i);
            if (LETTERS.containsKey(c)) {
                out.append(LETTERS.get(c));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        return out.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private boolean isTaken(String slug, Long barberId) {
        return barberRepository.findBySlug(slug)
                .filter(other -> !Objects.equals(other.getId(), barberId))
                .isPresent();
    }

    private static String trim(String slug) {
        return trim(slug, 0);
    }

    private static String trim(String slug, int room) {
        String cut = slug.length() > MAX - room ? slug.substring(0, MAX - room) : slug;
        return cut.replaceAll("-$", "");
    }
}
