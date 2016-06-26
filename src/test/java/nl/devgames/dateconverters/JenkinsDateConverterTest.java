package nl.devgames.dateconverters;

import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class JenkinsDateConverterTest {

    @Test
    public void testJenkinsDateStringIsParsedCorrectly() throws Exception{
        DateConverter dateConverter = new JenkinsDateConverter();
        String date = "2016-06-11 12:00:30 +0200";
        Date parsedDate = dateConverter.convertStringToDate(date);
    }

    @Test(expected = ParseException.class)
    public void testWrongDateFormatThrowsParseException() throws Exception{
        DateConverter dateConverter = new JenkinsDateConverter();
        String date = "2016-06-11 12:00:30+0200";
        Date parsedDate = dateConverter.convertStringToDate(date);
    }

    @Test
    public void testFormattedDateStringMatchesExpectedOutcome() {
        DateConverter dateConverter = new JenkinsDateConverter();
        String expectedOutcome = "2016-06-11 12:00:30 +0200";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016,Calendar.JUNE,11,12,0,30);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT+2:00"));
        Date dateToFormat = calendar.getTime();
        String actualOutcome = dateConverter.convertDateToString(dateToFormat);
        assertEquals(expectedOutcome, actualOutcome);
    }
}