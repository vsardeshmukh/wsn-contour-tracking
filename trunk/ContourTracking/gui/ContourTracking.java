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

	/* TimerTask: update motes clock periodically */
	public void run() {
		sendBeacon();
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

	/* The data object has informed us that nodeId is a previously unknown
	   mote. Update the GUI. */
	void newNode(int nodeId) {
		window.newNode(nodeId);
	}

	public synchronized void messageReceived(int dest_addr, 
			Message msg) {
		if (msg instanceof ContourTrackingMsg) {
			ContourTrackingMsg omsg = (ContourTrackingMsg)msg;

			/* Update interval and mote data */
			periodUpdate(omsg.get_version(), omsg.get_interval());
			data.update(omsg.get_id(), omsg.get_count(), omsg.get_readings());
			//System.out.println("mote[" + omsg.get_id() + "] msg seq: " + omsg.get_count() + " with ts: " + omsg.get_clock() + " at " + System.currentTimeMillis());

			/* Inform the GUI that new data showed up */
			window.newData();
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
