import java.util.HashMap;
import java.util.Map;

public class ChartUtils {

	/**
	 * Determines the best y values to use for vertical divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
	 * 
	 * @param plotHeight    Number of pixels for the y-axis
	 * @param minY          Y value at the bottom of the plot
	 * @param maxY          Y value at the top of the plot
	 * @return              A Map of the y values for each division, keys are Floats and values are formatted Strings
	 */
	public static Map<Float, String> getYdivisions125(float plotHeight, float minY, float maxY) {
		
		// calculate the best vertical division size
		float minSpacingBetweenText = 2.0f * FontUtils.tickTextHeight;
		float maxDivisionsCount = plotHeight / (FontUtils.tickTextHeight + minSpacingBetweenText) + 1.0f;
		float divisionSize = (maxY - minY) / maxDivisionsCount;
		float closestDivSize1 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1.0f; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
		float closestDivSize2 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2.0f; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
		float closestDivSize5 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5.0f; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
		float error1 = closestDivSize1 - divisionSize;
		float error2 = closestDivSize2 - divisionSize;
		float error5 = closestDivSize5 - divisionSize;
		if(error1 < error2 && error1 < error5)
			divisionSize = closestDivSize1;
		else if(error2 < error1 && error2 < error5)
			divisionSize = closestDivSize2;
		else
			divisionSize= closestDivSize5;
		
		// decide if the numbers should be displayed as integers, or as floats to one significant decimal place
		int precision = 0;
		String format = "";
		if(divisionSize < 0.99) {
			precision = 1;
			float size = divisionSize;
			while(size * (float) Math.pow(10, precision) < 1.0f)
				precision++;
			format = "%." + precision + "f";
		}
		
		// calculate the values for each vertical division
		float firstDivision = maxY - (maxY % divisionSize);
		float lastDivision  = minY - (minY % divisionSize);
		if(firstDivision > maxY)
			firstDivision -= divisionSize;
		if(lastDivision < minY)
			lastDivision += divisionSize;
		int divisionCount = (int) Math.round((firstDivision - lastDivision) / divisionSize) + 1;
		
		Map<Float, String> yValues = new HashMap<Float, String>();
		for(int i = 0; i < divisionCount; i++) {
			float number = firstDivision - (i * divisionSize);
			String text;
			if(precision == 0) {
				text = Integer.toString((int) number);
			} else {
				text = String.format(format, number);
			}
			yValues.put(number, text);
		}
		
		return yValues;
		
	}
	
	/**
	 * Determines the best Log y values to use for vertical divisions. Division size will be either 10e1, 10e3 or 10e9.
	 * 
	 * @param plotHeight    Number of pixels for the y-axis
	 * @param minY          Y value at the bottom of the plot
	 * @param maxY          Y value at the top of the plot
	 * @return              A Map of the y values for each division, keys are Floats and values are formatted Strings
	 */
	public static Map<Float, String> getLogYdivisions(float plotHeight, float minY, float maxY) {
		
		// calculate the best vertical division size
		float minSpacingBetweenText = 2.0f * FontUtils.tickTextHeight;
		float maxDivisionsCount = plotHeight / (FontUtils.tickTextHeight + minSpacingBetweenText) + 1.0f;
		float divisionSize = (maxY - minY) / maxDivisionsCount;
		float divSize1 = 1.0f; // 1W, 100mW, 10mW, 1mW, 100uW, ...
		float divSize3 = 3.0f; // 1W, 1mW, 1uW, ...
		float divSize9 = 9.0f; // 1W, 1nW, ...
		float error1 = divSize1 - divisionSize;
		float error3 = divSize3 - divisionSize;
		float error9 = divSize9 - divisionSize;
		if(error1 > 0 && error1 < error3 && error1 < error9)
			divisionSize = divSize1;
		else if(error3 > 0 && error3 < error9)
			divisionSize = divSize3;
		else if(error9 > 0)
			divisionSize = divSize9;
		else
			return new HashMap<Float, String>();
		
		// calculate the values for each vertical division
		float firstDivision = maxY - (maxY % divisionSize);
		float lastDivision  = minY - (minY % divisionSize);
		if(firstDivision > maxY)
			firstDivision -= divisionSize;
		if(lastDivision < minY)
			lastDivision += divisionSize;
		int divisionCount = (int) Math.round((firstDivision - lastDivision) / divisionSize) + 1;
		if(divisionCount > Math.floor(maxDivisionsCount))
			divisionCount = (int) Math.floor(maxDivisionsCount);
		
		Map<Float, String> yValues = new HashMap<Float, String>();
		for(int i = 0; i < divisionCount; i++) {
			float number = firstDivision - (i * divisionSize);
			String text = "10e" + Integer.toString((int) number);
			yValues.put(number, text);
		}
		
		return yValues;
		
	}
	
