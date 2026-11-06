package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;

/**
 * S->C : indique au client si l'overlay Jade doit etre masque pour le joueur local.
 */
public record JadeOverlayBlockedPayload(boolean blocked) {

    public static final NetCodec<JadeOverlayBlockedPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new JadeOverlayBlockedPayload(buf.readBoolean())
    );

}
