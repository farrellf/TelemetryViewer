import java.awt.Color;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.jogamp.opengl.GL2;

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
			divisionSize = closestDivSize5;
		
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
	 * Determines the best Log10 y values to use for vertical divisions. Division size will be either 1e1, 1e3 or 1e9.
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
//		if(divisionCount > Math.floor(maxDivisionsCount))
//			divisionCount = (int) Math.floor(maxDivisionsCount);
		
		Map<Float, String> yValues = new HashMap<Float, String>();
		for(int i = 0; i < divisionCount; i++) {
			float number = firstDivision - (i * divisionSize);
			String text = "1e" + Integer.toString((int) number);
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
		float maxDivisionsCount = plotWidth / (textWidth + minSpacingBetweenText);
		int divisionSize = (int) Math.ceil((maxX - minX) / maxDivisionsCount);
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
	 *                            1. Only %d %f %s or %b (boolean) can be used.
                                  2. A %d or %f can only be at the very beginning or very end. A %s or %b can only be at the very end.
                                  3. There must be a space between %d/%f/%s/%b and the rest of the text.
	 * @return                An Integer if %d was used, a Float if %f was used, a String if %s was used, a Boolean if %b was used, or null if no format specifier was used.
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
		
		// ending with %b, so a Boolean should be at the end of the text
		if(formatString.endsWith("%b")) {
			try {
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String actualText = text.substring(0, expectedText.length());
				String token = text.substring(expectedText.length()); 
				if(actualText.equals(expectedText))
					return Boolean.parseBoolean(token);
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			}
		}
		
		// formatString is not as expected
		throw new AssertionError("Line " + line + ": Source code contains an invalid format string.");
		
	}
	
	/**
	 * Parses an ASCII STL file to extract it's vertices and normal vectors.
	 * 
	 * Blender users: when exporting the STL file (File > Export > Stl) ensure the "Ascii" checkbox is selected,
	 * and ensure your model fits in a bounding box from -1 to +1 Blender units, centered at the origin.
	 * 
	 * @param fileStream    InputStream of an ASCII STL file.
	 * @returns             A float[] with a layout of u,v,w,x1,y1,z1,x2,y2,z2,x3,y3,z3 ... or null if the InputStream could not be parsed.
	 */
	public static float[] getShapeFromAsciiStl(InputStream fileStream) {
		
		try {
			
			// get the lines of text
			List<String> lines = new ArrayList<String>();
			Scanner s = new Scanner(fileStream);
			while(s.hasNextLine())
				lines.add(s.nextLine());
			s.close();
			
			// count the vertices
			int vertexCount = 0;
			for(String line : lines)
				if(line.startsWith("facet normal") || line.startsWith("vertex"))
					vertexCount += 3;
			
			
			// copy the vertices into the float[]
			float[] shape = new float[vertexCount];
			int vertex = 0;
			for(String line : lines) {
				if(line.startsWith("facet normal")) {
					String[] token = line.split(" ");
					shape[vertex++] = Float.parseFloat(token[2]);
					shape[vertex++] = Float.parseFloat(token[3]);
					shape[vertex++] = Float.parseFloat(token[4]);
				} else if(line.startsWith("vertex")) {
					String[] token = line.split(" ");
					shape[vertex++] = Float.parseFloat(token[1]);
					shape[vertex++] = Float.parseFloat(token[2]);
					shape[vertex++] = Float.parseFloat(token[3]);
				}
			}
			
			return shape;
			
		} catch (Exception e) {
			
			e.printStackTrace();
			return null;
			
		}
		
	}
	
	/**
	 * Draws a tooltip. An anchor point specifies where the tooltip should point to.
	 * 
	 * @param gl              The OpenGL context.
	 * @param text            Array of text to show.
	 * @param color           Corresponding array of colors to show at the left of each line of text.
	 * @param anchorX         X location to point to.
	 * @param anchorY         Y location to point to.
	 * @param topLeftX        Allowed bounding box's top-left x coordinate.
	 * @param topLeftY        Allowed bounding box's top-left y coordinate.
	 * @param bottomRightX    Allowed bounding box's bottom-right x coordinate.
	 * @param bottomRightY    Allowed bounding box's bottom-right y coordinate.
	 */
	public static void drawTooltip(GL2 gl, String[] text, Color[] colors, int anchorX, int anchorY, float topLeftX, float topLeftY, float bottomRightX, float bottomRightY) {
			
		final int NORTH      = 0;
		final int SOUTH      = 1;
		final int WEST       = 2;
		final int EAST       = 3;
		final int NORTH_WEST = 4;
		final int NORTH_EAST = 5;
		final int SOUTH_WEST = 6;
		final int SOUTH_EAST = 7;
		
		float padding = 6f * Controller.getDisplayScalingFactor();
		
		float maxTextWidth = FontUtils.tickTextWidth(text[0]);
		for(int i = 1; i < text.length; i++)
			if(FontUtils.tickTextWidth(text[i]) > maxTextWidth)
				maxTextWidth = FontUtils.tickTextWidth(text[i]);
		float textHeight = FontUtils.tickTextHeight;
		
		float boxWidth = textHeight + Theme.tooltipTextPadding + maxTextWidth + (2 * padding);
		float boxHeight = text.length * (textHeight + padding) + padding;
		
		// decide which orientation to draw the tooltip in, or return if there is not enough space
		int orientation = NORTH;
		if(anchorY + padding + boxHeight < topLeftY) {
			// there is space above the anchor, so use NORTH or NORTH_WEST or NORTH_EAST if there is enough horizontal space
			if(anchorX - (boxWidth / 2f) > topLeftX && anchorX + (boxWidth / 2f) < bottomRightX)
				orientation = NORTH;
			else if(anchorX - boxWidth > topLeftX && anchorX < bottomRightX)
				orientation = NORTH_WEST;
			else if(anchorX > topLeftX && anchorX + boxWidth < bottomRightX)
				orientation = NORTH_EAST;
			else
				return;
		} else if(anchorY + (boxHeight / 2f) < topLeftY && anchorY - (boxHeight / 2f) > bottomRightY) {
			// there is some space above and below the anchor, so use WEST or EAST if there is enough horizontal space
			if(anchorX - padding - boxWidth > topLeftX)
				orientation = WEST;
			else if(anchorX + padding + boxWidth < bottomRightX)
				orientation = EAST;
			else
				return;
		} else if(anchorY - padding - boxHeight > bottomRightY) {
			// there is space below the anchor, so use SOUTH or SOUTH_WEST or SOUTH_EAST if there is enough horizontal space
			if(anchorX - (boxWidth / 2f) > topLeftX && anchorX + (boxWidth / 2f) < bottomRightX)
				orientation = SOUTH;
			else if(anchorX - boxWidth > topLeftX && anchorX < bottomRightX)
				orientation = SOUTH_WEST;
			else if(anchorX > topLeftX && anchorX + boxWidth < bottomRightX)
				orientation = SOUTH_EAST;
			else
				return;
		} else {
			// there is not enough space anywhere
			return;
		}
		
		// draw the tooltip
		if(orientation == NORTH) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + (padding / 2f), anchorY + padding);
				gl.glVertex2f(anchorX - (padding / 2f), anchorY + padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY + padding);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY + padding);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY + padding + boxHeight);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY + padding);
				gl.glVertex2f(anchorX - (padding / 2f), anchorY + padding);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + (padding / 2f), anchorY + padding);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY + padding);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY + padding + boxHeight);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX - (boxWidth / 2f) + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY + padding + boxHeight - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == SOUTH) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - (padding / 2f), anchorY - padding);
				gl.glVertex2f(anchorX + (padding / 2f), anchorY - padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY - padding);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY - padding);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY - padding);
				gl.glVertex2f(anchorX - (boxWidth / 2f), anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + (boxWidth / 2f), anchorY - padding);
				gl.glVertex2f(anchorX + (padding / 2f), anchorY - padding);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - (padding / 2f), anchorY - padding);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX - (boxWidth / 2f) + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY - padding - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == WEST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - padding, anchorY + (padding / 2f));
				gl.glVertex2f(anchorX - padding, anchorY - (padding / 2f));
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX - padding - boxWidth, anchorY + (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding - boxWidth, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding, anchorY + (boxHeight / 2f));
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX - padding - boxWidth, anchorY + (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding - boxWidth, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX - padding, anchorY - (padding / 2f));
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - padding, anchorY + (padding / 2f));
				gl.glVertex2f(anchorX - padding, anchorY + (boxHeight / 2f));
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX - boxWidth + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY + (boxHeight / 2f) - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == EAST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + padding, anchorY - (padding / 2f));
				gl.glVertex2f(anchorX + padding, anchorY + (padding / 2f));
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX + padding, anchorY + (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding + boxWidth, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding + boxWidth, anchorY + (boxHeight / 2f));
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX + padding, anchorY + (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding, anchorY + (padding / 2f));
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + padding, anchorY - (padding / 2f));
				gl.glVertex2f(anchorX + padding, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding + boxWidth, anchorY - (boxHeight / 2f));
				gl.glVertex2f(anchorX + padding + boxWidth, anchorY + (boxHeight / 2f));
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX + (2f * padding) + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY + (boxHeight / 2f) - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == NORTH_WEST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX, anchorY + padding);
				gl.glVertex2f(anchorX - (0.85f * padding), anchorY + padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX - boxWidth, anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX - boxWidth, anchorY + padding);
				gl.glVertex2f(anchorX, anchorY + padding);
				gl.glVertex2f(anchorX, anchorY + padding + boxHeight);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX - boxWidth, anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX - boxWidth, anchorY + padding);
				gl.glVertex2f(anchorX - (0.85f * padding), anchorY + padding);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX, anchorY + padding + boxHeight);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX - boxWidth + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY + padding + boxHeight - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == NORTH_EAST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY + padding);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + (0.85f * padding), anchorY + padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX, anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX, anchorY + padding);
				gl.glVertex2f(anchorX + boxWidth, anchorY + padding);
				gl.glVertex2f(anchorX + boxWidth, anchorY + padding + boxHeight);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX, anchorY + padding + boxHeight);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX + (0.85f * padding), anchorY + padding);
				gl.glVertex2f(anchorX + boxWidth, anchorY + padding);
				gl.glVertex2f(anchorX + boxWidth, anchorY + padding + boxHeight);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY + padding + boxHeight - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == SOUTH_WEST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - (0.85f * padding), anchorY - padding);
				gl.glVertex2f(anchorX, anchorY - padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX - boxWidth, anchorY - padding);
				gl.glVertex2f(anchorX - boxWidth, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX, anchorY - padding);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX - boxWidth, anchorY - padding);
				gl.glVertex2f(anchorX - boxWidth, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX - (0.85f * padding), anchorY - padding);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX - boxWidth + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY - padding - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		} else if(orientation == SOUTH_EAST) {
			
			gl.glColor4fv(Theme.tooltipBackgroundColor, 0);
			gl.glBegin(GL2.GL_TRIANGLES);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX, anchorY - padding);
				gl.glVertex2f(anchorX + (0.85f * padding), anchorY - padding);
			gl.glEnd();
			gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2f(anchorX, anchorY - padding);
				gl.glVertex2f(anchorX, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + boxWidth, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + boxWidth, anchorY - padding);
			gl.glEnd();
			
			gl.glColor4fv(Theme.tooltipBorderColor, 0);
			gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glVertex2f(anchorX, anchorY);
				gl.glVertex2f(anchorX, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + boxWidth, anchorY - padding - boxHeight);
				gl.glVertex2f(anchorX + boxWidth, anchorY - padding);
				gl.glVertex2f(anchorX + (0.85f * padding), anchorY - padding);
			gl.glEnd();
			
			// draw the text and color boxes
			float textX = anchorX + padding + textHeight + Theme.tooltipTextPadding;
			for(int i = 0; i < text.length; i++) {
				float textY = anchorY - padding - ((i + 1) * (padding + textHeight));
				FontUtils.drawTickText(text[i], (int) textX, (int) textY);
				gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(new float[] {colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f, 1}, 0);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY + textHeight);
					gl.glVertex2f(textX - Theme.tooltipTextPadding - textHeight, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY);
					gl.glVertex2f(textX - Theme.tooltipTextPadding, textY + textHeight);
				gl.glEnd();
			}
			
		}
		
	}
	
}
