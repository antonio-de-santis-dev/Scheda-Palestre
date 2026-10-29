package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.NotFoundException;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ProfileService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }

    @Transactional
    public User updatePhone(UUID userId, String phone) {
        User user = get(userId);
        user.updatePhone(phone == null || phone.isBlank() ? null : phone.trim());
        return user;
    }
}
