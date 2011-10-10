/*
 * Copyright (c) 2006 Intel Corporation
* All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

/**
 * ContourTracking demo application. Uses the demo sensor - change the
 * new DemoSensorC() instantiation if you want something else.
 *
 * See README.txt file in this directory for usage instructions.
 *
 * @author David Gay
 */
configuration ContourTrackingAppC { }
implementation
{
  components ContourTrackingC, MainC, ActiveMessageC, LedsC,
    new TimerMilliC(), new DemoSensorC() as Sensor, 
    new AMSenderC(AM_CONTOURTRACKING), new AMReceiverC(AM_CONTOURTRACKING);

  ContourTrackingC.Boot -> MainC;
  ContourTrackingC.RadioControl -> ActiveMessageC;
  ContourTrackingC.AMSend -> AMSenderC;
  ContourTrackingC.Receive -> AMReceiverC;
  ContourTrackingC.Timer -> TimerMilliC;
  ContourTrackingC.Read -> Sensor;
  ContourTrackingC.Leds -> LedsC;

  
}
