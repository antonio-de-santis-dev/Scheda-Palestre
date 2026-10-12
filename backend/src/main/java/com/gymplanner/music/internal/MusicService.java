package com.gymplanner.music.internal;

import com.gymplanner.identity.api.UserEvents;
import com.gymplanner.shared.concurrency.UserLock;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);

    /** Upper bound of saved links per account: the list is loaded whole, without paging. */
    static final int MAX_ENTRIES = 200;

    record Entry(UUID id, String title, String kind, String youtubeId) {
    }

    private final JdbcTemplate jdbc;
    private final UserLock userLock;

    @Transactional(readOnly = true)
    List<Entry> list(UUID userId) {
        return jdbc.query("SELECT id, title, kind, youtube_id FROM music_entries WHERE user_id = ? ORDER BY created_at, id",
                (rs, row) -> new Entry(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("kind"),
                        rs.getString("youtube_id")), userId);
    }

    @Transactional
    Entry add(UUID userId, String title, String url) {
        YouTubeLink link = YouTubeLink.parse(url);
        // Serialises concurrent additions of the same user, so the limit cannot be exceeded.
        userLock.lock(userId);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM music_entries WHERE user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_ENTRIES) {
            throw new BusinessRuleException("MUSIC_LIMIT_REACHED", "At most " + MAX_ENTRIES + " music links can be saved")
                    .with("max", MAX_ENTRIES);
        }
        Entry entry = new Entry(UUID.randomUUID(), title.trim(), link.kind(), link.youtubeId());
        try {
            jdbc.update("INSERT INTO music_entries (id, user_id, title, kind, youtube_id, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    entry.id(), userId, entry.title(), entry.kind(), entry.youtubeId());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("MUSIC_DUPLICATE", "This video or playlist is already saved");
        }
        return entry;
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        if (jdbc.update("DELETE FROM music_entries WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw new NotFoundException("Music entry");
        }
    }

    /**
     * ADR 0010: accounts are never deleted physically, so ON DELETE CASCADE never fires. The
     * titles are free text written by the user and are removed with the other personal data.
     */
    @EventListener
    @Transactional
    public void onUserDeleted(UserEvents.UserDeleted event) {
        int removed = jdbc.update("DELETE FROM music_entries WHERE user_id = ?", event.userId());
        log.info("Account id={} deleted: {} music link(s) removed", event.userId(), removed);
    }
}
