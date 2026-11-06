package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;

/**
 * S-H21 (v1.5) : S->C payload qui indique au client si le joueur
 * local a ses actions ParCool bloquees (zone avec parcool_actions=deny).
 *
 * <p>Le client maintient un flag static via ClientParcoolState, consulte
 * par {@code ActionProcessorMixin} pour canceler le tick ParCool local.
 */
public record ParcoolBlockedPayload(boolean blocked) {

    public static final NetCodec<ParcoolBlockedPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new ParcoolBlockedPayload(buf.readBoolean())
    );

}
