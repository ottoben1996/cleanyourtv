package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the weather inputs: units, coordinates and the WMO code mapping. */
public class WeatherTest {

    @Test
    public void unitsFallBackToCelsius() {
        assertEquals(Weather.FAHRENHEIT, Weather.units("fahrenheit"));
        assertEquals(Weather.FAHRENHEIT, Weather.units(" FAHRENHEIT "));
        assertEquals(Weather.CELSIUS, Weather.units("celsius"));
        assertEquals(Weather.CELSIUS, Weather.units("kelvin"));
        assertEquals(Weather.CELSIUS, Weather.units(""));
        assertEquals(Weather.CELSIUS, Weather.units(null));
    }

    @Test
    public void coordinatesAreRangeChecked() {
        assertTrue(Weather.validLatitude(0));
        assertTrue(Weather.validLatitude(48.85));
        assertTrue(Weather.validLatitude(-90));
        assertTrue(Weather.validLatitude(90));
        assertFalse(Weather.validLatitude(90.1));
        assertFalse(Weather.validLatitude(-90.1));
        assertFalse(Weather.validLatitude(Double.NaN));
        assertTrue(Weather.validLongitude(180));
        assertTrue(Weather.validLongitude(-180));
        assertFalse(Weather.validLongitude(180.1));
        assertFalse(Weather.validLongitude(Double.NaN));
    }

    @Test
    public void clearSkiesPickTheSunByDayAndTheMoonByNight() {
        assertEquals(R.drawable.ic_wx_clear, Weather.icon(0, true));
        assertEquals(R.drawable.ic_wx_clear_night, Weather.icon(0, false));
        assertEquals(R.drawable.ic_wx_clear, Weather.icon(1, true));    // mainly clear
    }

    @Test
    public void wmoGroupsMapToTheRightIcon() {
        assertEquals(R.drawable.ic_wx_partly, Weather.icon(2, true));
        assertEquals(R.drawable.ic_wx_cloudy, Weather.icon(3, true));
        assertEquals(R.drawable.ic_wx_fog, Weather.icon(45, true));
        assertEquals(R.drawable.ic_wx_fog, Weather.icon(48, true));
        assertEquals(R.drawable.ic_wx_drizzle, Weather.icon(53, true));
        assertEquals(R.drawable.ic_wx_rain, Weather.icon(63, true));
        assertEquals(R.drawable.ic_wx_rain, Weather.icon(81, true));
        assertEquals(R.drawable.ic_wx_snow, Weather.icon(75, true));
        assertEquals(R.drawable.ic_wx_snow, Weather.icon(86, true));
        assertEquals(R.drawable.ic_wx_storm, Weather.icon(96, true));
        assertEquals(R.drawable.ic_wx_cloudy, Weather.icon(-1, true));  // unknown reads as cloudy
    }

    @Test
    public void everyGroupHasItsOwnLabel() {
        assertEquals(R.string.wx_clear, Weather.label(0));
        assertEquals(R.string.wx_clear, Weather.label(1));
        assertEquals(R.string.wx_partly, Weather.label(2));
        assertEquals(R.string.wx_cloudy, Weather.label(100));
        assertEquals(R.string.wx_fog, Weather.label(48));
        assertEquals(R.string.wx_drizzle, Weather.label(56));
        assertEquals(R.string.wx_rain, Weather.label(65));
        assertEquals(R.string.wx_rain, Weather.label(80));
        assertEquals(R.string.wx_snow, Weather.label(77));
        assertEquals(R.string.wx_snow, Weather.label(85));
        assertEquals(R.string.wx_storm, Weather.label(95));
        assertEquals(R.string.wx_storm, Weather.label(99));
        assertNotEquals(Weather.label(0), Weather.label(3));
    }
}
