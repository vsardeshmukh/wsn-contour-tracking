/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

import net.tinyos.message.*;
import net.tinyos.util.*;
import java.io.*;
import java.util.*;
import java.lang.Math;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;

/* The "ContourTracking" demo app. Displays graphs showing data received from
   the ContourTracking mote application, and allows the user to:
   - zoom in or out on the X axis
   - set the scale on the Y axis
   - change the sampling period
   - change the color of each mote's graph
   - clear all data

   This application is in three parts:
   - the Node and Data objects store data received from the motes and support
     simple queries
   - the Window and Graph and miscellaneous support objects implement the
     GUI and graph drawing
   - the ContourTracking object talks to the motes and coordinates the other
     objects

   Synchronization is handled through the ContourTracking object. Any operation
   that reads or writes the mote data must be synchronized on ContourTracking.
   Note that the messageReceived method below is synchronized, so no further
   synchronization is needed when updating state based on received messages.
*/

public class ContourTracking extends TimerTask implements MessageListener
{
	MoteIF mote;
	Data data;
	Window window;

	/* The current sampling period. If we receive a message from a mote
	   with a newer version, we update our interval. If we receive a message
	   with an older version, we broadcast a message with the current interval
	   and version. If the user changes the interval, we increment the
	   version and broadcast the new interval and version. */
	int interval = Constants.DEFAULT_INTERVAL;
	int threshold = Constants.DEFAULT_THRESHOLD;
	int version = -1;

	/* event tracking info - Farley */
	private boolean lightEventMode = true;
	private Vector snapshots = new Vector();

	/* TimerTask: update motes clock periodically */
	public void run() {
		sendBeacon();
		track();
	}

	/* Main entry point */
	void exec() {
		data = new Data(this);
		window = new Window(this);
		window.setup();
		mote = new MoteIF(PrintStreamMessenger.err);
		mote.registerListener(new ContourTrackingMsg(), this);
		new Timer().schedule(this, 0, 1000);
	}

