package gr.barber.booking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "barbers")
public class Barber {

    /** The choices a barber has for how far ahead customers can book: two weeks, a month, two months. */
    public static final java.util.Set<Integer> ALLOWED_BOOKING_WINDOWS = java.util.Set.of(14, 30, 60);

    /** Minutes kept free after each appointment: the barber's choices. */
    public static final java.util.Set<Integer> ALLOWED_BUFFERS = java.util.Set.of(0, 5, 10, 15);

    /** How long before an appointment customers must book it: the barber's choices. */
    public static final java.util.Set<Integer> ALLOWED_MIN_NOTICE = java.util.Set.of(30, 60, 120);

    /**
     * Requests wait for the barber's answer and expire 2 hours before they start, so they can only
     * be sent at least 3 hours ahead: the barber always has an hour or more to reply.
     */
    public static final java.time.Duration REQUEST_EXPIRY = java.time.Duration.ofHours(2);
    private static final java.time.Duration MIN_REQUEST_NOTICE = REQUEST_EXPIRY.plusHours(1);


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Null when the barber works independently.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    private String bio;

    private Integer experienceYears;

    private String specialty;

    // Used for independent barbers; barbers in a shop are located at the shop's address.
    private String address;

    private String city;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BarberStatus status = BarberStatus.PENDING;

    // Short public link, e.g. "giorgos" for /giorgos.
    @Column(unique = true)
    private String slug;

    // Storage key of the profile photo, or null.
    private String avatarKey;

    // Appointments may start every this many minutes from the start of a shift (15 or 30).
    @Column(nullable = false)
    private int slotStepMinutes = 30;

    // How many days ahead customers can book: 14, 30 or 60 (see ALLOWED_BOOKING_WINDOWS).
    @Column(nullable = false)
    private int bookingWindowDays = 14;

    // Kept free after each appointment for online bookings (0, 5, 10 or 15 minutes).
    @Column(nullable = false)
    private int bufferMinutes = 0;

    // Customers book at least this many minutes ahead (30, 60 or 120).
    @Column(nullable = false)
    private int minNoticeMinutes = 60;

    // When false, new bookings stay PENDING until the barber confirms them.
    @Column(nullable = false)
    private boolean autoConfirmBookings = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Barber() {
    }

    public Barber(User user) {
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Shop getShop() {
        return shop;
    }

    public void setShop(Shop shop) {
        this.shop = shop;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BarberStatus getStatus() {
        return status;
    }

    public void setStatus(BarberStatus status) {
        this.status = status;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public int getBufferMinutes() {
        return bufferMinutes;
    }

    public void setBufferMinutes(int bufferMinutes) {
        this.bufferMinutes = bufferMinutes;
    }

    public java.time.Duration buffer() {
        return java.time.Duration.ofMinutes(bufferMinutes);
    }

    public int getMinNoticeMinutes() {
        return minNoticeMinutes;
    }

    public void setMinNoticeMinutes(int minNoticeMinutes) {
        this.minNoticeMinutes = minNoticeMinutes;
    }

    /** How far ahead a customer must book: the barber's notice, or longer when requests need an answer. */
    public java.time.Duration bookingNotice() {
        java.time.Duration notice = java.time.Duration.ofMinutes(minNoticeMinutes);
        return autoConfirmBookings || notice.compareTo(MIN_REQUEST_NOTICE) >= 0 ? notice : MIN_REQUEST_NOTICE;
    }

    public int getBookingWindowDays() {
        return bookingWindowDays;
    }

    public void setBookingWindowDays(int bookingWindowDays) {
        this.bookingWindowDays = bookingWindowDays;
    }

    public int getSlotStepMinutes() {
        return slotStepMinutes;
    }

    public void setSlotStepMinutes(int slotStepMinutes) {
        this.slotStepMinutes = slotStepMinutes;
    }

    public boolean isAutoConfirmBookings() {
        return autoConfirmBookings;
    }

    public void setAutoConfirmBookings(boolean autoConfirmBookings) {
        this.autoConfirmBookings = autoConfirmBookings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
