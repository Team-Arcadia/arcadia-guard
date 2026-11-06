package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;

/**
 * S->C : indique au client si les emotes du joueur local sont bloquees
 * (zone avec emote_use=deny). Le server-side verifier natif d'Emotecraft
 * depend d'une config externe (validateEmote) qui peut etre desactivee,
 * donc on double avec un verifier CLIENT-side authoritative.
 */
public record EmoteBlockedPayload(boolean blocked) {

    public static final NetCodec<EmoteBlockedPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new EmoteBlockedPayload(buf.readBoolean())
    );

}
