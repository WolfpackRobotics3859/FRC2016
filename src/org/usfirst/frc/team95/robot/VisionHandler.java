package org.usfirst.frc.team95.robot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import edu.wpi.first.wpilibj.networktables.NetworkTable;
import edu.wpi.first.wpilibj.tables.ITable;
import edu.wpi.first.wpilibj.tables.ITableListener;

public class VisionHandler {
	double x, y; // These represent the x and y, in pixels, of the
		// center of the goal.
	
	public double getX() { // Getters!
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	private final static String[] CLEAR_TMP_CMD =
		{"/bin/rm", "-rf", "/tmp/*"};
	
	private final static String[] GRIP_CMD =
		{"/usr/local/frc/JRE/bin/java", "-jar", "/home/lvuser/grip.jar", 
				"/home/lvuser/project.grip"};
	
	static VisionHandler instance; // This is the only instance of VisionHandler that should be used
	
	NetworkTable GRIPTable; // This represents GRIPs published reports
	Comparator<Line> sort = new Comparator<Line>() { // This sorts Line objects based on length
		public int compare(Line l1, Line l2) { // Hooray for anonymous classes.
			if (l1.length == l2.length)
				return 0;
			return l1.length > l2.length ? 1 : -1;
		}
	};
	
	ITableListener updater = new ITableListener() { // This updates the x and y whenever the table updates.
		@Override
		public void valueChanged(ITable table, String key, Object value, boolean newP) {
			if (key.equals("x")) // So that we don't get spammed by updates.
				VisionHandler.getInstance().update(table);
		}
	};
	
	Runnable poller = new Runnable() {
		@Override
		public void run() {
			while (true) {
				VisionHandler.getInstance().update(NetworkTable.getTable("GRIP/myLinesReport"));
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	};
	
	public void init() { // This sets everything up to listen!
		try {
			new ProcessBuilder(CLEAR_TMP_CMD).inheritIO().start();
			new ProcessBuilder(GRIP_CMD).inheritIO().start();
			//new ProcessBuilder("echo").inheritIO().start();
		} catch (IOException e) {
			System.out.println(e);
		}
		GRIPTable = NetworkTable.getTable("GRIP/myLinesReport");
		//GRIPTable.addTableListener(updater);
		
		new Thread(poller).start();
	}
	
	public static VisionHandler getInstance() { // Yay. Boilerplate.
		if (instance != null)
			return instance;
		instance = new VisionHandler();
		return getInstance();
	}

	public boolean horizontalP(double degrees) { // This determines if an angle is vertical or horizontal
		degrees = Math.abs((degrees - 45) % 180);
		return degrees > 90;
	}
	
	public double average(List<Line> list, Field field) {
		double x = 0;
		for (Line l : list) {
			try {
				x += field.getDouble(l);
			} catch (IllegalArgumentException e) {
				System.out.println("Tell Daroc that the VisionHandler is misbehaving. (A)");
			} catch (IllegalAccessException e) {
				System.out.println("Tell Daroc that the VisionHandler is misbehaving. (B)");
			}
		}
		x /= list.size();
		return x;
	}
	
	public void update(ITable table) { // This does the workhorsing of the updates
		//System.out.println(table.getKeys());
		Line[] lineTable = getLines(table);
		System.out.println(lineTable.length);
		
		ArrayList<Line> lines = new ArrayList<Line>(); // This would be a Set, if I could get sets working.
		lines.addAll(Arrays.asList(lineTable));
		lines.sort(sort);
		ArrayList<Line> horizontal = new ArrayList<Line>(); // Again, sets.
		ArrayList<Line> vertical = new ArrayList<Line>();
		
		for (Line line : lines) { // This splits them up.
			if (horizontalP(line.angle)) {
				horizontal.add(line);
			} else {
				vertical.add(line);
			}
		}
		
		if (vertical.size() < 4 || horizontal.size() < 2) {
			return;
		}
		
		// This section finds the average of the endpoints of the likely goal lines.
		vertical.sort(sort);
		horizontal.sort(sort);
		try {
			double n = average(vertical.subList(0, 4), Line.class.getField("x1"));
			this.x = n + average(vertical.subList(0,  4), Line.class.getField("x2"));
			this.x /= 2;
			this.y = n; // Note: If this mis-aims vertically, this is the culpret.
						// It assumes that x1 should be the endpoint closer to the top-left corner.
						// If that's wrong, then this should be changed.
		} catch (NoSuchFieldException | SecurityException e) {
			System.out.println("Tell Daroc that the VisionHandler is misbehaving. (C)");
		}
		
		
		
	}
	
	public Line[] getLines(ITable lineReport) { // This marshals stuff from the NetworkTable to
												// a bunch of line objects.
		double[] x1s = lineReport.getNumberArray("x1", Constants.emptydoubleTable);
		double[] x2s = lineReport.getNumberArray("x2", Constants.emptydoubleTable);
		double[] y1s = lineReport.getNumberArray("y1", Constants.emptydoubleTable);
		double[] y2s = lineReport.getNumberArray("y2", Constants.emptydoubleTable);
		double[] lengths = lineReport.getNumberArray("length", Constants.emptydoubleTable);
		double[] angles = lineReport.getNumberArray("angle", Constants.emptydoubleTable);
		Line[] lineTable = new Line[x1s.length];
		for (int i=0; i<x1s.length; i++) {
			lineTable[i] = new Line();
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].x1 = x1s[i];
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].x2 = x2s[i];
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].y1 = y1s[i];
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].y2 = y2s[i];
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].length = lengths[i];
		}
		for (int i=0; i<x1s.length; i++) {
			lineTable[i].angle = angles[i];
		}
		return lineTable;
	}
	
	private class Line { // This would be a struct, if java weren't stupid.
		@SuppressWarnings("unused")
		public double x1, x2, y1, y2, length, angle;
	}

}