	private synchronized boolean track() {
		int count = window.moteListModel.size();
		long timestamp = data.getLastSamplingTimestamp();
		if(timestamp < 0) {
			System.out.println("No tracking since there are no valid sampling timestamp.");
			return false;
		}
		//if(count != 9 && count != 16)
		//	return false;

		//System.out.println("There are " + snapshots.size() + " snapshots done");
		Vector prevSnapshot = snapshots.isEmpty() ? null : (Vector)snapshots.lastElement();
		if(prevSnapshot != null) {
			long ts = ((Long)prevSnapshot.firstElement()).longValue();
			//System.out.println("prev snapshot timestamp: " + ts + ", last sampling timestamp: " + timestamp);
			if(ts == timestamp) {
				System.out.println("Last sampling timestamp has not been updated.");
				return false;
			}
		}

		int mode = lightEventMode ? 1 : 0;
		Vector motes = new Vector();
		Vector eventMotes = new Vector();
		for (int i = 0; i < count; i++) {
			int id = window.moteListModel.get(i);
			int sample = data.getData(id, data.maxX(id));
			int mote[] = new int[3];
			mote[0] = id;
			mote[1] = sample;
			mote[2] = (sample >= threshold ? 1 : 0);
			motes.add(mote);
			if(mote[2] == mode) {
				//System.out.println("mote[" + id + "] is bright.");
				eventMotes.add(mote);
			}
		}
		
		Vector blobs = new Vector();
		Vector eventMotes2 = new Vector(eventMotes);
		for(Iterator itr = eventMotes.iterator(); itr.hasNext();) {
			int[] mote = (int[])itr.next();
			Vector blob = null;
			//System.out.println("mote[" + mote[0] + "] is event mote");
			if(eventMotes2.contains(mote)) { // not contained yet
				blob = new Vector();
				blob.add(mote);
				eventMotes2.remove(mote);
				//System.out.println("new blob with mote[" + mote[0] + "]");
			} else {
				for(Iterator iter = blobs.iterator(); iter.hasNext();) {
					blob = (Vector)iter.next();
					if(blob.contains(mote)) {
						//System.out.println("existing blob with mote[" + mote[0] + "]");
						break;
					} else
						blob = null;
				}
			}
			assert(blob != null);

			// add event mote neighbors
			final int DIM = (count <= 9 ? 3 : 4);
			int idx = motes.indexOf(mote);
			int[] join = null;
			if(idx % DIM < DIM - 1 && idx < count - 1) { // R neighbor
				int[] R = (int[])motes.get(idx+1);
				if(eventMotes2.contains(R)){
					blob.add(R);
					eventMotes2.remove(R);
				} else if((!blobs.contains(blob) || !blob.contains(R)) && eventMotes.contains(R) ) {
					join = R;
				}
			}

			if(idx % DIM > 0 && idx + DIM <= count) { // LU neighbor
				int[] LU = (int[])motes.get(idx+DIM-1);
				if(eventMotes2.contains(LU)){
					blob.add(LU);
					eventMotes2.remove(LU);
				} else if((!blobs.contains(blob) || !blob.contains(LU)) && eventMotes.contains(LU))
					join = LU;
			}

			if(idx + DIM < count) {
				int[] U = (int[])motes.get(idx+DIM); // U neighbor
				if(eventMotes2.contains(U)) {
					blob.add(U);
					eventMotes2.remove(U);
				} else if((!blobs.contains(blob) || !blob.contains(U)) && eventMotes.contains(U))
					join = U;
			}

			if(idx % DIM < DIM - 1 && idx + DIM + 1 < count) { // RU neighbor
				int[] RU = (int[])motes.get(idx+DIM+1);
				if(eventMotes2.contains(RU)) {
					blob.add(RU);
					eventMotes2.remove(RU);
				} else if((!blobs.contains(blob) || !blob.contains(RU)) && eventMotes.contains(RU))
					join = RU;
			}

			if(join != null) {
				for(Iterator iterator = blobs.iterator(); iterator.hasNext();) {
					Vector b = (Vector)iterator.next();
					if(b.contains(join)) {
						blob.addAll(b);
						iterator.remove();
						break;
					}
				}
			}
			
			if(!blobs.contains(blob))
				blobs.add(blob);
		}

		if(blobs.isEmpty())
			return false;
		
		Vector snapshot = new Vector();
		snapshot.add(new Long(timestamp));
		snapshot.add(blobs);
		//debugSnapshot(snapshot);

		if(isSnapshotChanged(snapshot)) {
			detect(snapshot);
			snapshots.add(snapshot);
			if(snapshots.size() > 10)
				snapshots.remove(snapshots.firstElement());
			System.out.println("Snapshot changed");
		} else { // update timestamp only
			snapshots.remove(snapshots.lastElement());
			snapshots.add(snapshot);
			System.out.println("Snapshot not changed");
		}

/* Bob's experimental code
		snapshots.add(snapshot);
		if(snapshots.size() > 10)
			snapshots.remove(snapshots.firstElement());

		// debug bright or dark sets
		analyzeSnapshots(snapshots);
		//debugSnapshot(snapshot);
*/

		return true;
	}

	private int getGridDimension() {
		return window.moteListModel.size() <= 9 ? 3 : 4;
	}

	private int computeBlobIntersectionCount(Vector blob1, Vector blob2) {
		if(blob1 == null || blob1.isEmpty() | blob2 == null || blob2.isEmpty())
			return 0;

		int count = 0;
		for(Iterator itr = blob1.iterator(); itr.hasNext();) {
			int[] mote1 = (int[])itr.next();
			for(Iterator iter = blob2.iterator(); iter.hasNext();) {
				int[] mote2 = (int[])iter.next();
				count += (mote1[0] == mote2[0] ? 1 : 0);
			}
		}
		return count;
	}

	private boolean isBlobNeighboring(Vector blob1, Vector blob2) {
		if(blob1 == null || blob1.isEmpty() | blob2 == null || blob2.isEmpty())
			return false;

		if(computeBlobIntersectionCount(blob1, blob2) > 0)
			return false;

		final int DIM = getGridDimension();
		boolean grid[][] = new boolean[DIM][DIM];
		for(Iterator itr = blob1.iterator(); itr.hasNext();) {
			int[] mote = (int[])itr.next();
			int idx = mote[0] - 1;
			grid[idx / DIM][idx % DIM] = true;
		}

		for(Iterator itr = blob2.iterator(); itr.hasNext();) {
			int[] mote = (int[])itr.next();
			int idx = mote[0] - 1;
			int row = idx / DIM;
			int col = idx % DIM;
			if(row + 1 < DIM) {
				if(col - 1 >= 0 && grid[row+1][col-1]) // LU
						return true;
				if(grid[row+1][col]) // U
						return true;
				if(col + 1 < DIM && grid[row+1][col+1]) // RU
						return true;
			}

			if(col - 1 >= 0 && grid[row][col-1]) // L
				return true;

			if(col + 1 < DIM && grid[row][col+1]) // R
				return true;

			if(row - 1 >= 0) {
				if(col - 1 >= 0 && grid[row-1][col-1]) // LB
						return true;
				if(grid[row-1][col]) // B
						return true;
				if(col + 1 < DIM && grid[row-1][col+1]) // RB
						return true;
			}	
		}
		return false;
	}

