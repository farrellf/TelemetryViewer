import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class ColorPickerView extends JDialog {
	
	Color defaultColor;   // the color if the user does not pick a color
	Color chosenColor;    // the color if the user clicked on a swatch or typed in a hex color code
	Color mouseOverColor; // the color under the mouse cursor
	
	final int count = 4; // number of swatches along the edge of the color cube
	
	// colors and pixel locations are cached
	Map<Point, Color> cache; // key = swatch center, value = swatch color
	int[] xLeftCubeBackground;
	int[] yLeftCubeBackground;
	int[] xRightCubeBackground;
	int[] yRightCubeBackground;
	int swatchDiameter;
	int cachedWidth;
	int cachedHeight;
	
	/**
	 * Shows the palette dialog box, and returns the chosen Color.
	 * The user can pick a color, or type in a color code, or cancel (by closing the window or pressing ESC.)
	 * 
	 * @param name            Dataset name or Bitfield State name to show in the title bar.
	 * @param defaultColor    The default color, in case the user does not choose a color.
	 * @return                The color.
	 */
	public static Color getColor(String name, Color defaultColor) {
		
		ColorPickerView picker = new ColorPickerView(name, defaultColor);
		return (picker.chosenColor != null) ? picker.chosenColor : picker.defaultColor;
		
	}
	
	/**
	 * A palette where the user can pick a color or type in a hex color code.
	 * 
	 * @param name     Dataset name or Bitfield State name to show in the title bar.
	 * @param color    The default color, in case the user does not choose a color.
	 */
	private ColorPickerView(String name, Color color) {
		
		super();
		defaultColor = color;
		chosenColor = null;
		mouseOverColor = null;
		
		setSize((int) (600 * ChartsController.getDisplayScalingFactorForGUI()), (int) (400 * ChartsController.getDisplayScalingFactorForGUI()));
		setTitle("Pick a Color for \"" + name + "\"");
		setLayout(new BorderLayout());
		add(new Palette(), BorderLayout.CENTER);
		
		setModal(true);
		setLocationRelativeTo(Main.window);
		setVisible(true); // this blocks
		
	}
	
	/**
	 * Returns a "gamma corrected" color, but this has been tweaked to look "better" to me.
	 * This is NOT mathematically or perceptually correct code.
	 * 
	 * @param r    Red, in the range (0,1).
	 * @param g    Green, in the range (0,1).
	 * @param b    Blue, in the range (0,1).
	 * @return     The resulting color.
	 */
	private Color colorFromLinear(float r, float g, float b) {
		
		float gammaR  = (float) Math.pow(r, 1/2.5);
		float gammaG  = (float) Math.pow(g, 1/2.5);
		float gammaB  = (float) Math.pow(b, 1/2.5);
		
		float scalar = (float) Math.pow(r*g*b, 0.2);
		
		r = (scalar*gammaR) + (1-scalar)*r;
		g = (scalar*gammaG) + (1-scalar)*g;
		b = (scalar*gammaB) + (1-scalar)*b;
		
		return new Color(r, g, b);
		
	}
	
	/**
	 * Fills the cache with colors and their corresponding locations on screen.
	 * 
	 * @param width     Width of the palette, in pixels.
	 * @param height    Height of the palette, in pixels.
	 */
	private void generateCache(int width, int height) {
		
		// flush the cache
		cache = new HashMap<Point, Color>();
		xLeftCubeBackground = new int[6];
		yLeftCubeBackground = new int[6];
		xRightCubeBackground = new int[6];
		yRightCubeBackground = new int[6];
		swatchDiameter = 0;
		cachedWidth = width;
		cachedHeight = height;
		
		// calculate locations
		double cubeRadius = Integer.min(width/2, height) / 2.0;
		cubeRadius *= (double) count / (double) (count+1);
		double yCubeCenter = height / 2;
		swatchDiameter = (int) (cubeRadius / count);
		double xLeftCubeCenter = width / 3.0 - swatchDiameter*1.5;
		double xRightCubeCenter = width * 2.0 / 3.0 + swatchDiameter*1.5;
		
		// populate the cache
		xLeftCubeBackground[0] = (int) (xLeftCubeCenter);
		xLeftCubeBackground[1] = (int) (xLeftCubeCenter + 0.87 * cubeRadius);
		xLeftCubeBackground[2] = (int) (xLeftCubeCenter + 0.87 * cubeRadius);
		xLeftCubeBackground[3] = (int) (xLeftCubeCenter);
		xLeftCubeBackground[4] = (int) (xLeftCubeCenter - 0.87 * cubeRadius);
		xLeftCubeBackground[5] = (int) (xLeftCubeCenter - 0.87 * cubeRadius);
		
		yLeftCubeBackground[0] = (int) (yCubeCenter - cubeRadius);
		yLeftCubeBackground[1] = (int) (yCubeCenter - 0.5 * cubeRadius);
		yLeftCubeBackground[2] = (int) (yCubeCenter + 0.5 * cubeRadius);
		yLeftCubeBackground[3] = (int) (yCubeCenter + cubeRadius);
		yLeftCubeBackground[4] = (int) (yCubeCenter + 0.5 * cubeRadius);
		yLeftCubeBackground[5] = (int) (yCubeCenter - 0.5 * cubeRadius);
		
		xRightCubeBackground[0] = (int) (xRightCubeCenter);
		xRightCubeBackground[1] = (int) (xRightCubeCenter + 0.87 * cubeRadius);
		xRightCubeBackground[2] = (int) (xRightCubeCenter + 0.87 * cubeRadius);
		xRightCubeBackground[3] = (int) (xRightCubeCenter);
		xRightCubeBackground[4] = (int) (xRightCubeCenter - 0.87 * cubeRadius);
		xRightCubeBackground[5] = (int) (xRightCubeCenter - 0.87 * cubeRadius);
		
		yRightCubeBackground[0] = (int) (yCubeCenter - cubeRadius);
		yRightCubeBackground[1] = (int) (yCubeCenter - 0.5 * cubeRadius);
		yRightCubeBackground[2] = (int) (yCubeCenter + 0.5 * cubeRadius);
		yRightCubeBackground[3] = (int) (yCubeCenter + cubeRadius);
		yRightCubeBackground[4] = (int) (yCubeCenter + 0.5 * cubeRadius);
		yRightCubeBackground[5] = (int) (yCubeCenter - 0.5 * cubeRadius);
		
		for(int red = 0; red <= count; red++) {
			for(int blue = 0; blue <= count; blue++) {
				float r = (float) red / (float) count;
				float g = 0;
				float b = (float) blue / (float) count;
				double originX = xRightCubeCenter - 0.87 * (b * cubeRadius);
				double originY = yCubeCenter - (r * cubeRadius) + (b * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
		for(int red = 0; red <= count; red++) {
			for(int green = 0; green <= count; green++) {
				float r = (float) red / (float) count;
				float g = (float) green / (float) count;
				float b = 0;
				double originX = xRightCubeCenter + 0.87 * (g * cubeRadius);
				double originY = yCubeCenter - (r * cubeRadius) + (g * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
		for(int green = 0; green <= count; green++) {
			for(int blue = 0; blue <= count; blue++) {
				float r = 0;
				float g = (float) green / (float) count;
				float b = (float) blue / (float) count;
				double originX = xRightCubeCenter + 0.87 * (g * cubeRadius) - 0.87 * (b * cubeRadius);
				double originY = yCubeCenter + (g * cubeRadius / 2.0) + (b * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
		for(int green = 0; green <= count; green++) {
			for(int blue = 0; blue <= count; blue++) {
				float r = 1;
				float g = (float) green / (float) count;
				float b = (float) blue  / (float) count;
				double originX = xLeftCubeCenter + 0.87 * (b * cubeRadius) - 0.87 * (g * cubeRadius);
				double originY = yCubeCenter - cubeRadius + (b * cubeRadius / 2.0) + (g * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
		for(int red = 0; red <= count; red++) {
			for(int blue = 0; blue <= count; blue++) {
				float r = (float) red / (float) count;
				float g = 1;
				float b = (float) blue / (float) count;
				double originX = xLeftCubeCenter - 0.87 * cubeRadius + 0.87 * (b * cubeRadius);
				double originY = yCubeCenter + (cubeRadius / 2.0) - (r * cubeRadius) + (b * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
		for(int red = 0; red <= count; red++) {
			for(int green = 0; green <= count; green++) {
				float r = (float) red / (float) count;
				float g = (float) green / (float) count;
				float b = 1;
				double originX = xLeftCubeCenter + 0.87 * cubeRadius - 0.87 * (g * cubeRadius);
				double originY = yCubeCenter + (cubeRadius / 2.0) - (r * cubeRadius) + (g * cubeRadius / 2.0);
				cache.put(new Point((int) originX, (int) originY), colorFromLinear(r, g, b));
			}
		}
		
	}
	
	/**
	 * A JPanel where the palette is drawn on screen and keyboard/mouse events are handled.
	 */
	private class Palette extends JPanel {
		
		JTextField colorCodeTextfield;
		boolean mouseClicked;
		int mouseX;
		int mouseY;
		
		public Palette() {

			super();
			
			mouseClicked = false;
			mouseX = -1;
			mouseY = -1;
			
			colorCodeTextfield = new JTextField(String.format("0x%02X%02X%02X", defaultColor.getRed(), defaultColor.getGreen(), defaultColor.getBlue()), 8);
			colorCodeTextfield.addKeyListener(new KeyListener() {
				// update the chosenColor based on what the user typed in
				@Override public void keyReleased(KeyEvent e) {
					try {
						String text = colorCodeTextfield.getText().trim();
						while(text.startsWith("0x") || text.startsWith("0X"))
							text = text.substring(2);
						colorCodeTextfield.setText("0x" + text.toUpperCase());
						int number = Integer.parseInt(text, 16);
						if(number < 0 || number > 0xFFFFFF)
							return;
						int red   = (number >> 16) & 0xFF;
						int green = (number >> 8)  & 0xFF;
						int blue  = (number >> 0)  & 0xFF;
						chosenColor = new Color(red, green, blue);
						repaint();
					} catch(Exception ex) { }
				}
				// press ESC to cancel
				@Override public void keyPressed(KeyEvent e)  {
					if(e.getKeyChar() == KeyEvent.VK_ESCAPE) {
						chosenColor = null;
						ColorPickerView.this.setVisible(false);
						ColorPickerView.this.dispose();
					}
				}
				@Override public void keyTyped(KeyEvent e)    { }
			});
			// press Enter to close the window
			colorCodeTextfield.addActionListener(event -> {
				try {
					String text = colorCodeTextfield.getText().trim();
					while(text.startsWith("0x") || text.startsWith("0X"))
						text = text.substring(2);
					colorCodeTextfield.setText("0x" + text.toUpperCase());
					int number = Integer.parseInt(text, 16);
					if(number < 0 || number > 0xFFFFFF)
						return;
					int red   = (number >> 16) & 0xFF;
					int green = (number >> 8)  & 0xFF;
					int blue  = (number >> 0)  & 0xFF;
					chosenColor = new Color(red, green, blue);
					setVisible(false);
					ColorPickerView.this.dispose();
				} catch(Exception ex) { }
			});
			colorCodeTextfield.addFocusListener(new FocusListener() {
				@Override public void focusGained(FocusEvent e) {
					colorCodeTextfield.selectAll();
				}
				@Override public void focusLost(FocusEvent e) { }
			});
			
			setLayout(null);
			add(colorCodeTextfield);
			
			addMouseListener(new MouseListener() {
				@Override public void mousePressed(MouseEvent e) {
					mouseClicked = true;
					mouseX = e.getX();
					mouseY = e.getY();
					repaint();
				}
				@Override public void mouseExited(MouseEvent e) {
					mouseX = -1;
					mouseY = -1;
					repaint();
				}
				@Override public void mouseClicked(MouseEvent e)  { }
				@Override public void mouseReleased(MouseEvent e) { }
				@Override public void mouseEntered(MouseEvent e)  { }
			});
			
			addMouseMotionListener(new MouseMotionListener() {
				@Override public void mouseMoved(MouseEvent e) {
					mouseX = e.getX();
					mouseY = e.getY();
					repaint();
				}
				@Override public void mouseDragged(MouseEvent e) { }
			});
			
		}
		
		@Override public void paintComponent(Graphics g) {
			
			int width = getWidth();
			int height = getHeight();
			
			// flush the cache if necessary
			if(cache == null || cachedWidth != width || cachedHeight != height)
				generateCache(width, height);
			
			// draw the background
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(2f));
			g2.setColor(getBackground());
			g2.fillRect(0, 0, width, height);
			
			// position the textfield and surround it with the color
			Dimension dim = colorCodeTextfield.getPreferredSize();
			colorCodeTextfield.setBounds(width/2 - dim.width/2, height - dim.height*2, dim.width, dim.height);
			g2.setColor((mouseOverColor != null) ? mouseOverColor :
			            (chosenColor    != null) ? chosenColor :
			                                       defaultColor);
			g2.fillRoundRect(width/2 - dim.width/2 - dim.height/2, height - dim.height*2 - dim.height/2, dim.width + dim.height, dim.height*2, dim.height, dim.height);
			
			// draw the white and black hexagon backgrounds
			g2.setColor(Color.WHITE);
			g2.fillPolygon(xLeftCubeBackground, yLeftCubeBackground, 6);
			g2.setColor(Color.BLACK);
			g2.fillPolygon(xRightCubeBackground, yRightCubeBackground, 6);
			
			// draw the swatches
			boolean mouseOverSwatch = false;
			int outlineX = 0;
			int outlineY = 0;
			Color outlineColor = null;
			
			for(Map.Entry<Point, Color> entry : cache.entrySet()) {
				
				// draw the swatch
				Point p = entry.getKey();
				Color c = entry.getValue();
				g2.setColor(c);
				g2.fillOval(p.x - swatchDiameter/2, p.y - swatchDiameter/2, swatchDiameter, swatchDiameter);
				
				// draw a dot if this color is used by any dataset or bitfield state
				boolean colorUsed = false;
				for(Dataset d : DatasetsController.getAllDatasets()) {
					if(d.color.equals(c))
						colorUsed = true;
					if(d.isBitfield)
						for(Dataset.Bitfield b : d.getBitfields())
							for(Dataset.Bitfield.State s : b.states)
								if(s.color.equals(c))
									colorUsed = true;
				}
				if(colorUsed) {
					g2.setColor(p.x < width / 2 ? Color.BLACK : Color.WHITE);
					g2.fillOval(p.x - swatchDiameter/8, p.y - swatchDiameter/8, swatchDiameter/4, swatchDiameter/4);
				}
				
				// draw a thin outline if this color is the chosenColor or defaultColor
				if((chosenColor != null && c.equals(chosenColor)) || (chosenColor == null && c.equals(defaultColor))) {
					g2.setColor(p.x < width / 2 ? Color.BLACK : Color.WHITE);
					g2.drawOval(p.x - swatchDiameter/2, p.y - swatchDiameter/2, swatchDiameter, swatchDiameter);
				}
				
				int distance = (int) Math.sqrt((p.x - mouseX)*(p.x - mouseX) + (p.y - mouseY)*(p.y - mouseY));
				if(distance < swatchDiameter/2) {
					mouseOverSwatch = true;
					if(mouseOverColor != c) {
						mouseOverColor = c;
						colorCodeTextfield.setText(String.format("0x%02X%02X%02X", mouseOverColor.getRed(), mouseOverColor.getGreen(), mouseOverColor.getBlue()));
						repaint();
					}
					outlineX = p.x - swatchDiameter/2;
					outlineY = p.y - swatchDiameter/2;
					outlineColor = p.x < width/2 ? Color.BLACK : Color.WHITE;
				}
				
			}
			
			if(mouseOverSwatch) {
				
				// draw a thick outline if mouseOver a swatch
				Stroke originalStroke = g2.getStroke();
				g2.setColor(outlineColor);
				g2.setStroke(new BasicStroke(2*ChartsController.getDisplayScalingFactorForGUI()));
				g2.drawOval(outlineX, outlineY, swatchDiameter, swatchDiameter);
				
				// draw a tooltip if any datasets or bitfield states use this color
				List<String> datasetNames = new ArrayList<String>();
				for(Dataset d : DatasetsController.getAllDatasets()) {
					
					if(d.color.equals(mouseOverColor))
						datasetNames.add(d.name);

					if(d.isBitfield)
						for(Dataset.Bitfield b : d.getBitfields())
							for(Dataset.Bitfield.State s : b.states)
								if(s.color.equals(mouseOverColor))
									datasetNames.add(d.name + ": " + s.name);
					
				}
				
				if(!datasetNames.isEmpty()) {
					
					// calculate tooltip size
					int textHeight = getFontMetrics(getFont()).getHeight();
					int maxTextWidth = 0;
					for(String s : datasetNames) {
						int textWidth = getFontMetrics(getFont()).stringWidth(s);
						if(textWidth > maxTextWidth)
							maxTextWidth = textWidth;
					}
					int gap = getFontMetrics(getFont()).getDescent() * 2;
					
					// calculate tooltip location
					int boxWidth = maxTextWidth + 2*gap;
					int boxHeight = textHeight*datasetNames.size() + gap;
					int xBoxLeft = outlineX + swatchDiameter/2 - boxWidth/2;
					if(xBoxLeft + boxWidth > width)
						xBoxLeft = width - boxWidth - (int) ChartsController.getDisplayScalingFactorForGUI();
					if(xBoxLeft < 0)
						xBoxLeft = 0;
					int yBoxTop = outlineY - boxHeight;
					if(yBoxTop < 0)
						yBoxTop = outlineY + swatchDiameter;
					int xTextLeft = xBoxLeft + gap;
					int yTextBaseline = yBoxTop + textHeight;
					
					// draw the tooltip
					g2.setColor(Color.WHITE);
					g2.fillRect(xBoxLeft, yBoxTop, boxWidth, boxHeight);
					g2.setColor(Color.BLACK);
					for(String s : datasetNames) {
						g2.drawString(s, xTextLeft, yTextBaseline);
						yTextBaseline += textHeight;
					}
					g2.setStroke(originalStroke);
					g2.drawRect(xBoxLeft, yBoxTop, boxWidth, boxHeight);
					
				}
				
				if(mouseClicked) {
					chosenColor = mouseOverColor;
					setVisible(false);
					ColorPickerView.this.dispose();
				}
				
			} else if(mouseOverColor != null) {
				// if no longer mouseOver a swatch, update the textfield to show the chosen or default color
				mouseOverColor = null;
				colorCodeTextfield.setText((chosenColor != null) ? String.format("0x%02X%02X%02X", chosenColor.getRed(),  chosenColor.getGreen(),  chosenColor.getBlue()) :
				                                                   String.format("0x%02X%02X%02X", defaultColor.getRed(), defaultColor.getGreen(), defaultColor.getBlue()));
				repaint();
			}
			
			if(mouseClicked) {
				mouseClicked = false;
				mouseX = -1;
				mouseY = -1;
			}
			
		}
		
	}

}
