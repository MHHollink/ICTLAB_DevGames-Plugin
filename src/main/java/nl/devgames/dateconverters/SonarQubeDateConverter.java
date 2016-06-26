package nl.devgames.dateconverters;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SonarQubeDateConverter implements DateConverter {
    private String dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ";

    @Override
    public Date convertStringToDate(String dateString) throws ParseException {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat);
        return dateFormatter.parse(dateString);
    }

    @Override
    public String convertDateToString(Date date) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat);
        return dateFormatter.format(date);
    }
}