	/**
	 * Determines the best integer x values to use for horizontal divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
	 * 
	 * @param plotWidth    Number of pixels for the x-axis
	 * @param minX         X value at the left of the plot
	 * @param maxX         X value at the right of the plot
	 * @return             A Map of the x values for each division, keys are Integers and values are formatted Strings
	 */
	public static Map<Integer, String> getXdivisions125(float plotWidth, int minX, int maxX) {
		
		// calculate the best horizontal division size
		int textWidth = (int) Float.max(FontUtils.tickTextWidth(Integer.toString(maxX)), FontUtils.tickTextWidth(Integer.toString(minX)));
		int minSpacingBetweenText = textWidth;
		int maxDivisionsCount = (int) plotWidth / (textWidth + minSpacingBetweenText) + 1;
		int divisionSize = (maxX - minX) / maxDivisionsCount;
		if(divisionSize == 0) divisionSize = 1;
		int closestDivSize1 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
		int closestDivSize2 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
		int closestDivSize5 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
		int error1 = closestDivSize1 - divisionSize;
		int error2 = closestDivSize2 - divisionSize;
		int error5 = closestDivSize5 - divisionSize;
		if(error1 < error2 && error1 < error5)
			divisionSize = closestDivSize1;
		else if(error2 < error1 && error2 < error5)
			divisionSize = closestDivSize2;
		else
			divisionSize= closestDivSize5;
		
		// calculate the values for each horizontal division
		int firstDivision = maxX - (maxX % divisionSize);
		int lastDivision  = minX - (minX % divisionSize);
		if(firstDivision > maxX)
			firstDivision -= divisionSize;
		if(lastDivision < minX)
			lastDivision += divisionSize;
		int divisionCount = ((firstDivision - lastDivision) / divisionSize + 1);
		
		Map<Integer, String> xValues = new HashMap<Integer, String>();
		for(int i = 0; i < divisionCount; i++) {
			int number = lastDivision + (i * divisionSize);
			String text = Integer.toString(number);
			xValues.put(number, text);
		}
		
		return xValues;
		
	}
	
	/**
	 * Determines the best floating point x values to use for horizontal divisions. The 1/2/5 pattern is used (.1,.2,.5,1,2,5,10,20,50...)
	 * 
	 * @param plotWidth    Number of pixels for the x-axis
	 * @param minX         X value at the left of the plot
	 * @param maxX         X value at the right of the plot
	 * @return             A Map of the x values for each division, keys are Integers and values are formatted Strings
	 */
	public static Map<Float, String> getFloatXdivisions125(float plotWidth, float minX, float maxX) {
		
		Map<Float, String> xValues = new HashMap<Float, String>();
		
		for(int maxDivisionsCount = 1; maxDivisionsCount < 100; maxDivisionsCount++) {
			
			float divisionSize = (maxX - minX) / (float) maxDivisionsCount;
			float closestDivSize1 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
			float closestDivSize2 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
			float closestDivSize5 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
			float error1 = closestDivSize1 - divisionSize;
			float error2 = closestDivSize2 - divisionSize;
			float error5 = closestDivSize5 - divisionSize;
			if(error1 < error2 && error1 < error5)
				divisionSize = closestDivSize1;
			else if(error2 < error1 && error2 < error5)
				divisionSize = closestDivSize2;
			else
				divisionSize= closestDivSize5;
			
			// decide if the numbers should be displayed as integers, or as floats to one significant decimal place
			int precision = 0;
			String format = "";
			if(divisionSize < 0.99) {
				precision = 1;
				float size = divisionSize;
				while(size * (float) Math.pow(10, precision) < 1.0f)
					precision++;
				format = "%." + precision + "f";
			}
			
			// calculate the values for each vertical division
			float firstDivision = maxX - (maxX % divisionSize);
			float lastDivision  = minX - (minX % divisionSize);
			firstDivision += divisionSize; // compensating for floating point error that may skip the end points
			lastDivision -= divisionSize;
			while(firstDivision > maxX)
				firstDivision -= divisionSize;
			while(lastDivision < minX)
				lastDivision += divisionSize;
			int divisionCount = (int) Math.round((firstDivision - lastDivision) / divisionSize) + 1;
			
			Map<Float, String> proposedXvalues = new HashMap<Float, String>();
			for(int i = 0; i < divisionCount; i++) {
				float number = firstDivision - (i * divisionSize);
				String text;
				if(precision == 0) {
					text = Integer.toString((int) number);
				} else {
					text = String.format(format, number);
				}
				proposedXvalues.put(number, text);
			}
			
			// calculate how much width is taken up by the text
			float width = 0;
			for(String s : proposedXvalues.values())
				width += FontUtils.tickTextWidth(s);
			
			// stop and don't use this iteration if we're using more than half of the width
			if(width > plotWidth / 2.0f)
				break;
			
			xValues = proposedXvalues;
			
		}
		
		return xValues;
		
	}
	
