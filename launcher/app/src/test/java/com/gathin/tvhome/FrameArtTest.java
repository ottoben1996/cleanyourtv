package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FrameArtTest {

    @Test
    public void artworksKeepOnlyImagesInNameOrder() {
        List<String> files = FrameArt.artworks(new String[]{
                "06-wanderer.webp", "CREDITS.md", "01-starry.jpg", "03-wave.PNG", "02-sunrise.jpeg"});
        assertEquals(Arrays.asList("01-starry.jpg", "02-sunrise.jpeg", "03-wave.PNG", "06-wanderer.webp"),
                files);
    }

    @Test
    public void artworksTolerateAnEmptyGallery() {
        assertTrue(FrameArt.artworks(null).isEmpty());
        assertTrue(FrameArt.artworks(new String[]{"CREDITS.md", "art"}).isEmpty());
    }

    @Test
    public void stepWrapsAtBothEnds() {
        assertEquals(5, FrameArt.step(0, 6, -1));
        assertEquals(0, FrameArt.step(5, 6, 1));
        assertEquals(3, FrameArt.step(2, 6, 1));
        assertEquals(0, FrameArt.step(0, 0, 1));
        assertEquals(2, FrameArt.step(-1, 3, 0));
    }

    @Test
    public void theRoomIsDarkFromTenToEight() {
        assertFalse(FrameArt.night(21));
        assertTrue(FrameArt.night(22));
        assertTrue(FrameArt.night(23));
        assertTrue(FrameArt.night(0));
        assertTrue(FrameArt.night(7));
        assertFalse(FrameArt.night(8));
        assertFalse(FrameArt.night(12));
    }

    @Test
    public void fitKeepsTheAspectRatioAndCentresLandscape() {
        FrameArt.Box print = FrameArt.fit(1000, 500,
                new FrameArt.Box(0, 0, 1600, 900));
        assertEquals(0f, print.left, 0.01f);
        assertEquals(1600f, print.right, 0.01f);
        assertEquals(800f, print.height(), 0.01f);
        assertEquals(50f, print.top, 0.01f);
        assertEquals(850f, print.bottom, 0.01f);
    }

    @Test
    public void fitKeepsTheAspectRatioAndCentresPortrait() {
        FrameArt.Box print = FrameArt.fit(1000, 2000,
                new FrameArt.Box(100, 20, 1700, 920));
        assertEquals(450f, print.width(), 0.01f);
        assertEquals(900f, print.height(), 0.01f);
        assertEquals(675f, print.left, 0.01f);
        assertEquals(20f, print.top, 0.01f);
    }

    @Test
    public void fitFallsBackToTheAreaWithoutAnImage() {
        FrameArt.Box area = new FrameArt.Box(10, 20, 110, 220);
        assertSame(area, FrameArt.fit(0, 100, area));
        assertSame(area, FrameArt.fit(100, 0, area));
        FrameArt.Box flat = new FrameArt.Box(10, 20, 10, 220);
        assertSame(flat, FrameArt.fit(100, 100, flat));
    }
}
