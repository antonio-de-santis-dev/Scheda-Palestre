package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserDirectory;
import com.gymplanner.identity.api.UserSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> find(UUID id) {
        return users.findById(id).map(UserDirectoryService::toSummary);
    }

    @Override
    public Map<UUID, UserSummary> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(ids).stream()
                .map(UserDirectoryService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getFirstName(), user.getLastName(), user.getUsername(),
                user.getEmail(), user.getRole(), user.isActive());
    }
}