	private Point2D computeBlobCenter(Vector blob) {
		if(blob == null || blob.isEmpty())
			return null;

		double x = 0, y = 0;
		final int DIM = getGridDimension();
		for(Iterator itr = blob.iterator(); itr.hasNext();) {
			int[] mote = (int[])itr.next();
			int idx = mote[0] - 1;
			x += idx % DIM;
			y += idx / DIM;
		}

		return new Point2D.Double(x / blob.size(), y / blob.size());
	}

	private boolean isSnapshotChanged(Vector snapshot) {
		if(snapshot == null)
			return false;

		long timestamp2 = ((Long)snapshot.firstElement()).longValue();
		Vector blobs2 = (Vector)snapshot.lastElement();
		if(blobs2 == null || blobs2.isEmpty())
			return false;

		if(snapshots.isEmpty())
			return true;

		Vector prevSnapshot = (Vector)snapshots.lastElement();
		Vector blobs1 = (Vector)prevSnapshot.lastElement();
		long timestamp1 = ((Long)prevSnapshot.firstElement()).longValue();

		if(timestamp1 == timestamp2)
			return false;

		int count1 = 0, count2= 0;
		final int DIM = getGridDimension();
		System.out.println("DIM: " + DIM);
		boolean grid[][] = new boolean[DIM][DIM];
		//boolean grid[][] = new boolean[4][4];
		for(Iterator itr = blobs1.iterator(); itr.hasNext();) {
			Vector blob = (Vector)itr.next();
			for(Iterator iter = blob.iterator(); iter.hasNext();) {
				count1++;
				int[] mote = (int[])iter.next();
				int idx = mote[0] - 1;
				grid[idx / DIM][idx % DIM] = true;
			}
		}

		for(Iterator itr = blobs2.iterator(); itr.hasNext();) {
			Vector blob = (Vector)itr.next();
			for(Iterator iter = blob.iterator(); iter.hasNext();) {
				count2++;
				int[] mote = (int[])iter.next();
				int idx = mote[0] - 1;
				if(!grid[idx / DIM][idx % DIM])
					return true;
			}
		}

		return count1 != count2;
	}

	private int sizeOfBlobs(Vector blobs) {
		int size = 0;
		for(Iterator itr = blobs.iterator(); itr.hasNext();) {
			Vector blob = (Vector)itr.next();
			size += blob.size();
		}
		return size;
	}
	
	// function two print 2 decimal float
	double roundTwoDecimals(double d) {
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		return Double.valueOf(twoDForm.format(d));
	}
	//end
	
