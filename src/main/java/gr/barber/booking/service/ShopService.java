package gr.barber.booking.service;

import gr.barber.booking.dto.ShopInvitationDTO;
import gr.barber.booking.dto.ShopRequestDTO;
import gr.barber.booking.dto.ShopResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.InvitationStatus;
import gr.barber.booking.model.Shop;
import gr.barber.booking.model.ShopInvitation;
import gr.barber.booking.notification.ShopInvitationSent;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.ShopInvitationRepository;
import gr.barber.booking.repository.ShopRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Shops and their barbers. An approved barber opens a shop and becomes its owner; other barbers
 * join only by accepting the owner's invitation, and can leave whenever they like.
 */
@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final BarberRepository barberRepository;
    private final ShopInvitationRepository invitationRepository;
    private final BarberService barberService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ShopService(ShopRepository shopRepository,
                       BarberRepository barberRepository,
                       ShopInvitationRepository invitationRepository,
                       BarberService barberService,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock) {
        this.shopRepository = shopRepository;
        this.barberRepository = barberRepository;
        this.invitationRepository = invitationRepository;
        this.barberService = barberService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ShopResponseDTO createShop(String email, ShopRequestDTO request) {

        Barber owner = barberService.getByEmail(email);

        if (owner.getStatus() != BarberStatus.APPROVED) {
            throw new BadRequestException("NOT_APPROVED", "Only approved barbers can create a shop");
        }
        if (owner.getShop() != null) {
            throw new ConflictException("ALREADY_IN_SHOP", "You already belong to a shop");
        }

        Shop shop = new Shop(owner);
        applyDetails(shop, request);
        shop = shopRepository.save(shop);

        owner.setShop(shop);
        cancelPendingInvitationsFor(owner);

        return ShopResponseDTO.from(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopResponseDTO> getShops(String city) {

        List<Shop> shops = (city != null && !city.isBlank())
                ? shopRepository.findAllByCityIgnoreCase(city.trim())
                : shopRepository.findAll();

        return shops.stream().map(ShopResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ShopResponseDTO getShop(Long id) {
        return ShopResponseDTO.from(getById(id));
    }

    @Transactional
    public ShopResponseDTO updateShop(Long id, String email, ShopRequestDTO request) {
        Shop shop = getOwnedShop(id, email);
        applyDetails(shop, request);
        return ShopResponseDTO.from(shop);
    }

    @Transactional
    public void deleteShop(Long id, String email) {
        Shop shop = getOwnedShop(id, email);
        // Members are detached by the ON DELETE SET NULL foreign key, invitations deleted with the shop.
        shop.getOwner().setShop(null);
        shopRepository.delete(shop);
    }

    // ---- invitations ----

    @Transactional
    public ShopInvitationDTO invite(Long shopId, String ownerEmail, String barberEmail) {

        Shop shop = getOwnedShop(shopId, ownerEmail);

        Barber barber = barberRepository.findByUserEmail(UserService.normalizeEmail(barberEmail))
                .orElseThrow(() -> new ResourceNotFoundException("NO_BARBER_FOR_EMAIL", "No barber profile for this email"));

        if (Objects.equals(barber.getId(), shop.getOwner().getId())) {
            throw new BadRequestException("CANNOT_INVITE_SELF", "You are already the owner of this shop");
        }
        if (barber.getStatus() != BarberStatus.APPROVED) {
            throw new BadRequestException("BARBER_NOT_APPROVED", "Barber is not approved yet");
        }
        if (barber.getShop() != null) {
            throw new ConflictException("BARBER_IN_SHOP", "Barber already belongs to a shop");
        }
        if (invitationRepository.existsByShopIdAndBarberIdAndStatus(shop.getId(), barber.getId(), InvitationStatus.PENDING)) {
            throw new ConflictException("ALREADY_INVITED", "This barber has already been invited");
        }

        ShopInvitation invitation = invitationRepository.save(new ShopInvitation(shop, barber));
        eventPublisher.publishEvent(new ShopInvitationSent(barber.getUser().getEmail(), shop.getName(), shop.getCity()));
        return ShopInvitationDTO.from(invitation);
    }

    @Transactional(readOnly = true)
    public List<ShopInvitationDTO> getPendingInvitations(Long shopId, String ownerEmail) {
        Shop shop = getOwnedShop(shopId, ownerEmail);
        return invitationRepository.findAllByShopIdAndStatusOrderByCreatedAt(shop.getId(), InvitationStatus.PENDING).stream()
                .map(ShopInvitationDTO::from)
                .toList();
    }

    @Transactional
    public void cancelInvitation(Long shopId, Long invitationId, String ownerEmail) {
        Shop shop = getOwnedShop(shopId, ownerEmail);
        ShopInvitation invitation = invitationRepository.findByIdAndShopId(invitationId, shop.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        requirePending(invitation);
        invitation.close(InvitationStatus.CANCELLED, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ShopInvitationDTO> getMyInvitations(String barberEmail) {
        Barber barber = barberService.getByEmail(barberEmail);
        return invitationRepository.findAllByBarberIdAndStatusOrderByCreatedAt(barber.getId(), InvitationStatus.PENDING).stream()
                .map(ShopInvitationDTO::from)
                .toList();
    }

    @Transactional
    public ShopResponseDTO acceptInvitation(Long invitationId, String barberEmail) {
        Barber barber = barberService.getByEmail(barberEmail);
        ShopInvitation invitation = getMyInvitation(invitationId, barber);
        requirePending(invitation);
        if (barber.getShop() != null) {
            throw new ConflictException("ALREADY_IN_SHOP", "You already belong to a shop; leave it first");
        }

        barber.setShop(invitation.getShop());
        invitation.close(InvitationStatus.ACCEPTED, clock.instant());
        // A barber works in one shop, so other open invitations no longer apply.
        cancelPendingInvitationsFor(barber);
        return ShopResponseDTO.from(invitation.getShop());
    }

    @Transactional
    public void declineInvitation(Long invitationId, String barberEmail) {
        Barber barber = barberService.getByEmail(barberEmail);
        ShopInvitation invitation = getMyInvitation(invitationId, barber);
        requirePending(invitation);
        invitation.close(InvitationStatus.DECLINED, clock.instant());
    }

    // The shop owner can remove any barber; a barber can remove themselves (leave the shop).
    @Transactional
    public void removeBarber(Long shopId, Long barberId, String email) {

        Shop shop = getById(shopId);
        Barber requester = barberService.getByEmail(email);

        boolean isOwner = Objects.equals(shop.getOwner().getId(), requester.getId());
        boolean isSelf = Objects.equals(barberId, requester.getId());

        if (!isOwner && !isSelf) {
            throw new AccessDeniedException("You do not have permission to remove this barber");
        }
        if (Objects.equals(barberId, shop.getOwner().getId())) {
            throw new BadRequestException("OWNER_CANNOT_LEAVE", "The owner cannot leave the shop; delete the shop instead");
        }

        Barber barber = barberRepository.findById(barberId)
                .filter(b -> b.getShop() != null && Objects.equals(b.getShop().getId(), shopId))
                .orElseThrow(() -> new ResourceNotFoundException("Barber is not part of this shop"));

        barber.setShop(null);
    }

    private ShopInvitation getMyInvitation(Long invitationId, Barber barber) {
        return invitationRepository.findByIdAndBarberId(invitationId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    }

    private void cancelPendingInvitationsFor(Barber barber) {
        invitationRepository.findAllByBarberIdAndStatusOrderByCreatedAt(barber.getId(), InvitationStatus.PENDING)
                .forEach(other -> other.close(InvitationStatus.CANCELLED, clock.instant()));
    }

    private static void requirePending(ShopInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("INVITATION_CLOSED", "This invitation has already been answered or withdrawn");
        }
    }

    private Shop getById(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
    }

    private Shop getOwnedShop(Long id, String email) {
        Shop shop = getById(id);
        if (!shop.getOwner().getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("Only the shop owner can do this");
        }
        return shop;
    }

    private void applyDetails(Shop shop, ShopRequestDTO request) {
        shop.setName(request.name());
        shop.setAddress(request.address());
        shop.setCity(request.city());
        shop.setPhoneNumber(request.phoneNumber());
        shop.setLatitude(request.latitude());
        shop.setLongitude(request.longitude());
    }
}
