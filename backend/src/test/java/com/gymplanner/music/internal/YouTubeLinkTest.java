package com.gymplanner.music.internal;

import static org.assertj.core.api.Assertions.*;
import com.gymplanner.shared.error.BadRequestException;
import org.junit.jupiter.api.Test;

class YouTubeLinkTest {
    @Test void acceptsSharedVideosAndMobileFormats() {
        for (String url : new String[] {"https://youtu.be/M7lc1UVf-VE?si=test", "https://www.youtube.com/watch?v=M7lc1UVf-VE", "https://m.youtube.com/shorts/M7lc1UVf-VE", "https://www.youtube.com/live/M7lc1UVf-VE"}) {
            assertThat(YouTubeLink.parse(url)).isEqualTo(new YouTubeLink("VIDEO", "M7lc1UVf-VE"));
        }
    }
    @Test void playlistTakesPrecedenceOverVideo() {
        assertThat(YouTubeLink.parse("https://music.youtube.com/watch?v=M7lc1UVf-VE&list=PL1234567890abcdef"))
            .isEqualTo(new YouTubeLink("PLAYLIST", "PL1234567890abcdef"));
        assertThat(YouTubeLink.parse("https://www.youtube.com/playlist?list=PL1234567890abcdef").kind()).isEqualTo("PLAYLIST");
    }
    @Test void rejectsUntrustedHostsProtocolsAndInvalidIdentifiers() {
        for (String url : new String[] {"javascript:alert(1)", "http://youtube.com/watch?v=M7lc1UVf-VE", "https://youtube.com.evil.test/watch?v=M7lc1UVf-VE", "https://youtube.com@evil.test/watch?v=M7lc1UVf-VE", "https://youtube.com:443/watch?v=M7lc1UVf-VE", "https://youtube.com/watch?v=bad", "https://youtube.com/playlist?list=bad", "https://youtube.com/redirect?list=PL1234567890", "https://youtube.com/watch?v=M7lc1UVf-VE&list=%3Cscript%3E"}) {
            assertThatThrownBy(() -> YouTubeLink.parse(url)).isInstanceOf(BadRequestException.class);
        }
    }
}
