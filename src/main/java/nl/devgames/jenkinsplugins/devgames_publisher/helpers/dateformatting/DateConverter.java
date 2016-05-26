package nl.devgames.jenkinsplugins.devgames_publisher.helpers.dateformatting;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateConverter {
    public static final String JENKINS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss Z";
    public static final String SONARQUBE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    private DateConverter() {
    }

    public static Date convertStringToDate(String dateString, String dateFormat) throws ParseException {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat);
        return dateFormatter.parse(dateString);
    }

    public static String convertDateToString(Date date, String dateFormat){
        SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat);
        return dateFormatter.format(date);
    }
}
