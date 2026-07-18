package com.example.mindmap.service.asr;

import java.io.File;

final class DecodedAudioFile {
    final File file;
    final String format;
    final boolean temporary;

    DecodedAudioFile(File file, String format, boolean temporary) {
        this.file = file;
        this.format = format;
        this.temporary = temporary;
    }
}
