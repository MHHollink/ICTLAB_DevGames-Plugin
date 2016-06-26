package nl.devgames.dateconverters;

import java.text.ParseException;
import java.util.Date;

public interface DateConverter
{
    Date convertStringToDate(String dateString) throws ParseException;
    String convertDateToString(Date date);
}
