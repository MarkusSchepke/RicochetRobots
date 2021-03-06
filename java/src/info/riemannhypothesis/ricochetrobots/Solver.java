/**
 * 
 */
package info.riemannhypothesis.ricochetrobots;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.swing.JFrame;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * @author MarkusSchepke
 * 
 */
public class Solver {

    public final static int     DEFAULT_MAX_MOVES  = 20;
    public final static double  DEFAULT_MAX_TIME   = 2 * 60;
    public static final int     DEFAULT_FIELD_SIZE = 30;
    private static final long   DEFAULT_WAIT       = 1000;

    private final Board         board;
    private final Point         target;
    private final int           targetRobotIndex;
    private final int           numberRobots;
    private final int           moves;

    private final List<Point[]> solution;

    public Solver(Board board, Robot[] robots, Point target, int targetRobot) {
        this(board, robots, target, targetRobot, DEFAULT_MAX_MOVES,
                DEFAULT_MAX_TIME);
    }

    public Solver(Board board, Robot[] robots, Point target, int targetRobot,
            int maxMoves) {
        this(board, robots, target, targetRobot, maxMoves, DEFAULT_MAX_TIME);
    }

    public Solver(Board board, Robot[] robots, Point target, int targetRobot,
            int maxMoves, double maxTime) {
        this.board = board;
        this.target = target;

        this.numberRobots = robots.length;
        Point[] initial = new Point[numberRobots];
        int counter = 0;
        for (Robot robot : robots) {
            initial[counter] = robot.getPosition();
            counter++;
        }
        this.targetRobotIndex = targetRobot;

        this.solution = solveBruteForce(initial, maxMoves, maxTime);
        this.moves = this.solution == null ? -1 : this.solution.size() - 1;
    }

    /**
     * Finds a solution by trying out all possible moves.
     * 
     * @param initial
     *            the initial configuration of the robots
     * @param maxMoves
     *            the maximal number of moves to be searched
     * @return a List of configurations representing the moves of the solution
     *         found, or null if none could be found withing {@link maxMoves}.
     */
    private List<Point[]> solveBruteForce(Point[] initial, int maxMoves,
            double maxTime) {

        long start = System.nanoTime();
        long end = (long) (start + maxTime * 1e9);

        Queue<Node> queue = new LinkedList<Node>();
        queue.add(new Node(initial, 0, null));

        Node current;
        boolean solved = false;

        while ((current = queue.poll()) != null) {

            if (current.configuration[targetRobotIndex].equals(target)) {
                solved = true;
                break;
            }

            Point[] configWithoutTarget = new Point[numberRobots - 1];
            for (int i = 0, c = 0; i < numberRobots; i++) {
                if (i == targetRobotIndex) {
                    continue;
                }
                configWithoutTarget[c++] = current.configuration[i];
            }

            HashMap<Point, MoveNode> endMoves = endMoves(configWithoutTarget);
            MoveNode moveNode = null;
            int moves = -1;

            for (Point dest : board.reachable(
                    current.configuration[targetRobotIndex],
                    current.configuration)) {
                final MoveNode temp = endMoves.get(dest);
                if (temp != null && (moveNode == null || temp.moves < moves)) {
                    moveNode = temp;
                    moves = temp.moves;
                }
            }

            if (moveNode != null) {
                while (moveNode != null) {
                    Point[] newConfig = Arrays.copyOf(current.configuration,
                            numberRobots);
                    newConfig[targetRobotIndex] = moveNode.point;
                    current = new Node(newConfig, current.moves + 1, current);
                    queue.add(current);
                    moveNode = moveNode.next;
                }
                solved = true;
                break;
            }

            if (System.nanoTime() >= end) {
                break;
            }

            if (current.moves >= maxMoves) {
                continue;
            }

            for (int i = 0; i < numberRobots; i++) {
                Point position = current.configuration[i];
                for (Point dest : board.reachable(position,
                        current.configuration)) {
                    if (!dest.equals(position)) {
                        Point[] newConfig = Arrays.copyOf(
                                current.configuration, numberRobots);
                        newConfig[i] = dest;
                        if (current.previous == null
                                || !Arrays.equals(newConfig,
                                        current.previous.configuration)) {
                            queue.add(new Node(newConfig, current.moves + 1,
                                    current));
                        }
                    }
                }
            }
        }

        if (!solved) {
            return null;
        }

        assert current.configuration[targetRobotIndex].equals(target);

        LinkedList<Point[]> result = new LinkedList<Point[]>();
        while (current != null) {
            assert current.previous == null
                    || legalMove(current.previous.configuration,
                            current.configuration);
            result.addFirst(current.configuration);
            current = current.previous;
        }
        return result;
    }

