package com.gymplanner.music.internal;

import com.gymplanner.shared.error.BadRequestException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Parse identifiers only; never fetch user-provided URLs from the server. */
record YouTubeLink(String kind, String youtubeId) {
    static YouTubeLink parse(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1)
                throw new IllegalArgumentException();
            String host = uri.getHost();
            if (host == null || !Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be").contains(host.toLowerCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException();
            Map<String, String> query = new HashMap<>();
            if (uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
            }
            String path = uri.getPath();
            boolean shortHost = host.equalsIgnoreCase("youtu.be") || host.equalsIgnoreCase("www.youtu.be");
            boolean supported = shortHost ? path.matches("/[A-Za-z0-9_-]{11}")
                    : path.equals("/watch") || path.equals("/playlist") || path.matches("/(shorts|embed|live)/[A-Za-z0-9_-]{11}");
            if (!supported) throw new IllegalArgumentException();
            // A watch link carrying list= is intentionally saved as the whole playlist.
            String list = query.get("list");
            if (list != null && list.matches("[A-Za-z0-9_-]{10,128}")) return new YouTubeLink("PLAYLIST", list);
            if (list != null || path.equals("/playlist")) throw new IllegalArgumentException();
            String id = shortHost ? path.substring(1) : path.equals("/watch") ? query.get("v") : path.substring(path.lastIndexOf('/') + 1);
            if (id != null && id.matches("[A-Za-z0-9_-]{11}")) return new YouTubeLink("VIDEO", id);
        } catch (IllegalArgumentException ignored) { }
        throw BadRequestException.field("url", "Inserisci un link HTTPS valido di un video o di una playlist YouTube.");
    }
}
