package com.gymplanner.music.internal;

import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class MusicService {
    record Entry(UUID id, String title, String kind, String youtubeId) { }
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    List<Entry> list(UUID userId) {
        return jdbc.query("SELECT id, title, kind, youtube_id FROM music_entries WHERE user_id = ? ORDER BY created_at, id",
                (rs, row) -> new Entry(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("kind"), rs.getString("youtube_id")), userId);
    }

    @Transactional
    Entry add(UUID userId, String title, String url) {
        YouTubeLink link = YouTubeLink.parse(url);
        Entry entry = new Entry(UUID.randomUUID(), title.trim(), link.kind(), link.youtubeId());
        try {
            jdbc.update("INSERT INTO music_entries (id, user_id, title, kind, youtube_id, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    entry.id(), userId, entry.title(), entry.kind(), entry.youtubeId());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("MUSIC_DUPLICATE", "Questo video o questa playlist è già nella tua musica.");
        }
        return entry;
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        if (jdbc.update("DELETE FROM music_entries WHERE id = ? AND user_id = ?", id, userId) == 0)
            throw new NotFoundException("Music entry");
    }
}
