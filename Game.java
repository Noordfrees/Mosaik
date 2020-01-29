import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Game {
	
	public static final String DEFAULT_BRICKSET = "Default";
	
	private final JFrame frame;
	private final JLabel display;
	
	private String currentImageName, lastImageName;
	private BufferedImage image;
	private boolean[][] occupied;
	private long points;
	private int trash;
	private Brick[] waiting;
	private int highlightBrick, selectedBrick;
	private Point mousePos;
	
	private Shape[] waitingRects;
	private Rectangle trashRect;
	
	private BrickSet brickset;
	private Menu menu;
	
	private static final Highscore[] highscores = new Highscore[10];
	public static class Highscore {
		public final String name;
		public final long points;
		public Highscore(String n, long p) {
			name = n;
			points = p;
		}
	}
	
	private abstract class Message {
		public Message(String header, String body, boolean save) {
			title = header;
			text = body;
			allowSaving = save;
		}
		public final boolean allowSaving;
		public final String title;
		protected String text;
		public String text() {
			return text;
		}
		public abstract void function(InputEvent e);
	}
	private Message message;
	
	public static class BrickSet {
		public BrickDescription random() {
			return descrs[(int)(descrs.length * Math.random())];
		}
		public BrickDescription get(int id) {
			for (BrickDescription d : descrs)
				if (d.id == id)
					return d;
			return null;
		}
		public final String name;
		private final BrickDescription[] descrs;
		private static class MutableBrickDescription {
			public ArrayList<Point> occupies;
			public Color colour;
			public MutableBrickDescription() {
				occupies = new ArrayList<>();
				colour = Color.BLUE;
			}
			public BrickDescription create(int id) {
				return new BrickDescription(id, colour, occupies.toArray(new Point[0]));
			}
		}
		public BrickSet(String n) {
			name = n;
			File f = new File("data/bricks", name);
			Map<String, MutableBrickDescription> list = new LinkedHashMap<>();
			try {
				java.util.List<String> lines = Files.readAllLines(f.toPath());
				/* Syntax for brickset files:
				 * 
				 * Each line starts with a command and the name of a brick.
				 * Next is the action to perform on the brick:
				 *   · new <name>         – create brick with given name
				 *   · add <name> <x> <y> – add a tile to the given brick at the given point
				 *   · col <name> <rgb>   – set the given brick's color to the given RGB value
				 *   · #                  – comment
				 */
				for (String s : lines) {
					while (s.contains("  "))
						s = s.replaceAll("  ", " ");
					s = s.trim();
					if (s.isEmpty() || s.startsWith("#")) {
						continue;
					}
					String[] str = s.split(" ");
					switch (str[0]) {
						case "new":
							if (list.containsKey(str[1]))
								throw new Exception("Attempt to add already known brick \"" + str[1] + "\"!");
							else
								list.put(str[1], new MutableBrickDescription());
							break;
						case "col":
							if (list.containsKey(str[1])) {
								// The cast-conversion Long–>long–>int is needed because RGB values
								// are specified bitwise, so they may cause overflows
								list.get(str[1]).colour = new Color((int)(long)Long.valueOf(str[2], 16));
							}
							else
								throw new Exception("Attempt to set colour for unknown brick \"" + str[1] + "\"!");
							break;
						case "add":
							if (list.containsKey(str[1]))
								list.get(str[1]).occupies.add(new Point(Integer.valueOf(str[2]), Integer.valueOf(str[3])));
							else
								throw new Exception("Attempt to add location for unknown brick \"" + str[1] + "\"!");
							break;
						default:
							throw new Exception("Unknown command \"" + str[0] + "\"!");
					}
				}
			}
			catch (Exception e) {
				System.out.println("Unable to read brickset file: " + e);
			}
			descrs = new BrickDescription[list.size()];
			int i = 0;
			for (MutableBrickDescription descr : list.values()) {
				descrs[i] = descr.create(i);
				i++;
			}
		}
	}
	public static class BrickDescription {
		private final Point[] occupies;
		public final Color colour;
		public final int id;
		private BrickDescription(int i, Color c, Point ... p) {
			id = i;
			occupies = p;
			colour = c;
		}
		public Point[] occupies(int rot) {
			Point[] result = new Point[occupies.length];
			switch (rot) {
				case 0: // None
					return occupies;
				case 2: // Double
					for (int i = 0; i < result.length; i++)
						result[i] = new Point(-occupies[i].x, -occupies[i].y);
					break;
				case 1: // Clockwise
					for (int i = 0; i < result.length; i++)
						result[i] = new Point(-occupies[i].y, occupies[i].x);
					break;
				case 3: // Counterclockwise
					for (int i = 0; i < result.length; i++)
						result[i] = new Point(occupies[i].y, -occupies[i].x);
					break;
				default:
					throw new IllegalArgumentException("Invalid rotation index: " + rot);
			}
			return result;
		}
		public Dimension extent(int rot) {
			int minW = Integer.MAX_VALUE;
			int minH = Integer.MAX_VALUE;
			int maxW = Integer.MIN_VALUE;
			int maxH = Integer.MIN_VALUE;
			for (Point p : occupies(rot)) {
				minW = Math.min(minW, p.x);
				maxW = Math.max(maxW, p.x);
				minH = Math.min(minH, p.y);
				maxH = Math.max(maxH, p.y);
			}
			return new Dimension(1 + maxW - minW, 1 + maxH - minH);
		}
		public int size() {
			return occupies.length;
		}
	}
	public static class Brick {
		public final BrickDescription descr;
		public int rotation;
		/* Rotation:
		 * 0 – None
		 * 1 – Clockwise
		 * 2 – Double
		 * 3 – Counterclockwise
		 */
		public Brick(BrickDescription d, int rot) {
			descr = d;
			rotation = rot;
		}
		public Brick(BrickDescription d) {
			this(d, (int)(4 * Math.random()));
		}
		public Point[] occupies() {
			return descr.occupies(rotation);
		}
	}
	
	public int tilesize() {
		int w = display.getWidth();
		int h = display.getHeight();
		return Math.min(w * 2 / (3 * occupied.length), h / occupied[0].length);
	}	
	public Rectangle rect() {
		int w = display.getWidth();
		int h = display.getHeight();
		int size = tilesize();
		return new Rectangle((w / 3) - (size * occupied.length / 2), (h / 2) - (size * occupied[0].length / 2),
				size * occupied.length, size * occupied[0].length);
	}
	public Point tileAt() {
		if (mousePos == null)
			return null;
		Rectangle rect = rect();
		int size = tilesize();
		return new Point((mousePos.x - rect.x) / size, (mousePos.y - rect.y) / size);
	}
	public Point[] tilesAt() {
		if (mousePos == null)
			return null;
		Point center = tileAt();
		Point[] locs = waiting[selectedBrick].occupies();
		Point[] result = new Point[locs.length];
		for (int i = 0; i < locs.length; i++)
			result[i] = new Point(locs[i].x + center.x, locs[i].y + center.y);
		return result;
	}
	public boolean mayPlace() {
		if (selectedBrick == -1 || mousePos == null)
			return false;
		for (Point p : tilesAt()) {
			if (!occupied(p.x, p.y, false))
				return false;
		}
		return true;
	}
	public boolean occupied(int x, int y, boolean def) {
		try {
			return occupied[x][y];
		}
		catch (IndexOutOfBoundsException e) {
			return def;
		}
	}
	
	public synchronized void draw() {
		
		int w = display.getWidth();
		int h = display.getHeight();
		int whm = Math.max(w, h);
		
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		
		for (int i = 0; i < whm * 2; i++) {
			int c = 255 * i / (whm * 2);
			g.setColor(new Color(c, c, c));
			if (w > h)
				g.drawLine(i, 0, 0, i * h / w);
			else
				g.drawLine(i * w / h, 0, 0, i);
		}
		
		int size = tilesize();
		Rectangle rect = rect();
		{
			double imgR = (double)image.getWidth() / image.getHeight();
			double rectR = (double)rect.width / rect.height;
			int x, y;
			if (imgR > rectR) {
				y = rect.y;
				x = rect.x + rect.width / 2 - rect.width / 2;
			}
			else {
				x = rect.x;
				y = rect.y + rect.height / 2 - rect.height / 2;
			}
			g.setClip(rect);
			g.drawImage(image, x, y, rect.width, rect.height, null);
			g.setClip(null);
		}
		for (int i = 0; i < occupied.length; i++)
			for (int j = 0; j < occupied[i].length; j++) 
				if (occupied[i][j]) {
					g.setColor(new Color(0xCCCCCC));
					g.fillRect(rect.x + i * size, rect.y + j * size, size, size);
					g.setColor(new Color(0xAAAAAA));
					g.drawRect(rect.x + i * size + 1, rect.y + j * size + 1, size - 3, size - 3);
				}
		
		int compH = h / (waiting.length + 2);
		g.setColor(new Color(0x1F000000, true));
		g.fillRect(w * 2 / 3, 0, w / 3, h);
		trashRect = new Rectangle(w * 2 / 3, h - compH, w / 3 - 1, compH * 2 / 3);
		g.setColor(new Color(0x111111));
		g.fill(trashRect);
		g.setColor(new Color(0xCCCCCC));
		g.draw(trashRect);
		g.setFont(new Font(Font.SERIF, Font.BOLD, compH / 2));
		String str = trash > 0 ? ("+" + trash) : "–";
		Rectangle b = g.getFont().getStringBounds(str, g.getFontRenderContext()).getBounds();
		g.drawString(str, trashRect.x + trashRect.width / 2 - b.width / 2,
				trashRect.y + trashRect.height / 2 + b.height / 3);
		g.setFont(new Font(Font.SERIF, Font.BOLD, compH / 4));
		str = "" + points;
		b = g.getFont().getStringBounds(str, g.getFontRenderContext()).getBounds();
		g.setColor(new Color(0x222222));
		g.drawString(str, trashRect.x + trashRect.width / 2 - b.width / 2,
				h - b.height / 3);
		if (trash <= 0)
			trashRect = null;
		ArrayList<Integer> indicesToDraw = new ArrayList<>();
		for (int i = 0; i < waiting.length; i++)
			if (waiting[i] != null && (i != highlightBrick && (i != selectedBrick || mousePos == null)))
				indicesToDraw.add(i);
		if (selectedBrick != -1 && mousePos != null)
			indicesToDraw.add(selectedBrick);
		else if (highlightBrick != -1)
			indicesToDraw.add(highlightBrick);
		for (int i : indicesToDraw) {
			if (waiting[i] != null) {
				if (i == selectedBrick && mousePos != null) {
					Point[] locations = waiting[i].occupies();
					g.setColor(mayPlace() ? waiting[i].descr.colour : new Color(0x3F000000, true));
					for (Point p : tilesAt()) {
						g.fillRect(rect.x + p.x * size + 3, rect.y + p.y * size + 3, size - 6, size - 6);
					}
					for (Point p : locations) {
						g.setColor(waiting[i].descr.colour.brighter());
						g.fillRect(mousePos.x + p.x * size - size / 2, mousePos.y + p.y * size - size / 2, size, size);
						g.setColor(waiting[i].descr.colour.darker().darker());
						g.drawRect(mousePos.x + p.x * size + 1 - size / 2, mousePos.y + p.y * size + 1 - size / 2,
								size - 3, size - 3);
					}
					waitingRects[i] = null;
				}
				else {
					Area area = new Area();
					int off;
					if (i % 2 == 0) {
						int m = 0;
						for (Point p : waiting[i].occupies())
							m = Math.min(m, p.x);
						off = w * 2 / 3 - m * size + size / 2;
					}
					else {
						int m = 0;
						for (Point p : waiting[i].occupies())
							m = Math.max(m, p.x);
						off = w - m * size - size * 3 / 2;
					}
					for (Point p : waiting[i].occupies()) {
						g.setColor(waiting[i].descr.colour);
						if (i == highlightBrick)
							g.setColor(g.getColor().brighter());
						g.fillRect(off + p.x * size, i * compH + compH * 3 / 2 + p.y * size, size, size);
						g.setColor(waiting[i].descr.colour.darker());
						if (i == highlightBrick)
							g.setColor(g.getColor().darker());
						g.drawRect(off + p.x * size + 1, i * compH + compH * 3 / 2 + p.y * size + 1,
								size - 3, size - 3);
						area.add(new Area(new Rectangle2D.Double(
								off + p.x * size, i * compH + compH * 3 / 2 + p.y * size, size, size)));
					}
					waitingRects[i] = area;
				}
			}
			else
				waitingRects[i] = null;
		}
		
		if (menu != null) {
			menu.draw(g, new Rectangle(rect.x + size / 2, rect.y + size / 2,
					size * (occupied.length - 1), size * (occupied[0].length - 1)));
		}
		else if (message != null) {
			Rectangle msgRect = new Rectangle(rect.x + size / 2, rect.y + size / 2,
					size * (occupied.length - 1), size * (occupied[0].length - 1));
			g.setColor(new Color(0x7F000000, true));
			g.fill(msgRect);
			g.setColor(Color.WHITE);
			g.draw(msgRect);
			g.setFont(new Font(Font.SERIF, Font.BOLD, size / 2));
			b = g.getFont().getStringBounds(message.title, g.getFontRenderContext()).getBounds();
			g.drawString(message.title, msgRect.x + msgRect.width / 2 - b.width / 2, msgRect.y + size);
			String[] strs = message.text().split("\n");
			int spacing = Math.min(size, size * (occupied[0].length - 2) / (strs.length + 2));
			g.setFont(new Font(Font.SERIF, Font.PLAIN, size / 3));
			int i = 0;
			for (String s : strs) {
				b = g.getFont().getStringBounds(s, g.getFontRenderContext()).getBounds();
				g.drawString(s, msgRect.x + msgRect.width / 2 - b.width / 2, msgRect.y + size * 2 + spacing * i);
				i++;
			}
		}
		
		display.setIcon(new ImageIcon(img));
		
	}
	
	public void checkGameOver() {
		if (trash > 0)
			return;
		// check if we can place any brick anywhere
		for (Brick b : selectedBrick == -1 ? waiting : new Brick[] { waiting[selectedBrick] }) {
			for (int r = 0; r < 4; r++) {
				Point[] pp = b.descr.occupies(r);
				for (int i = 0; i < occupied.length; i++)
					for (int j = 0; j < occupied[i].length; j++) {
						boolean ok = true;
						for (Point p : pp)
							if (!occupied(p.x + i, p.y + j, false)) {
								ok = false;
								break;
							}
						if (ok)
							return;
					}
			}
		}
		int hp = -1;
		for (int i = highscores.length - 1; i >= 0; i--) {
			if (points > highscores[i].points)
				hp = i;
			else
				break;
		}
		final int highscorePlace = hp;
		String scores = "~~~~ Highscores ~~~~";
		for (int i = 0; i < highscores.length; i++) {
			int j = highscorePlace < 0 ? i : i > highscorePlace ? i - 1 : i;
			scores += "\n" + (i + 1) + ") " + (i == highscorePlace ? "_" : highscores[j].name) + ": " +
					(i == highscorePlace ? points : highscores[j].points);
		}
		if (highscorePlace < 0) {
			message = new Message("Game Over", "The game is over.\nYou gained " + points + 
					" points.\nYou have not earned a highscore entry.\n\n" + scores +
					"\n\nPress any key or click to start a new game", false) {
				public void function(InputEvent e) {
					int mask = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
					if ((e.getModifiersEx() | mask) != mask)
						return;
					if (e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ESCAPE)
						System.exit(0);
					message = null;
					menu = new Menu(occupied.length, occupied[0].length, brickset.name);
				}
			};
		}
		else {
			message = new Message("Game Over", "The game is over.\nYou gained " + points + 
					" points.\nYou have earned the " + (highscorePlace + 1) +
					(highscorePlace == 0 ? "st" : highscorePlace == 1 ? "nd" : highscorePlace == 2 ? "rd" : "th") +
					" place in the highscore list!\nCongratulations!\n\n" + scores +
					"\n\nPlease enter your name, then press enter", false) {
				private String input = "";
				public void function(InputEvent e) {
					if (input != null) {
						if (e instanceof KeyEvent) {
							KeyEvent k = (KeyEvent)e;
							String scores = "~~~~ Highscores ~~~~";
							boolean create = true;
							if (k.getKeyCode() == KeyEvent.VK_ENTER) {
								try {
									PrintWriter write = new PrintWriter(new File("data/highscores"));
									for (int i = highscores.length - 1; i > highscorePlace; i--) {
										highscores[i] = highscores[i - 1];
									}
									highscores[highscorePlace] = new Highscore(input, points);
									for (int i = 0; i < highscores.length; i++) {
										write.println(highscores[i].name);
										write.println(highscores[i].points);
									}
									write.close();
								}
								catch (Exception ex) {
									System.out.println("Unable to save game highscores: " + ex);
								}
								for (int i = 0; i < highscores.length; i++)
									scores += "\n" + (i + 1) + ") " + highscores[i].name + ": " + highscores[i].points;
								input = null;
								create = false;
							}
							else if (k.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
								if (!input.isEmpty())
									input = input.substring(0, input.length() - 1);
							}
							else if (k.getKeyChar() != KeyEvent.CHAR_UNDEFINED &&
									Character.isLetterOrDigit(k.getKeyChar())) {
								input += k.getKeyChar();
							}
							if (create) {
								for (int i = 0; i < highscores.length; i++) {
									int j = highscorePlace < 0 ? i : i > highscorePlace ? i - 1 : i;
									scores += "\n" + (i + 1) + ") " +
											(i == highscorePlace ? input + "_" : highscores[j].name) + ": " +
											(i == highscorePlace ? points : highscores[j].points);
								}
							}
							text = "The game is over.\nYou gained " + points + 
									" points.\nYou have earned the " + (highscorePlace + 1) +
									(highscorePlace == 0 ? "st" : highscorePlace == 1 ? "nd" : highscorePlace == 2 ?
									"rd" : "th") + " place in the highscore list!\nCongratulations!\n\n" + scores +
									"\n\n" + (input == null ? "Press any key or click to start a new game" :
											"Please enter your name, then press enter");
						}
					}
					else {
						int mask = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK |
								InputEvent.BUTTON3_DOWN_MASK;
						if ((e.getModifiersEx() | mask) != mask)
							return;
						if (e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ESCAPE)
							System.exit(0);
						message = null;
						menu = new Menu(occupied.length, occupied[0].length, brickset.name);
					}
				}
			};
		}
		draw();
	}
	
	public void checkComplete() {
		boolean complete = true;
		for (boolean[] bb : occupied) {
			for (boolean b : bb) {
				if (b) {
					complete = false;
					break;
				}
			}
			if (!complete)
				break;
		}
		if (complete) {
			message = new Message("Congratulations", "Press any key or click to continue", true) {
				public void function(InputEvent e) {
					int mask = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
					if ((e.getModifiersEx() | mask) != mask)
						return;
					reset(false);
					message = null;
				}
			};
			draw();
		}
		else {
			checkGameOver();
		}
	}
	
	public void save() {
		try {
			PrintWriter write = new PrintWriter(new File("data/save"));
			write.println(occupied.length + " " + occupied[0].length + " " + waiting.length + " " + points +
					" " + trash + " " + selectedBrick);
			write.println(currentImageName);
			write.println(lastImageName);
			write.println(brickset.name);
			for (int i = 0; i < occupied.length; i++) {
				for (int j = 0; j < occupied[0].length; j++) {
					write.print(occupied[i][j] ? "1" : "0");
				}
				write.println();
			}
			for (int i = 0; i < waiting.length; i++)
				write.println(waiting[i] == null ? "" : waiting[i].descr.id + "," + waiting[i].rotation);
			write.close();
		}
		catch (Exception e) {
			System.out.println("Unable to save game because: " + e);
		}
	}
	
	public boolean load() {
		File f = new File("data/save");
		if (!f.isFile())
			return false;
		try {
			java.util.List <String> lines = Files.readAllLines(f.toPath());
			String[] data = lines.get(0).split(" ");
			occupied = new boolean[Integer.valueOf(data[0])][Integer.valueOf(data[1])];
			waiting = new Brick[Integer.valueOf(data[2])];
			waitingRects = new Shape[waiting.length];
			points = Long.valueOf(data[3]);
			trash = Integer.valueOf(data[4]);
			selectedBrick = Integer.valueOf(data[5]);
			highlightBrick = -1;
			currentImageName = lines.get(1);
			lastImageName = lines.get(2);
			image = ImageIO.read(new File("data/images", currentImageName));
			brickset = new BrickSet(lines.get(3));
			int line = 4;
			for (int i = 0; i < occupied.length; i++) {
				String l = lines.get(line++);
				for (int j = 0; j < occupied[0].length; j++)
					occupied[i][j] = l.charAt(j) == '1';
			}
			for (int i = 0; i < waiting.length; i++) {
				String l = lines.get(line);
				if (l.isEmpty())
					waiting[i] = null;
				else {
					data = lines.get(line).split(",");
					waiting[i] = new Brick(brickset.get(Integer.valueOf(data[0])), Integer.valueOf(data[1]));
				}
				line++;
			}
		}
		catch (Exception e) {
			System.out.println("Unable to load game because: " + e);
			return false;
		}
		f.delete();
		menu = null;
		return true;
	}
	
	
	public void reset(boolean newGame) {
		reset(newGame, occupied == null ? 15 : occupied.length, occupied == null ? 10 : occupied[0].length, 
				5, waiting == null ? 5 : waiting.length, DEFAULT_BRICKSET);
	}
	
	public void reset(boolean newGame, int w, int h, int tr, int wait, String bs) {
		menu = null;
		message = null;
		File[] images = new File("data/images").listFiles();
		int i;
		switch(images.length) {
			case 0:
				System.out.println("ERROR: No images found!");
				System.exit(1);
				return;
			case 1:
				i = 0;
				break;
			case 2:
				i = images[0].getName().equals(currentImageName) ? 1 : 0;
				break;
			default:
				do {
					i = (int)(images.length * Math.random());
				} while (images[i].getName().equals(currentImageName) || images[i].getName().equals(lastImageName));
				break;
		}
		lastImageName = currentImageName;
		currentImageName = images[i].getName();
		try {
			image = ImageIO.read(images[i]);
		}
		catch (Exception e) {
			System.out.println("ERROR: Unable to read image file »" + images[i].getAbsolutePath() + "«: " + e);
			System.exit(2);
			return;
		}
		if (newGame) {
			points = 0;
			trash = tr;
			brickset = new BrickSet(bs);
		}
		else {
			trash++;
		}
		occupied = new boolean[w][h];
		for (int x = 0; x < occupied.length; x++)
			for (int y = 0; y < occupied[x].length; y++)
				occupied[x][y] = true;
		if (newGame)
			waiting = new Brick[wait];
		waitingRects = new Shape[waiting.length];
		for (int x = 0; x < waiting.length; x++)
			if (newGame || waiting[x] == null)
				waiting[x] = new Brick(brickset.random());
		highlightBrick = -1;
		selectedBrick = -1;
	}
	
	public Game() {
		
		frame = new JFrame("Mosaik");
		display = new JLabel();
		
		currentImageName = "";
		lastImageName = "";
		if (!load()) {
			reset(true);
			menu = new Menu(15, 10, DEFAULT_BRICKSET);
		}
		
		display.setPreferredSize(new Dimension(800, 600));
		
		frame.add(display);
		display.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (menu != null) {
					menu.handleKey(e);
				}
				else if (message != null) {
					message.function(e);
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					save();
					menu = new Menu(occupied.length, occupied[0].length, brickset.name);
				}
				draw();
			}
		});
		display.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent m) {
				final boolean modCtrl = m.isControlDown() || m.getButton() > 1;
				if (menu != null) {
					menu.handleMouse(m);
				}
				else if (message != null) {
					message.function(m);
				}
				else if (selectedBrick == -1) {
					if (highlightBrick != -1 && waiting[highlightBrick] != null) {
						selectedBrick = highlightBrick;
						highlightBrick = -1;
						checkGameOver();
					}
				}
				else if (m.isShiftDown()) {
					if (modCtrl) {
						waiting[selectedBrick].rotation += 2;
						waiting[selectedBrick].rotation %= 4;
					}
					else {
						waiting[selectedBrick].rotation += 3;
						waiting[selectedBrick].rotation %= 4;
					}
				}
				else if (modCtrl) {
					waiting[selectedBrick].rotation++;
					waiting[selectedBrick].rotation %= 4;
				}
				else if (trashRect != null && trashRect.contains(m.getPoint())) {
					trash --;
					int p = 1;
					for (int i = 0; i < waiting[selectedBrick].descr.size(); i++) {
						points -= p;
						p++;
					}
					waiting[selectedBrick] = new Brick(brickset.random());
					selectedBrick = -1;
					checkGameOver();
				}
				else if (mayPlace()) {
					int p = 0;
					for (Point c : tilesAt()) {
						occupied[c.x][c.y] = false;
						points += p;
						p++;
					}
					waiting[selectedBrick] = new Brick(brickset.random());
					selectedBrick = -1;
					checkComplete();
				}
				draw();
			}
		});
		display.addMouseWheelListener(new MouseAdapter() {
			public void mouseWheelMoved(MouseWheelEvent w) {
				if (selectedBrick == -1 || message != null || menu != null)
					return;
				waiting[selectedBrick].rotation -= w.getWheelRotation();
				while (waiting[selectedBrick].rotation < 0)
					waiting[selectedBrick].rotation += 4;
				waiting[selectedBrick].rotation %= 4;
				draw();
			}
		});
		display.addMouseMotionListener(new MouseAdapter() {
			public void mouseMoved(MouseEvent m) {
				if (menu != null) {
					if (menu.handleMouse(m))
						draw();
					return;
				}
				if (message != null)
					return;
				mousePos = m.getPoint();
				if (selectedBrick != -1) {
					if (highlightBrick != -1)
						highlightBrick = -1;
					draw();
					return;
				}
				int sel = -1;
				for (int i = 0; i < waitingRects.length; i++)
					if (waitingRects[i] != null && waitingRects[i].contains(m.getPoint())) {
						sel = i;
					}
				if (sel == highlightBrick)
					return;
				highlightBrick = sel;
				draw();
			}
		});
		display.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				draw();
			}
		});
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (menu == null && (message == null || message.allowSaving))
					save();
				System.exit(0);
			}
		});
		display.setFocusable(true);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
	}
	
	public class Menu {
		private int width, height;
		private int selection;
		private int brickset;
		private final boolean load;
		private final String[] bricksets;
		
		private Rectangle loadRect, startRect, quitRect, bricksetRect, bricksetLeftRect, bricksetRightRect,
				widthRect, heightRect, widthLeftRect, widthRightRect, heightLeftRect, heightRightRect;
		
		public static final int MIN_WIDTH = 5;
		public static final int MIN_HEIGHT = 5;
		/* Layout:
		 * 0|- [Continue]
		 * 1|0 Start!
		 * 2|1 Width
		 * 3|2 Height
		 * 4|3 Brickset
		 * 5|4 Quit
		 */
		public Menu(int w, int h, String bricks) {
			width = w;
			height = h;
			load = new File("data/save").isFile();
			selection = 0;
			bricksets = new File("data/bricks").list();
			brickset = 0;
			for (int i = 0; i < bricksets.length; i++) {
				if (bricksets[i].equals(bricks)) {
					brickset = i;
					break;
				}
			}
		}
		public boolean handleMouse(MouseEvent m) {
			boolean click = m.getClickCount() > 0;
			int sel = selection;
			if (loadRect != null && loadRect.contains(m.getPoint())) {
				selection = 0;
				if (click) load();
			}
			else if (startRect != null && startRect.contains(m.getPoint())) {
				selection = load ? 1 : 0;
				if (click) start();
			}
			else if (quitRect != null && quitRect.contains(m.getPoint())) {
				selection = load ? 5 : 4;
				if (click) System.exit(0);
			}
			else if (bricksetLeftRect != null && bricksetLeftRect.contains(m.getPoint())) {
				selection = load ? 4 : 3;
				if (click) prevBrickset();
			}
			else if (bricksetRightRect != null && bricksetRightRect.contains(m.getPoint())) {
				selection = load ? 4 : 3;
				if (click) nextBrickset();
			}
			else if (bricksetRect != null && bricksetRect.contains(m.getPoint())) {
				selection = load ? 4 : 3;
			}
			else if (widthLeftRect != null && widthLeftRect.contains(m.getPoint())) {
				selection = load ? 2 : 1;
				if (click) widthLess();
			}
			else if (widthRightRect != null && widthRightRect.contains(m.getPoint())) {
				selection = load ? 2 : 1;
				if (click) width++;
			}
			else if (widthRect != null && widthRect.contains(m.getPoint())) {
				selection = load ? 2 : 1;
			}
			else if (heightLeftRect != null && heightLeftRect.contains(m.getPoint())) {
				selection = load ? 3 : 2;
				if (click) heightLess();
			}
			else if (heightRightRect != null && heightRightRect.contains(m.getPoint())) {
				selection = load ? 3 : 2;
				if (click) height++;
			}
			else if (heightRect != null && heightRect.contains(m.getPoint())) {
				selection = load ? 3 : 2;
			}
			return sel != selection;
		}
		public void handleKey(KeyEvent e) {
			switch (e.getKeyCode()) {
				case KeyEvent.VK_UP:
					selection += load ? 5 : 4;
					selection %= load ? 6 : 5;
					break;
				case KeyEvent.VK_DOWN:
					selection++;
					selection %= load ? 6 : 5;
					break;
				case KeyEvent.VK_LEFT:
					switch (selection - (load ? 1 : 0)) {
						case 1:
							widthLess();
							break;
						case 2:
							heightLess();
							break;
						case 3:
							prevBrickset();
							break;
						default:
							break;
					}
					break;
				case KeyEvent.VK_RIGHT:
					switch (selection - (load ? 1 : 0)) {
						case 1:
							width++;
							break;
						case 2:
							height++;
							break;
						case 3:
							nextBrickset();
							break;
						default:
							break;
					}
					break;
				case KeyEvent.VK_ESCAPE:
					System.exit(0);
					break;
				case KeyEvent.VK_ENTER:
					switch (selection - (load ? 1 : 0)) {
						case 0:
							start();
							break;
						case 4:
							System.exit(0);
							break;
						case -1:
							load();
							break;
						default:
							break;
					}
					break;
				default:
					break;
			}
		}
		private void nextBrickset() {
			brickset++;
			brickset %= bricksets.length;
		}
		private void prevBrickset() {
			brickset += bricksets.length - 1;
			brickset %= bricksets.length;
		}
		private void widthLess() {
			if (width > MIN_WIDTH)
				width--;
		}
		private void heightLess() {
			if (height > MIN_HEIGHT)
				height--;
		}
		private void start() {
			int r = (int)Math.round(width * height / 30);
			reset(true, width, height, r, r, bricksets[brickset]);
		}
		public void draw(Graphics2D g, Rectangle rect) {
			loadRect = startRect = quitRect = bricksetRect = bricksetLeftRect = bricksetRightRect = widthRect =
					heightRect = widthLeftRect = widthRightRect = heightLeftRect = heightRightRect = null;
			
			g.setColor(new Color(0x7F000000, true));
			g.fill(rect);
			g.setColor(Color.WHITE);
			g.draw(rect);
			final int size = tilesize();
			g.setFont(new Font(Font.SERIF, Font.BOLD, size / 2));
			String str = "New Game";
			Rectangle b = g.getFont().getStringBounds(str, g.getFontRenderContext()).getBounds();
			g.drawString(str, rect.x + rect.width / 2 - b.width / 2, rect.y + size);
			ArrayList<String> text = new ArrayList<>();
			if (load) text.add("Continue saved game");
			text.add("Start!");
			text.add("Width: " + (width > MIN_WIDTH ? "« " : "") + width + " »");
			text.add("Height: " + (height > MIN_HEIGHT ? "« " : "") + height + " »");
			text.add("Brickset: " + (bricksets.length > 1 ? "« " : "") + bricksets[brickset] +
					(bricksets.length > 1 ? " »" : ""));
			text.add("Quit");
			for (int i = 0; i < text.size(); i++) {
				g.setFont(new Font(Font.SERIF, i == selection ? Font.BOLD : Font.PLAIN, size / 3));
				b = g.getFont().getStringBounds(text.get(i), g.getFontRenderContext()).getBounds();
				g.drawString(text.get(i), rect.x + rect.width / 2 - b.width / 2, rect.y + size * (i + 2));
				Rectangle r = new Rectangle(rect.x, rect.y + size * (i + 1) + size / 2, rect.width, size);
				switch (i) {
					case 0:
						if (load) loadRect = r; else startRect = r; break;
					case 1:
						if (load) startRect = r; else widthRect = r; break;
					case 2:
						if (load) widthRect = r; else heightRect = r; break;
					case 3:
						if (load) heightRect = r; else bricksetRect = r; break;
					case 4:
						if (load) bricksetRect = r; else quitRect = r; break;
					case 5:
						quitRect = r; break;
					default:
						break;
				}
			}
			widthLeftRect = new Rectangle(widthRect.x, widthRect.y, widthRect.width / 3, widthRect.height);
			widthRightRect = new Rectangle(widthRect.x + widthRect.width * 2 / 3,
					widthRect.y, widthRect.width / 3, widthRect.height);
			heightLeftRect = new Rectangle(heightRect.x, heightRect.y, heightRect.width / 3, heightRect.height);
			heightRightRect = new Rectangle(heightRect.x + heightRect.width * 2 / 3,
					heightRect.y, heightRect.width / 3, heightRect.height);
			bricksetLeftRect = new Rectangle(bricksetRect.x, bricksetRect.y, bricksetRect.width / 3, bricksetRect.height);
			bricksetRightRect = new Rectangle(bricksetRect.x + bricksetRect.width * 2 / 3,
					bricksetRect.y, bricksetRect.width / 3, bricksetRect.height);
		}
	}
	
	public static void main(String[] args) {
		java.util.List<String> lines;
		try {
			lines = Files.readAllLines(new File("data/highscores").toPath());
		}
		catch (Exception e) {
			System.out.println("Unable to read highscores: " + e);
			lines = new ArrayList<>();
		}
		for (int i = 0; i < highscores.length; i++) {
			String n;
			long p;
			try {
				n = lines.get(2 * i);
				p = Long.valueOf(lines.get(2 * i + 1));
			}
			catch (Exception e) {
				n = "Nobody";
				p = 0;
			}
			highscores[i] = new Highscore(n, p);
		}
		new Game();
	}
	
}
