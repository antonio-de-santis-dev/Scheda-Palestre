package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserEvents;
import com.gymplanner.identity.api.UserRole;
import com.gymplanner.identity.internal.AdminUserDtos.UserRequest;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.FieldViolation;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.web.Paging;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account management by the ADMIN (US-01, US-25). */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    record CreatedUser(User user, String temporaryPassword) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties bootstrap;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** The ADMIN created at start-up from the configuration cannot be deleted (ADR 0010). */
    boolean isProtected(User user) {
        return user.getRole() == UserRole.ADMIN && bootstrap.username() != null
                && user.getUsername().equalsIgnoreCase(bootstrap.username().trim());
    }

    /**
     * ADR 0010: logical deletion with anonymization. The row and everything that references it
     * are kept; active assignments are closed through {@link UserEvents.UserDeleted}; the session
     * version invalidates open sessions at once. Idempotent: an already deleted account is left
     * as is. Refused for oneself, the protected bootstrap ADMIN and the last active ADMIN.
     */
    @Transactional
    public void delete(UUID id, UUID currentAdminId) {
        User user = get(id);
        if (user.isDeleted()) {
            return;
        }
        if (user.getId().equals(currentAdminId)) {
            throw new BusinessRuleException("CANNOT_DELETE_SELF", "An ADMIN cannot delete their own account");
        }
        if (isProtected(user)) {
            throw new BusinessRuleException("PROTECTED_ACCOUNT", "The initial ADMIN account cannot be deleted");
        }
        if (user.getRole() == UserRole.ADMIN && user.isActive() && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ACTIVE_ADMIN", "At least one active ADMIN must exist");
        }
        user.anonymizeAndDelete(clock.instant(), passwordEncoder.encode(UUID.randomUUID().toString()));
        users.saveAndFlush(user);
        events.publishEvent(new UserEvents.UserDeleted(user.getId()));
        log.info("Account id={} deleted logically and anonymized", user.getId());
    }

    private static void requireNotDeleted(User user) {
        if (user.isDeleted()) {
            throw new BusinessRuleException("ACCOUNT_DELETED", "The account was deleted and cannot be changed");
        }
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, UserRole role, Boolean active, Pageable pageable) {
        return users.search(Paging.likePattern(q), role, active, pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    /** Creates a USER with a generated temporary password; the role is always USER. */
    @Transactional
    public CreatedUser create(UserRequest request) {
        UserRequest r = request.normalized();
        ensureUnique(r.username(), r.email(), null);
        String temporary = PasswordPolicy.generateTemporary();
        User user = new User(r.firstName(), r.lastName(), r.username(), r.email(), r.phone(),
                passwordEncoder.encode(temporary), UserRole.USER);
        users.saveAndFlush(user);
        log.info("USER account id={} created", user.getId());
        return new CreatedUser(user, temporary);
    }

    @Transactional
    public User update(UUID id, UserRequest request) {
        User user = get(id);
        requireNotDeleted(user);
        UserRequest r = request.normalized();
        ensureUnique(r.username(), r.email(), id);
        user.updateDetails(r.firstName(), r.lastName(), r.username(), r.email(), r.phone());
        users.saveAndFlush(user);
        return user;
    }

    @Transactional
    public User activate(UUID id) {
        User user = get(id);
        requireNotDeleted(user);
        user.activate();
        return user;
    }

    /** Deactivation invalidates open sessions (session version) and keeps at least one ADMIN. */
    @Transactional
    public User deactivate(UUID id, UUID currentAdminId) {
        User user = get(id);
        if (user.getId().equals(currentAdminId)) {
            throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "An ADMIN cannot deactivate their own account");
        }
        if (!user.isActive()) {
            return user;
        }
        if (user.getRole() == UserRole.ADMIN && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ACTIVE_ADMIN", "At least one active ADMIN must exist");
        }
        user.deactivate();
        log.info("Account id={} deactivated", user.getId());
        return user;
    }

    @Transactional
    public CreatedUser resetPassword(UUID id) {
        User user = get(id);
        requireNotDeleted(user);
        String temporary = PasswordPolicy.generateTemporary();
        user.resetPassword(passwordEncoder.encode(temporary));
        log.info("Password of account id={} reset by an ADMIN", user.getId());
        return new CreatedUser(user, temporary);
    }

    private void ensureUnique(String username, String email, UUID excludeId) {
        List<FieldViolation> errors = new ArrayList<>();
        boolean usernameTaken = users.existsUsername(username, excludeId);
        boolean emailTaken = users.existsEmail(email, excludeId);
        if (usernameTaken) {
            errors.add(new FieldViolation("username", "Username is already in use"));
        }
        if (emailTaken) {
            errors.add(new FieldViolation("email", "Email is already in use"));
        }
        if (!errors.isEmpty()) {
            String code = usernameTaken ? "USERNAME_TAKEN" : "EMAIL_TAKEN";
            throw new ConflictException(code, "Username or email already in use", errors);
        }
    }
}
