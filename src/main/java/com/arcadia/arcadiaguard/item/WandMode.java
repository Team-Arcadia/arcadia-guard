package com.arcadia.arcadiaguard.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

public enum WandMode {
    EDIT, VIEW;

    public static final Codec<WandMode> CODEC = Codec.BOOL.xmap(
        view -> view ? VIEW : EDIT,
        mode -> mode == VIEW
    );

    /** PORT: StreamCodec does not exist on 1.20.1; minimal ByteBuf codec with the same wire format. */
    public static final BufCodec STREAM_CODEC = new BufCodec();

    public static final class BufCodec {
        private BufCodec() {}
        public void encode(ByteBuf buf, WandMode mode) { buf.writeBoolean(mode == VIEW); }
        public WandMode decode(ByteBuf buf) { return buf.readBoolean() ? VIEW : EDIT; }
    }
}