    private boolean legalMove(Point[] prev, Point[] next) {
        if (prev.length != next.length) {
            System.out.println("Lengths don't match!");
            return false;
        }
        int countDiff = 0;
        for (int i = 0; i < prev.length; i++) {
            if (prev[i].equals(next[i])) {
                continue;
            }
            if (++countDiff > 1) {
                System.out.println("More than one position differs!");
                return false;
            }
            if (!board.reachable(prev[i], prev).contains(next[i])) {
                System.out.println("Point " + next[i] + " not reachable from "
                        + prev[i] + "!");
                return false;
            }
        }
        if (countDiff != 1) {
            System.out.println("No points differed!");
            return false;
        }
        return true;
    }

    private HashMap<Point, MoveNode> endMoves(Point[] configuration) {
        HashMap<Point, MoveNode> endMoves = new HashMap<Point, MoveNode>();
        for (Point point : configuration) {
            if (point.equals(target)) {
                return endMoves;
            }
        }
        MoveNode node = new MoveNode(target, 0, null);
        endMoves.put(target, node);
        for (int dir : Board.DIRECTIONS) {
            if (board.isConnected(target, dir, configuration)) {
                addToMap(endMoves, configuration, target.move(dir), 1, dir,
                        node);
            }
        }
        return endMoves;
    }

    private void addToMap(HashMap<Point, MoveNode> map, Point[] configuration,
            Point point, int moves, int direction, MoveNode next) {
        MoveNode existing = map.get(point);
        if (existing != null && existing.moves <= moves) {
            // been there, done that!
            return;
        }
        MoveNode node = new MoveNode(point, moves, next);
        map.put(point, node);
        if (board.isConnected(point, direction, configuration)) {
            addToMap(map, configuration, point.move(direction), moves,
                    direction, next);
        }
        for (int perp : Board.PERP[direction]) {
            if (!board.isConnected(point, perp, configuration)
                    && board.isConnected(point, Board.OPPOSITE[perp],
                            configuration)) {
                addToMap(map, configuration, point.move(Board.OPPOSITE[perp]),
                        moves + 1, Board.OPPOSITE[perp], node);
            }
        }
    }

    public int moves() {
        return moves;
    }

    public List<Point[]> solution() {
        return solution;
    }

    private class Node {
        private final Point[] configuration;
        private final int     moves;
        private final Node    previous;

        private Node(Point[] configuration, int moves, Node previous) {
            this.configuration = configuration;
            this.moves = moves;
            this.previous = previous;
        }
    }

    protected class MoveNode implements Comparable<MoveNode> {
        private final Point    point;
        protected final int    moves;
        private final MoveNode next;

        private MoveNode(Point point, int moves, MoveNode next) {
            this.point = point;
            this.moves = moves;
            this.next = next;
        }

        @Override
        public int compareTo(MoveNode that) {
            return this.moves - that.moves;
        }
    }

    /**
     * Constructs a board from a file given through a command line argument and
     * tries to solve it for a random configuration of robots.
     * 
     * @param args
     * @throws IOException
     * @throws ParseException 
     */
    public static void main(String[] args) throws IOException, ParseException {

		Options options = new Options();

		options.addOption(Option.builder("b").longOpt("board").required(true)
				.hasArg().desc("board file path").build());
		options.addOption(Option.builder("m").longOpt("max-moves").hasArg()
				.type(Integer.class).desc("maximal number of moves").build());
		options.addOption(Option.builder("t").longOpt("max-time").hasArg()
				.type(Double.class).desc("maximal time").build());
		options.addOption(Option.builder("g").longOpt("graphical")
				.desc("graphical representation").build());
		options.addOption(Option.builder("w").longOpt("width").hasArg()
				.type(Integer.class).desc("field width").build());
		options.addOption(Option.builder("h").longOpt("height").hasArg()
				.type(Integer.class).desc("field height").build());
		options.addOption(Option.builder("r").longOpt("repaint").hasArg()
				.type(Long.class).desc("wait to repaint").build());
		options.addOption(Option.builder("i").longOpt("image").hasArg()
				.desc("image dir path").build());

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("Solver", options);
			System.exit(1);
			return;
		}

