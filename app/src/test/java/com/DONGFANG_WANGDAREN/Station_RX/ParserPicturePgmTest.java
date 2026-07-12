package com.DONGFANG_WANGDAREN.Station_RX;

import com.DONGFANG_WANGDAREN.Station_RX.reader.ParserPicturePgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ParserPicturePgmTest {

    @Test
    public void parseP2_withComments_returnsArgbPixels() throws IOException {
        String source = ""
                + "P2\n"
                + "# sample\n"
                + "2 2\n"
                + "15\n"
                + "0 5 10 15\n";

        ParserPicturePgm.DataPicturePgm result = ParserPicturePgm.parse(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.US_ASCII))
        );

        assertEquals(2, result.getWidth());
        assertEquals(2, result.getHeight());
        assertArrayEquals(new int[]{
                0xFF000000,
                0xFF555555,
                0xFFAAAAAA,
                0xFFFFFFFF
        }, result.getArgbPixels());
    }

    @Test
    public void parseP5_returnsArgbPixels() throws IOException {
        byte[] source = new byte[]{
                'P', '5', '\n',
                '2', ' ', '1', '\n',
                '2', '5', '5', '\n',
                0x00, (byte) 0xFF
        };

        ParserPicturePgm.DataPicturePgm result = ParserPicturePgm.parse(new ByteArrayInputStream(source));

        assertEquals(2, result.getWidth());
        assertEquals(1, result.getHeight());
        assertArrayEquals(new int[]{
                0xFF000000,
                0xFFFFFFFF
        }, result.getArgbPixels());
    }
}