	/**
	 * Formats a double as a string, limiting the total number of digits to a specific length, but never truncating the integer part.
	 * 
	 * For example, with a digitCount of 4:
	 * 1.234567 -> "1.234"
	 * 12.34567 -> "12.34"
	 * 123456.7 -> "123456"
	 * -1.23456 -> "-1.234"
	 * 
	 * @param number        The double to format.
	 * @param digitCount    How many digits to clip to.
	 * @return              The double formatted as a String.
	 */
	public static String formattedNumber(double number, int digitCount) {
		
		String text = String.format("%.9f", number);
		int pointLocation = text.indexOf('.');
		int stringLength = text.charAt(0) == '-' ? digitCount + 2 : digitCount + 1;
		return text.substring(0, pointLocation < stringLength ? stringLength : pointLocation);
		
	}
	
	/**
	 * Takes a string of text and attempts to parse it with a format string.
	 * Throws an exception if the text does not match the format string, or if the format string is invalid.
	 * 
	 * @param line            Line number to show in the error message if an error occurs.
	 * @param text            Line of text to parse.
	 * @param formatString    Printf-style format string but with many limitations:
	 *                            1. Only %d %f or %s can be used.
                                  2. A %d or %f can only be at the very beginning or very end. A %s can only be at the very end.
                                  3. There must be a space between %d/%f/%s and the rest of the text.
	 * @return                An Integer if %d was used, a Float if %f was used, a String if %s was used, or null if no format specifier was used.
	 */
	public static Object parse(int line, String text, String formatString) {
		
		// error message line numbers should start at 1 but the argument starts at 0
		line++;
		
		// no format specifier, so just ensure the text matches the formatString exactly
		if(!formatString.contains("%")) {
			if(text.equals(formatString))
				return null;
			else
				throw new AssertionError("Line " + line + ": Text does not match the expected value.");
		}
		
		// starting with %d, so an integer should be at the start of the text
		if(formatString.startsWith("%d")) {
			try {
				String[] tokens = text.split(" ");
				int number = Integer.parseInt(tokens[0]);
				String expectedText = formatString.substring(2);
				String remainingText = "";
				for(int i = 1; i < tokens.length; i++)
					remainingText += " " + tokens[i];
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not start with an integer.");
			}
		}
		
		// starting with %f, so a float should be at the start of the text
		if(formatString.startsWith("%f")) {
			try {
				String[] tokens = text.split(" ");
				float number = Float.parseFloat(tokens[0]);
				String expectedText = formatString.substring(2);
				String remainingText = "";
				for(int i = 1; i < tokens.length; i++)
					remainingText += " " + tokens[i];
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not start with a floating point number.");
			}
		}
		
		// ending with %d, so an integer should be at the end of the text
		if(formatString.endsWith("%d")) {
			try {
				String[] tokens = text.split(" ");
				int number = Integer.parseInt(tokens[tokens.length - 1]);
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String remainingText = "";
				for(int i = 0; i < tokens.length - 1; i++)
					remainingText += tokens[i] + " ";
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not end with an integer.");
			}
		}
		
		// ending with %f, so a float should be at the end of the text
		if(formatString.endsWith("%f")) {
			try {
				String[] tokens = text.split(" ");
				float number = Float.parseFloat(tokens[tokens.length - 1]);
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String remainingText = "";
				for(int i = 0; i < tokens.length - 1; i++)
					remainingText += tokens[i] + " ";
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not end with a floating point number.");
			}
		}
		
		// ending with %s, so a String should be at the end of the text
		if(formatString.endsWith("%s")) {
			try {
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String actualText = text.substring(0, expectedText.length());
				String token = text.substring(expectedText.length()); 
				if(actualText.equals(expectedText))
					return token;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			}
		}
		
		// formatString is not as expected
		throw new AssertionError("Line " + line + ": Source code contains an invalid format string.");
		
	}
	
}