	private void detect(Vector snapshot) {
		if(snapshot == null || snapshot.lastElement() == null)
			return;

		long timestamp = ((Long)snapshot.firstElement()).longValue();
		Vector blobs2 = (Vector)snapshot.lastElement();
		if(blobs2.isEmpty())
			return;

		if(snapshots.isEmpty()) {
			window.showText("Blob(s) Formed");
			return;
		}
		
		System.out.println("break 1");
		Vector prevSnapshot = (Vector)snapshots.lastElement();
		Vector blobs1 = (Vector)prevSnapshot.lastElement();
		boolean MOVE = false, MERGE = false, SPLIT = false, EXPAND = false, SHRINK = false;
		Point2D from, to;
		String line = "";

		// MOVE: neighboring or intersect with only one blob of the same size
		for(Iterator itr = blobs2.iterator(); !MOVE && itr.hasNext();) {
			Vector blob2 = (Vector)itr.next();
			int intersections = 0;
			double dirX = 0;
			double dirY = 0;
			for(Iterator iter = blobs1.iterator(); !MOVE && iter.hasNext();) {
				Vector blob1 = (Vector)iter.next();
				if(isBlobNeighboring(blob1, blob2) || computeBlobIntersectionCount(blob1, blob2) > 0) {
					if(blob1.size() == blob2.size()) {
						intersections++;
						from = computeBlobCenter(blob1);
						to = computeBlobCenter(blob2);
						System.out.println("from(" + from.getX() + ", " + from.getY() + "), to(" + to.getX() + ", " + to.getY() + ")");
						dirX = roundTwoDecimals( to.getX() - from.getX() );
						dirY = roundTwoDecimals ( to.getY() - from.getY() );
					}
				}
			}
			if(intersections == 1) {
				MOVE = true;
				if(dirX>0) {
					if(dirY>0)
						line = "MOVED in NE";
					else if(dirY<0)
						line = "MOVED in SE";
					else
						line = "MOVED in E";
				}
				else if(dirX<0) {
					if(dirY>0)
						line = "MOVED in NW";
					else if(dirY<0)
						line = "MOVED in SW";
					else
						line = "MOVED in W";
				}
				else {
					if(dirY>0)
						line = "MOVED in N";
					else if(dirY<0)
						line = "MOVED in S";
					else
						line = "No Move";
				}
				//line = "MOVE(" + dirX + ", " + dirY + ")";
			}
		}
System.out.println("break 2");
		
		// MERGE: intersect with two or more, last --> prev
		int intersections = 0;
		for(Iterator itr = blobs2.iterator(); intersections < 2 && itr.hasNext(); intersections = 0) {
			Vector blob2 = (Vector)itr.next();
			for(Iterator iter = blobs1.iterator(); iter.hasNext();) {
				Vector blob1 = (Vector)iter.next();
				if(computeBlobIntersectionCount(blob1, blob2) > 0)
						intersections++;
			}
			if(intersections > 1) {
				MERGE = true;
				line += line.length() > 0 ? ", MERGE" : "MERGE";
			}
		}
System.out.println("break 3");
		
		// SPLIT: intersect with two or more, prev --> last
		intersections = 0;
		for(Iterator itr = blobs1.iterator(); intersections < 2 && itr.hasNext(); intersections = 0) {
			Vector blob1 = (Vector)itr.next();
			for(Iterator iter = blobs2.iterator(); iter.hasNext();) {
				Vector blob2 = (Vector)iter.next();
				if(computeBlobIntersectionCount(blob2, blob1) > 0)
						intersections++;
			}
			if(intersections > 1) {
				SPLIT = true;
				line += line.length() > 0 ? ", SPLIT" : "SPLIT";
			}
		}

System.out.println("break 4");
		// EXPAND: total number of bright motes increases
		// SHRINK: total number of bright motes decreases
		int sizeOfBlobs1 = sizeOfBlobs(blobs1);
System.out.println("break 4.1");
		int sizeOfBlobs2 = sizeOfBlobs(blobs2);
		if(sizeOfBlobs1 < sizeOfBlobs2) {
			EXPAND = true;
			line += line.length() > 0 ? ", EXPAND" : "EXPAND";
		} else if(sizeOfBlobs1 > sizeOfBlobs2) {
			SHRINK = true;
			line += line.length() > 0 ? ", SHRINK" : "SHRINK";
		}

		if(blobs1.size() < blobs2.size())
			line += line.length() > 0 ? ", FORM" : "BLOB FORM";
		else if(blobs1.size() > blobs2.size())
			line += line.length() > 0 ? ", VANISH" : "BLOB VANISH";

System.out.println("#blobs1: " + blobs1.size() + ", #blobs2: " + blobs2.size() + ", sizeOfBlobs1: " + sizeOfBlobs1 + ", sizeOfBlobs2: " + sizeOfBlobs2);
		line += line.length() > 0 ? ", " + timestamp : "No New Event; " + timestamp ;
		window.showText(line);
System.out.println("line: " + line);
	}

	/*function to analyze changes in topology, prints to Terminal - Bob 
	void analyzeSnapshots(Vector snapshots){
		int count = 1; 
		Vector snapshot2 = (Vector)snapshots.lastElement();
		Vector snapshot1 = (Vector)snapshots.get(snapshots.size()-2);
		Vector blobs1 = (Vector)snapshot1.lastElement(); 
		Vector blobs2 = (Vector)snapshot2.lastElement();
		boolean eventFound = false;
		boolean sameBlob = false;
		String events = "";
		Iterator iter2;
		
		
		//move
		for(Iterator itr1 = blobs1.iterator(); itr1.hasNext(); count++) {
			Vector blob1 = (Vector)itr1.next();
			for (Iterator itr2 = blobs2.iterator(); itr2.hasNext(); count++){
				Vector blob2 = (Vector)itr2.next();
				if (blob2.size() == blob1.size()){
					iter2 = blob2.iterator();
					for(Iterator iter1 = blob1.iterator(); iter1.hasNext(); count++){
						int[] mote1 = (int[])iter1.next();
						int[] mote2 = (int[])iter2.next();
						if(mote1[0]!=mote2[0]){
							eventFound = true;
						}
					}
				}
			}
			
		}
		if(eventFound){
			//events += "move ";
			System.out.println("move");
		}
		System.out.println(eventFound);
		eventFound = false;
		//expand

		


		//shrink




		//merge




		//split

		
		
	}*/

