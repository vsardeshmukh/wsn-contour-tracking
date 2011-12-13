/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.*;

/* Panel for drawing mote-data graphs */
class MoteGrid extends JPanel {
	final static int BORDER_LEFT = 20;
	final static int BORDER_RIGHT =20;
	final static int BORDER_TOP = 10;
	final static int BORDER_BOTTOM = 10;

	final static int TICK_SPACING = 40;
	final static int MAX_TICKS = 16;
	final static int TICK_WIDTH = 10;

	final static int MIN_WIDTH = 50;

	//Maximum sensor reading - Vivek
	final static double MAXREAD = 1000.0 ;

	int gx0, gx1, gy0, gy1; // graph bounds
	int scale = 2; // gx1 - gx0 == MIN_WIDTH << scale
	Window parent;

	/* Graph to screen coordinate conversion support */
	int height, width;
	double xscale, yscale;

	void updateConversion() {
		height = getHeight() - BORDER_TOP - BORDER_BOTTOM;
		width = getWidth() - BORDER_LEFT - BORDER_RIGHT;
		if (height < 1) {
			height = 1;
		}
		if (width < 1) {
			width = 1;
		}
		xscale = (double)width / (gx1 - gx0 + 1);
		yscale = (double)height / (gy1 - gy0 + 1);
	}

	Graphics makeClip(Graphics g) {
		return g.create(BORDER_LEFT, BORDER_TOP, width, height);
	}

	MoteGrid(Window parent) {
		setPreferredSize(new Dimension(0, 800));
		this.parent = parent;
		gx0 = 0; gx1 = MIN_WIDTH << scale;
	}

	protected void paintComponent(Graphics g) {
		ContourTracking.Snapshot snapshot = parent.parent.getLatestSnapshot();
		if(snapshot == null)
			return;

		//snapshot.debug();
		// draw canvas
		Graphics2D g2d = (Graphics2D)g;
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, getWidth(), getHeight());

		// compute colors and draw motes
		int DIM = snapshot.getGridDimension();
		int centerX = getWidth() / 2;
		int centerY = getHeight() / 2;
		int marginX = getWidth() / 5;
		int marginY = getHeight() / 100 * 20;
		int gridX = marginX;
		int gridY = marginY;
		int gridWidth = getWidth() - 2 * marginX;
		int gridHeight = getHeight() - 2 * marginY;
		int offsetX = gridWidth / (DIM - 1);
		int offsetY = gridHeight / (DIM - 1);
		int radius = 15;

