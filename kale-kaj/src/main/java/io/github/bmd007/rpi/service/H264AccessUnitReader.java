package io.github.bmd007.rpi.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public class H264AccessUnitReader {

    public record AccessUnit(byte[] data, boolean keyframe) {
    }

    private static final byte[] START_CODE = {0, 0, 0, 1};
    private static final int NAL_SLICE = 1;
    private static final int NAL_IDR_SLICE = 5;

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final Consumer<AccessUnit> consumer;
    private boolean pendingKeyframe;

    public H264AccessUnitReader(Consumer<AccessUnit> consumer) {
        this.consumer = consumer;
    }

    public void read(InputStream stream) throws IOException {
        var buffer = new byte[16 * 1024];
        var nal = new ByteArrayOutputStream();
        var zeros = 0;
        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            for (var i = 0; i < bytesRead; i++) {
                var current = buffer[i] & 0xFF;
                if (current == 1 && zeros >= 2) {
                    onNal(nal.toByteArray(), nal.size() - zeros);
                    nal.reset();
                    zeros = 0;
                    continue;
                }
                nal.write(current);
                zeros = current == 0 ? zeros + 1 : 0;
            }
        }
    }

    private void onNal(byte[] payload, int length) {
        if (length <= 0) {
            return;
        }
        pending.writeBytes(START_CODE);
        pending.write(payload, 0, length);
        var type = payload[0] & 0x1F;
        if (type == NAL_IDR_SLICE) {
            pendingKeyframe = true;
        }
        if (type == NAL_SLICE || type == NAL_IDR_SLICE) {
            consumer.accept(new AccessUnit(pending.toByteArray(), pendingKeyframe));
            pending.reset();
            pendingKeyframe = false;
        }
    }
}
