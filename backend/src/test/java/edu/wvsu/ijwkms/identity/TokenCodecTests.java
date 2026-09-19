package edu.wvsu.ijwkms.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCodecTests {

    private final TokenCodec codec = new TokenCodec();

    @Test
    void createsUnpredictableTokensAndStableNonReversibleHashes() {
        String first = codec.newToken();
        String second = codec.newToken();

        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(codec.hash(first)).hasSize(64).isEqualTo(codec.hash(first)).doesNotContain(first);
    }
}
