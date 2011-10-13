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
class MoteGrid extends JPanel
{
	final static int BORDER_LEFT = 20;
	final static int BORDER_RIGHT =20;
	final static int BORDER_TOP = 10;
	final static int BORDER_BOTTOM = 10;

	final static int TICK_SPACING = 40;
	final static int MAX_TICKS = 16;
	final static int TICK_WIDTH = 10;

	final static int MIN_WIDTH = 50;

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
		this.parent = parent;
		gx0 = 0; gx1 = MIN_WIDTH << scale;
	}

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
			int gridX = marginX;
			int gridY = marginY;
			int gridWidth = getWidth() - 2 * marginX;
			int gridHeight = getHeight() - 2 * marginY;
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
			clipped.drawLine(gridX+gridWidth/2, gridY, gridX+gridWidth/2, gridY+gridHeight);
			clipped.drawLine(gridX, gridY+gridHeight/2, gridX+gridWidth, gridY+gridHeight/2);
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

				// draw mote on the grid
				int row = i / DIM;
				int col = i % DIM;
				int moteX = gridX + col * offsetX;
				int moteY = gridY + gridHeight - row * offsetY;
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
	}
}
