package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;

/**
 * S->C : notifie le client qu'une zone vient d'etre supprimee cote serveur,
 * pour retirer l'entree correspondante du ClientZoneCache (rendu).
 * Sans ce packet, une zone deja activee via "Voir : ON" restait dessinee
 * meme apres sa suppression.
 */
public record ZoneRemovedPayload(String zoneName) {

    private static final NetCodec<String> NAME_C =
        NetCodec.utf8(64);

    public static final NetCodec<ZoneRemovedPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> NAME_C.encode(buf, p.zoneName()),
        buf -> new ZoneRemovedPayload(NAME_C.decode(buf))
    );

}