		String boardFile = cmd.getOptionValue("board");
		int maxMoves = Integer.parseInt(
				cmd.getOptionValue("max-moves",
						Integer.toString(DEFAULT_MAX_MOVES, 10)), 10);
		double maxTime = Double.parseDouble(cmd.getOptionValue("max-time",
				Double.toString(DEFAULT_MAX_TIME)));
		boolean graphical = cmd.hasOption("graphical");
		int fieldWidth = Integer.parseInt(
				cmd.getOptionValue("width",
						Integer.toString(DEFAULT_FIELD_SIZE, 10)), 10);
		int fieldHeight = Integer.parseInt(
				cmd.getOptionValue("height", Integer.toString(fieldWidth, 10)),
				10);
		long waitToRepaint = Long.parseLong(
				cmd.getOptionValue("wait-repaint",
						Long.toString(DEFAULT_WAIT, 10)), 10);
		String imageDir = cmd.getOptionValue("image", null);
		boolean saveImage = imageDir != null;

		Board board;
        try {
            board = new Board(new FileInputStream(new File(boardFile)));
        } catch (IOException e) {
            System.out.println("*** Error: File " + boardFile + " not found ***");
            return;
        }

        Robot[] robots = null;
        int targetRobot = 0;
        
        try {
        	robots = Robot.robotSet(new FileInputStream(new File(boardFile)));
        	targetRobot = 0;
        } catch (IOException e) {
        	System.out.println(e);
        	System.out.println(e.getStackTrace());
        	robots = null;
        }
        
        if (robots == null) {
        	System.out.println("File contains no information on robots, use random.");
	        robots = Robot.robotSet(board.getWidth(), board.getHeight(),
	                new String[] { "Red", "Yellow", "Green", "Blue" }, new Color[] {
	                        Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE });
	        targetRobot = (int) (Math.random() * robots.length);
        }

        Point target = (Point) board.getTargets().toArray()[(int) (Math
                .random() * board.getTargets().size())];
        HashSet<Point> targetSet = new HashSet<Point>();
        targetSet.add(target);

        if (!graphical) {
            System.out.println("Searching solution for robot "
                    + robots[targetRobot].getLabel() + " on board:");
            System.out.println(board.toString(robots, targetSet));
            System.out.println("Maximal number of moves: " + maxMoves
                    + "; maximal execution time: " + maxTime + " s.");
            System.out.println();
        }

        long start = System.nanoTime();
		Solver solver = new Solver(board, robots, target, targetRobot,
				maxMoves, maxTime);
        long end = System.nanoTime();
        double seconds = (end - start) / 1000000000.0;

        if (solver.solution() == null) {
            System.out.println("No solution found with " + maxMoves
                    + " moves or aborted after " + seconds + " seconds.");
            return;
        }

        System.out.println("Found solution in " + seconds + " seconds with "
                + solver.moves() + " moves.");

        if (graphical) {
        	File dir = null;
        	if (saveImage) {
        		dir = new File(imageDir);
        		dir.mkdirs();
        	}

            JFrame window = new JFrame("Move " + robots[targetRobot].getLabel()
                    + " to target with " + solver.moves() + " moves.");

            BoardPanel content = new BoardPanel(board, fieldWidth, fieldHeight);
            content.setTargets(targetSet);

            window.setContentPane(content);
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setLocation(0, 0);
            window.setSize(content.totalWidth, content.totalHeight + 25);
            window.setVisible(true);

            while (true) {

                content.setRobots(null);
                content.repaint();

                try {
                    Thread.sleep(waitToRepaint);
                } catch (InterruptedException e) {
                }

                content.setRobots(robots);
                content.repaint();

                int count = 0;

                for (Point[] config : solver.solution()) {
                    // update robot positions
                    for (int i = 0; i < config.length; i++) {
                        robots[i].setPosition(config[i]);
                    }

                    try {
                        Thread.sleep(waitToRepaint);
                    } catch (InterruptedException e) {
                    }

                    content.repaint();

					if (saveImage)
						content.save(
								new File(dir, String.format("image%03d.png",
										count++)), "png");
                }

                saveImage = false;
            }

        } else {

            int counter = 0;
            for (Point[] config : solver.solution()) {
                if (counter++ == 0) {
                    continue;
                }
                // update robot positions
                for (int i = 0; i < config.length; i++) {
                    robots[i].setPosition(config[i]);
                }
                System.out.println(board.toString(robots, targetSet));
                System.out.println();
            }
        }
    }
}