		// draw grid lines
		float []f={ 10f, 10f };
		Stroke stroke = g2d.getStroke();
		g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0F, f, 0f)); 
		g2d.setColor(Color.WHITE);
		g2d.drawLine(gridX, gridY, gridX+gridWidth, gridY);
		g2d.drawLine(gridX, gridY, gridX, gridY+gridHeight);
		g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX, gridY+gridHeight);
		g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX+gridWidth, gridY);
		for(int i  = 1; i < DIM - 1; i++) {
			g2d.drawLine(gridX + (i * gridWidth / (DIM-1)), gridY, gridX + (i * gridWidth / (DIM-1)), gridY + gridHeight);
			g2d.drawLine(gridX, gridY + (i * gridHeight / (DIM-1)), gridX + gridWidth, gridY + (i * gridHeight / (DIM-1)));
		}
		g2d.setStroke(stroke);

		for(Map.Entry<Integer, ContourTracking.Mote> entry: snapshot.entrySet()) {
			boolean same = true;
			int id = entry.getKey().intValue();
			ContourTracking.Mote mote = entry.getValue();

			ContourTracking.Mote nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.L));
			if(nbr != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.R))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.B))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.U))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			mote.setColor(same ? (mote.isAboveThreshold() ? Color.WHITE : Color.BLACK) : Color.GRAY);
			int idx = snapshot.getMoteIndex(id);
			int row = idx / DIM;
			int col = idx % DIM;
			int moteX = gridX + col * offsetX;
			int moteY = gridY + gridHeight - row * offsetY;
			//System.out.printf("mote[%d] idx: %d, sample: %d, color: %s\n", id, idx, mote.getSample(), mote.getColor());

			// draw mote on the grid
			if(mote.getColor() == Color.BLACK) { // black
				g2d.setColor(mote.getColor());
				g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(Color.WHITE);
				g2d.drawRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(Color.WHITE);
				g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
			} else { // white or gray
				g2d.setColor(mote.getColor());
				g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(mote.getColor() == Color.WHITE ? Color.BLACK : Color.RED);
				g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
			}
		}

		// draw contour splines for each blob
		for(ContourTracking.Blob blob: snapshot.getBlobs()) {
			// step1. find contour motes without neighbors in either directions
			Set<Point> cPoints = new HashSet<Point>();
			for(Integer moteId: blob.getMotes()) {
				int idx = snapshot.getMoteIndex(moteId.intValue());
				int x = idx % DIM; // col
				int y = idx / DIM; // row
				int moteX = gridX + x * offsetX;
				int moteY = gridY + gridHeight - y * offsetY;

				int nbrL = snapshot.getMoteNeighborId(moteId.intValue(), ContourTracking.Position.L);
				int nbrR = snapshot.getMoteNeighborId(moteId.intValue(), ContourTracking.Position.R);
				int nbrU = snapshot.getMoteNeighborId(moteId.intValue(), ContourTracking.Position.U);
				int nbrB = snapshot.getMoteNeighborId(moteId.intValue(), ContourTracking.Position.B);
				if(nbrL < 0 || !blob.contains(nbrL)) {
					Point p = new Point(moteX - offsetX / 4, moteY);
					cPoints.add(p);
					//System.out.printf("add control point (%d, %d)\n", (int)p.getX(), (int)p.getY());
				}

				if(nbrR < 0 || !blob.contains(nbrR)) {
					Point p = new Point(moteX + offsetX / 4, moteY);
					cPoints.add(p);
					//System.out.printf("add control point (%d, %d)\n", (int)p.getX(), (int)p.getY());
				}

				if(nbrU < 0 || !blob.contains(nbrU)) {
					Point p = new Point(moteX, moteY - offsetY / 3);
					cPoints.add(p);
					//System.out.printf("add control point (%d, %d)\n", (int)p.getX(), (int)p.getY());
				}

				if(nbrB < 0 || !blob.contains(nbrB)) {
					Point p = new Point(moteX, moteY + offsetY / 3);
					cPoints.add(p);
					//System.out.printf("add control point (%d, %d)\n", (int)p.getX(), (int)p.getY());
				}
			}

			// step2. sort and cluster the contour motes according to their x and y coordinates
			SortedMap<Integer, Vector<Point>> contourMotes = new TreeMap<Integer, Vector<Point>>();
			for(Point cp: cPoints) {
				int x = (int)cp.getX();
				int y = (int)cp.getY();
				Vector<Point> points = contourMotes.get(new Integer(x));
				if(points == null) {
					points = new Vector<Point>();
					points.add(cp);
					contourMotes.put(new Integer(x), points);
				} else {
					boolean inserted = false;
					for(int i = 0; i < points.size(); i++) {
						Point p = points.get(i);
						if(y < (int)p.getY()) {
							points.insertElementAt(cp, i);
							inserted = true;
							break;
						}
					}
					if(!inserted) 
						points.add(cp);
				}
			}

			// step3. add control points for each contour motes
			ContourSpline spline = new ContourSpline();
			int refX = contourMotes.firstKey().intValue();
			int refY = (int)contourMotes.get(new Integer(refX)).firstElement().getY();
			spline.addPoint(refX, refY);
			contourMotes.get(new Integer(refX)).removeElementAt(0);
			//System.out.printf("add ref control point(%d, %d)\n", refX, refY);
			for(Map.Entry<Integer, Vector<Point>> entry: contourMotes.entrySet()) {
				Vector<Point> points = entry.getValue();
				for(Iterator<Point> itr = points.iterator(); itr.hasNext();) {
					Point p = itr.next();
					if((int)p.getY() <= refY) {
						int x = entry.getKey().intValue();
						int y = (int)p.getY();
						spline.addPoint(x, y);
						//System.out.printf("add control point(%d, %d)\n", x, y);
						itr.remove();
					}
				}
			}

			while(!contourMotes.isEmpty()) {
				int x = contourMotes.lastKey().intValue();
				Vector<Point> points = contourMotes.get(new Integer(x));
				while(!points.isEmpty()) {
					Point p = (x > refX) ? points.firstElement() : points.lastElement();
					int y = (int)p.getY();
					spline.addPoint(x, y);
					//System.out.printf("add control point(%d, %d)\n", x, y);
					points.remove(p);
				}
				contourMotes.remove(new Integer(x));
			}

	 		// step4. paint the spline
			g2d.setColor(Color.RED);
			spline.paint(g);
		}
	}

	/*
	protected void paintComponent(Graphics g) {
		//Repaint. Synchronize on ContourTracking to avoid data changing.
		synchronized (parent.parent) {
			int count = parent.moteListModel.size();
			// compute contour values
			Vector motes = new Vector();
			for (int i = 0; i < count; i++) {
				Data data = parent.parent.data;
				int id = parent.moteListModel.get(i);
				int sample = data.getData(id, data.maxX(id));
				int mote[] = new int[4];
				mote[0] = id;
				mote[1] = sample;
				mote[2] = (sample >= parent.parent.threshold ? 1 : 0);
				motes.add(mote);
			}

			// draw canvas
			Graphics2D g2d = (Graphics2D)g;
			updateConversion();
			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, getWidth(), getHeight());

			// compute colors and draw motes
			final int DIM = count <= 9 ? 3 : 4;
			int centerX = getWidth() / 2;
			int centerY = getHeight() / 2;
			int marginX = getWidth() / 10;
			int marginY = getHeight() / 100 * 20;
			int gridX = 2*marginX;
			int gridY = marginY;
			//int gridWidth = getWidth() - 2 * marginX;
			int gridHeight = getHeight() - 2 * marginY;
			//Bluff to draw squares - Vivek
			int gridWidth = gridHeight;
			int offsetX = gridWidth / (DIM - 1);
			int offsetY = gridHeight / (DIM - 1);
			int radius = 15;

			// draw grid lines
			Graphics2D clipped = (Graphics2D)makeClip(g2d);
			clipped.setColor(Color.WHITE);
			float []f={ 10f, 10f };
			Stroke stroke = clipped.getStroke();
			clipped.setStroke(new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0F, f, 0f)); 
			clipped.drawLine(gridX, gridY, gridX+gridWidth, gridY);
			clipped.drawLine(gridX, gridY, gridX, gridY+gridHeight);
			clipped.drawLine(gridX+gridWidth, gridY+gridHeight, gridX, gridY+gridHeight);
			clipped.drawLine(gridX+gridWidth, gridY+gridHeight, gridX+gridWidth, gridY);
			//clipped.drawLine(gridX+gridWidth/2, gridY, gridX+gridWidth/2, gridY+gridHeight);
			//clipped.drawLine(gridX, gridY+gridHeight/2, gridX+gridWidth, gridY+gridHeight/2);
			//For 16 motes - Vivek
			clipped.drawLine(gridX+gridWidth/3, gridY, gridX+gridWidth/3, gridY+gridHeight);
			clipped.drawLine(gridX+(2*gridWidth/3), gridY, gridX+(2*gridWidth/3), gridY+gridHeight);
			clipped.drawLine(gridX, gridY+gridHeight/3, gridX+gridWidth, gridY+gridHeight/3);
			clipped.drawLine(gridX, gridY+(2*gridHeight/3), gridX+gridWidth, gridY+(2*gridHeight/3));
			// End - Vivek
			clipped.setStroke(stroke);
			for (int i = 0; i < (count > 16 ? 16 : count) ; i++) {
				boolean same = true;
				int mote[] = (int[])motes.elementAt(i);
				if(i > 0 && (i % DIM) > 0) {// left neight
					same = mote[2] == ((int[])motes.elementAt(i-1))[2];
					//System.out.println(i+"th mote[" + mote[0] + "] with contour value: " + mote[2] + " while L mote[" + ((int[])motes.elementAt(i-1))[0] + "] has value " + ((int[])motes.elementAt(i-1))[2]);
				}

				if(same && (i < count - 1 && (i % DIM) < DIM-1)) { // right neighbor
					same = mote[2] == ((int[])motes.elementAt(i+1))[2];
					//System.out.println(i+"th mote[" + mote[0] + "] with contour value: " + mote[2] + " while R mote[" + ((int[])motes.elementAt(i+1))[0] + "] has value " + ((int[])motes.elementAt(i+1))[2]);
				}

				if(same && i > DIM - 1) // bottom neighbor
					same = mote[2] == ((int[])motes.elementAt(i-DIM))[2];

				if(same && i+DIM < count) // top neighbor
					same = mote[2] == ((int[])motes.elementAt(i+DIM))[2];

				// BLACK = 0, WHITE = 1, GREY = 2
				mote[3] = same ? mote[2] : 2;

				int row = i / DIM;
				int col = i % DIM;
				int moteX = gridX + col * offsetX;
				int moteY = gridY + gridHeight - row * offsetY;
		
				// Draw circles for contour - Vivek
				Ellipse2D.Double circle;
				double circleR;
				if(mote[2] == 1) {
					circleR = ( MAXREAD - (parent.parent.threshold - mote[1] ) ) / (2*MAXREAD);
					circleR = circleR * (gridHeight/3);  
					clipped.setColor(new Color(153,153,255));
					circle=new Ellipse2D.Double(moteX-circleR, moteY-circleR, 2*circleR, 2*circleR);
					//System.out.println("R = "+ circleR);
					clipped.fill(circle);
				}
				//End - Vivek

				// draw mote on the grid
						if(mote[3] == 0) { // black
					clipped.setColor(Color.BLACK);
					clipped.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					clipped.setColor(Color.WHITE);
					clipped.drawRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					clipped.setColor(Color.WHITE);
					clipped.drawString(String.valueOf(mote[0]), moteX-3, moteY+4);
				} else { // white or gray
					clipped.setColor(mote[3] == 1 ? Color.WHITE : Color.GRAY);
					clipped.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					clipped.setColor(mote[3] == 1 ? Color.BLACK : Color.RED);
					clipped.drawString(String.valueOf(mote[0]), moteX-3, moteY+4);
				}
				//System.out.println("mote[" + mote[0] +"] row: " + row + ", col: " + col + ", x: " + moteX + ", y: " + moteY + ", count: " + count + ", DIM: " + DIM);
				//System.out.println("mote[" + mote[0] + "] sample: " + mote[1] + ", threshold: " + parent.parent.threshold + ", contour: " + mote[2] + ", same: " + (same ? "true": "false") + ", color: " + mote[3]);
			}
		}
	}*/
}
