package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

/** S→C : flags configurés sur une dimension spécifique. */
public record DimFlagsPayload(String dimKey, List<FlagInfo> flags, boolean viewOnly) {

    /** type: 0=BOOL, 1=INT, 2=LIST, 3=STRING. stringValue contient la valeur brute. */
    public record FlagInfo(String id, String label, boolean value, boolean configured,
                           String description, byte type, String stringValue) {
        public static final byte TYPE_BOOL = 0, TYPE_INT = 1, TYPE_LIST = 2, TYPE_STRING = 3;

        private static final NetCodec<String> ID_C    = NetCodec.utf8(64);
        private static final NetCodec<String> LABEL_C = NetCodec.utf8(128);
        private static final NetCodec<String> DESC_C  = NetCodec.utf8(512);
        private static final NetCodec<String> VALUE_C = NetCodec.utf8(32768);

        static final NetCodec<FlagInfo> CODEC = NetCodec.of(
            (buf, f) -> {
                ID_C.encode(buf, f.id);
                LABEL_C.encode(buf, f.label);
                buf.writeBoolean(f.value);
                buf.writeBoolean(f.configured);
                DESC_C.encode(buf, f.description);
                buf.writeByte(f.type);
                VALUE_C.encode(buf, f.stringValue);
            },
            buf -> new FlagInfo(
                ID_C.decode(buf),
                LABEL_C.decode(buf),
                buf.readBoolean(), buf.readBoolean(),
                DESC_C.decode(buf),
                buf.readByte(),
                VALUE_C.decode(buf)
            )
        );
    }

    private static final NetCodec<String> DIM_C = NetCodec.utf8(256);

    public static final NetCodec<DimFlagsPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> {
            DIM_C.encode(buf, p.dimKey());
            buf.writeInt(p.flags().size());
            for (FlagInfo f : p.flags()) FlagInfo.CODEC.encode(buf, f);
            buf.writeBoolean(p.viewOnly());
        },
        buf -> {
            String dimKey = DIM_C.decode(buf);
            int size = buf.readInt();
            if (size < 0 || size > 256) throw new io.netty.handler.codec.DecoderException("Invalid size " + size + " in DimFlagsPayload");
            List<FlagInfo> flags = new ArrayList<>(size);
            for (int i = 0; i < size; i++) flags.add(FlagInfo.CODEC.decode(buf));
            boolean viewOnly = buf.readBoolean();
            return new DimFlagsPayload(dimKey, flags, viewOnly);
        }
    );

}
