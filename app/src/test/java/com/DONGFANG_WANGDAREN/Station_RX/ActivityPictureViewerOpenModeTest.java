package com.DONGFANG_WANGDAREN.Station_RX;

import com.DONGFANG_WANGDAREN.Station_RX.ui.activity.ActivityPictureViewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActivityPictureViewerOpenModeTest {

    @Test
    public void resolveOpenMode_withPmgExtension_returnsPgmMode() {
        assertEquals("pgm", ActivityPictureViewer.resolveOpenMode("sample.pmg", null));
    }

    @Test
    public void resolveOpenMode_withPgmExtension_returnsPgmModeForStandardPgm() {
        assertEquals("pgm", ActivityPictureViewer.resolveOpenMode("sample.pgm", null));
    }

    @Test
    public void buildPmgOutputFileName_appendsTimestampAndPmgExtension() {
        String outputFileName = ActivityPictureViewer.buildPmgOutputFileName("sample.pgm");

        org.junit.Assert.assertTrue(outputFileName.startsWith("sample_"));
        org.junit.Assert.assertTrue(outputFileName.endsWith(".pmg"));
    }
}
