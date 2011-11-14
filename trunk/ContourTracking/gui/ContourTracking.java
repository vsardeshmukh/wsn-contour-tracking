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

		System.out.println("There are " + snapshots.size() + " snapshots done");
		Vector prevSnapshot = snapshots.isEmpty() ? null : (Vector)snapshots.lastElement();
		if(prevSnapshot != null) {
			long ts = ((Long)prevSnapshot.firstElement()).longValue();
			System.out.println("prev snapshot timestamp: " + ts + ", last sampling timestamp: " + timestamp);
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
			System.out.println("mote[" + mote[0] + "] is event mote");
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
				} else if(!blobs.contains(blob) && eventMotes.contains(R))
					join = R;
			}

			//if(idx < count - DIM) { // LU, U, RU neighbors
				if(idx % DIM > 0 && idx + DIM <= count) { // LU neighbor
					int[] LU = null;
					if(idx < count)
						LU = (int[])motes.get(idx+DIM-1);
					if(eventMotes2.contains(LU)){
						blob.add(LU);
						eventMotes2.remove(LU);
					} else if(!blobs.contains(blob) && eventMotes.contains(LU))
						join = LU;
				}

				if(idx + DIM < count) {
					int[] U = (int[])motes.get(idx+DIM); // U neighbor
					if(eventMotes2.contains(U)) {
						blob.add(U);
						eventMotes2.remove(U);
					} else if(!blobs.contains(blob) && eventMotes.contains(U))
						join = U;
				}

				if(idx % DIM < DIM - 1 && idx + DIM + 1 < count) { // RU neighbor
					int[] RU = (int[])motes.get(idx+DIM+1);
					if(eventMotes2.contains(RU)) {
						blob.add(RU);
						eventMotes2.remove(RU);
					} else if(!blobs.contains(blob) && eventMotes.contains(RU))
						join = RU;
				}
			//}

			if(join != null) {
				for(Iterator iterator = blobs.iterator(); iterator.hasNext();) {
					Vector b = (Vector)iterator.next();
					if(b.contains(join))
						b.addAll(blob);
				}
			} else if(!blobs.contains(blob))
				blobs.add(blob);
		}

		if(blobs.isEmpty())
			return false;

		Vector snapshot = new Vector();
		snapshot.add(new Long(timestamp));
		snapshot.add(blobs);
		snapshots.add(snapshot);
		if(snapshots.size() > 10)
			snapshots.remove(snapshots.firstElement());

		// debug bright or dark sets
		debugSnapshot(snapshot);

		// identify events between two snapshots
		return true;
	}

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
