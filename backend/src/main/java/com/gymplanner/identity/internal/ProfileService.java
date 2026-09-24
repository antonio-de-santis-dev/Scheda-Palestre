package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService {

    private final UserRepository users;

    ProfileService(UserRepository users) {
        this.users = users;
    }

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