	void debugSnapshot(Vector snapshot) {
		int count = 1; 
		long timestamp = ((Long)snapshot.firstElement()).longValue();
		System.out.println("DEBUG: snapshot at " + timestamp);
		Vector blobs = (Vector)snapshot.lastElement();
		for(Iterator itr = blobs.iterator(); itr.hasNext(); count++) {
			Vector blob = (Vector)itr.next();
			System.out.print("blob " + count + ": {" );
			for(Iterator iter = blob.iterator(); iter.hasNext();) {
				int[] mote = (int[])iter.next();
				System.out.print(mote[0]);
				if(iter.hasNext())
					System.out.print(", ");
			}
			System.out.println("}");
		}
	}

	/* The data object has informed us that nodeId is a previously unknown
	   mote. Update the GUI. */
	void newNode(int nodeId) {
		window.newNode(nodeId);
	}

	public synchronized void messageReceived(int dest_addr,	Message msg) {
		if (msg instanceof ContourTrackingMsg) {
			ContourTrackingMsg omsg = (ContourTrackingMsg)msg;

			/* Update interval and mote data */
			periodUpdate(omsg.get_version(), omsg.get_interval());
			//System.out.println("mote[" + omsg.get_id() + "] msg seq: " + omsg.get_count() + " with clock: " + omsg.get_clock() + ", local ts: " + omsg.get_ftsp_local_timestamp() + ", global ts: " + omsg.get_ftsp_global_timestamp() + ", root id: " + omsg.get_ftsp_root_id() + ", skew: " + omsg.get_ftsp_skew() + ", synced: " + omsg.get_ftsp_synced() + " at " + System.currentTimeMillis());
			if(omsg.get_ftsp_synced() > 0) {
				data.update(omsg.get_id(), omsg.get_count(), omsg.get_readings(), omsg.get_ftsp_global_timestamp(), omsg.get_ftsp_synced() > 0);
				/* Inform the GUI that new data showed up */
				window.newData();
			}
		}
	}

	/* A potentially new version and interval has been received from the mote */
	void periodUpdate(int moteVersion, int moteInterval) {
		if (moteVersion > version) {
			/* It's new. Update our vision of the interval. */
			version = moteVersion;
			interval = moteInterval;
			window.updateSamplePeriod();
		}
		else if (moteVersion < version) {
			/* It's old. Update the mote's vision of the interval. */
			sendBeacon();
		}
	}

	/* The user wants to set the interval to newPeriod. Refuse bogus values
	   and return false, or accept the change, broadcast it, and return
	   true */
	synchronized boolean setInterval(int newPeriod) {
		if (newPeriod < 1 || newPeriod > 65535) {
			return false;
		}
		interval = newPeriod;
		version++;
		sendBeacon();
		return true;
	}

	/* The user wants to set the threshold to newThreshold. Refuse bogus values
	   and return false, or accept the change, broadcast it, and return
	   true */
	synchronized boolean setThreshold(int newThreshold) {
		if (newThreshold < 1 || newThreshold > 1000) {
			return false;
		}
		threshold = newThreshold;
		version++;
		sendBeacon();
		return true;
	}

	/* Broadcast a version+interval message. */
	void sendBeacon() {
		ContourTrackingMsg omsg = new ContourTrackingMsg();
		omsg.set_version(version);
		omsg.set_interval(interval);
		omsg.set_threshold(threshold);
		omsg.set_clock(System.currentTimeMillis());
		try {
			mote.send(MoteIF.TOS_BCAST_ADDR, omsg);
		}
		catch (IOException e) {
			window.error("Cannot send message to mote");
		}
	}

	/* User wants to clear all data. */
	void clear() {
		data = new Data(this);
	}

	public static void main(String[] args) {
		ContourTracking me = new ContourTracking();
		me.exec();
	}
}
