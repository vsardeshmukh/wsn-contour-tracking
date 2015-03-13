Quick Build Command-line Reference

# Introduction #

**telosb**
```
make telosb install,0 bsl,/dev/ttyUSB0
```

**iris with mda100cb light sensor**
```
SENSORBOARD=mda100 make iris install,0 mib520,/dev/ttyUSB0
```

**Listen to packets from serial port**
```
java net.tinyos.tools.Listen -comm serial@/dev/ttyUSB1:iris
```

**SerialForwarder forwards packets from serial port**
```
java net.tinyos.sf.SerialForwarder -comm serial@/dev/ttyUSB1:iris
```

# Details #

Add your content here.  Format your content with:
  * Text in **bold** or _italic_
  * Headings, paragraphs, and lists
  * Automatic links to other wiki pages