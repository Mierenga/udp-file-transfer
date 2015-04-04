/*
    Server for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.net.*;
import java.lang.*;

class server {
    
    public static final int DATA_SIZE = 1000;
    public static final int HEAD_SIZE = 4;
    public static final int PACK_SIZE = DATA_SIZE + HEAD_SIZE;
    public static final int WINDOW_SIZE = 5;
    public static final int ACKNOWLEDGED = -1;
    public static final int TIMEOUT = 100; // in millis
    
    public static void main(String args[]) {
    
        /* Get port number from arg */
        
        int port = 0;
        
        try {
            port = (Integer.parseInt(args[0]));
            
            if (port > 65535 || port < 1024) {
                System.err.println("port must be from 1024 to 65535");
                System.exit(0);
            }
            
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
            
        } catch (NumberFormatException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
        }
        
        
        DatagramSocket serverSocket = null;
        InetAddress clientAddr = null;
        int clientPort = 0;
        String fileName = null;
        
        try {
            // Open a socket to listen
        
            serverSocket = new DatagramSocket(port);
        
        } catch (SocketException e) {
			    System.err.println(e);
			    System.exit(1);
        }
        
        
        for(;;) {
        
            try {

                // Receive a file request 
                
                byte[] data = new byte[PACK_SIZE];

                DatagramPacket recvPacket = 
                    new DatagramPacket(data,data.length);
                serverSocket.receive(recvPacket);
                
                fileName = new String(recvPacket.getData()).trim();

                // Save client addr and port            
                
                clientAddr = recvPacket.getAddress();
                clientPort = recvPacket.getPort();
	            
	            System.out.println("server received request for '" +
	                fileName + "'" + " from " + clientAddr.toString() + " [" + clientPort + "]" );
	            
                // Create a path from the file name,
                //   can throw NoSuchFileException
                
                
            	Path path = Paths.get(fileName).toRealPath(); 
                        
            	if (!Files.exists(path)) {
            	
            	    serverSocket.send(
            	        assembleDenialPacket(fileName, clientAddr, clientPort));
                            
            	} else {
            	
                	// Create a SeekableByteChannel object for the path
                	
                	SeekableByteChannel fileChannel = Files.newByteChannel(path, StandardOpenOption.READ);
                	
                	// Get info about the file
                	
                	double dFileSize = fileChannel.size();

                	int totalPackets = (int) Math.ceil(dFileSize / DATA_SIZE);
                    int fileSize = (int) dFileSize;
                	
                	// Send the confirmation of request
                	
                	serverSocket.send (
            	        assembleConfirmPacket (
                            fileName, fileSize, totalPackets, clientAddr, clientPort));

                    // Setup variables for sequencing and acknowledging                    
                    
                	int sequence = 0;
                	int acknowledgment = 0;
                	int ackCount = 0;
                	
                	// TODO to be converted into Window class
                	Window window = new Window(WINDOW_SIZE);
                	
                	//int[] window = new int[WINDOW_SIZE];
                	int head = 0;
                	
                    // Construct and send the first five packets

                    System.out.print("packet traffic:\n[");

                    for (int i = 0; i < WINDOW_SIZE; i++) {
                        
                        if (sequence < totalPackets) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence));
                            
                            window.updateTimeSent(i);

                            System.out.print("s:" + sequence + ", ");
                            sequence++;
                            
                        } else {
                            break;
                        }
                        
                    }
	                
	                // Fire off a TimoutThread to check for packet losses
	                
	                TimeoutThread timeoutThread = new TimeoutThread();
	                timeoutThread.start();
	                
	                
                    // listen for acknowledgments from client
                    
                    while (sequence < totalPackets || ackCount < totalPackets) {
                    
            			// listen for any ack
            			
            			serverSocket.receive(recvPacket);
            			
            			// parse ack for sequence number
            			
            			acknowledgment = getAckNumber(recvPacket);
                        ackCount++;
                        
                        System.out.print("a:" + acknowledgment + ", ");
                        
                        // update window with new acknowledgment,
                        //     find how many new packets to send from the return value
                        
                        int packetsToSend = window.update(acknowledgment);
                        
                        // If necessary, send new packets, updating their time sent
                        
                        for (int i = 0; i < packetsToSend; i++) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence));
                        
                            updateTimeSent(sequence);
                            
                            System.out.print("s:" + sequence + ", ");
                            sequence++;
                            
                        }
            	    }
            	    
	                System.out.println("client acknowledged " +
                            ackCount + " of " + totalPackets + " packets]\n");
	            	
	            }
	            	
            } catch (NoSuchFileException x) {
            
                // Send and print denial of request
                try {
                	serverSocket.send(
                	    assembleDenialPacket(fileName, clientAddr, clientPort));
                            
            	} catch (IOException e) {
            	    System.err.println(e);
            	}
            	
            } catch (IOException e) {
			    System.err.println(e);
			    System.exit(1);
		    } catch (IllegalArgumentException e) {
		        System.err.println(e);
			    System.exit(1);
		    }
        }
        
    }
    
    /* 
        Returns a DatagramPacket with request confirmation information, ready to be sent
    */
    public static DatagramPacket
    assembleConfirmPacket(String fn, int fs, int tp, InetAddress addr, int port)
    {
        String msg = new String("server received request for '" + fn + "'");
        System.out.println("  file size: " + fs);
        System.out.println("  packets to send: " + tp);
        msg += (":" + fs + ":" + tp + ":");
        	
    	byte[] data = msg.getBytes();
        	
    	return new DatagramPacket(data, data.length, addr, port);
    }
    
    /* 
        Returns a DatagramPacket with request denial information, ready to be sent
    */
    public static DatagramPacket
    assembleDenialPacket(String fn, InetAddress addr, int port)
    {
        String msg = new String("unable to locate '" + fn + "' on server");
        System.out.println("  - " + msg);
    	byte[] data = msg.getBytes();
    	return new DatagramPacket(data, data.length, addr, port);
    }
    
    /*
        Returns a DatagramPacket with 1000 bytes of file data
        and a complete header, ready to be sent
    */
    public static DatagramPacket
    constructNextPacket(SeekableByteChannel sbc, InetAddress addr, int port, int seq)
    {
        ByteBuffer buf = ByteBuffer.allocate(DATA_SIZE);
        try {
            sbc.read(buf);
        } catch (IOException e) {
            System.err.println(e);
        }
        
        
        // TODO part 3: compute the checkSum of the buf and seq here
        computeChecksum(seq, buf);
        
        byte[] data = new byte[PACK_SIZE];
        data = addHeader(seq, buf);
        
        return new DatagramPacket(data, data.length, addr, port);
    }
    /*
        TODO part 3: include the checkSum in the header construction
        
        Helper for constructNextPacket()
        Returns a byte array with header and data, ready to be loaded into a packet
        Packet includes:
            4 bytes of sequence number
            TODO (Up to) 20 bytes of checksum
            1000 bytes data
    */
    public static byte[]
    addHeader(int seq, ByteBuffer data)
    {
        byte[] packArr = new byte[PACK_SIZE];
        byte[] headerArr = ByteBuffer.allocate(HEAD_SIZE).putInt(seq).array();
        
        System.arraycopy(headerArr, 0, packArr, 0, HEAD_SIZE);
        System.arraycopy(data.array(), 0, packArr, 4, DATA_SIZE);
        
        return packArr;
    }
    /*
        TODO part 3
        Returns the checksum for the given seq# and data buffer
    */
    public static byte[]
    computeChecksum(int seq, ByteBuffer buf)
    {
        return null;
    }

    /*
        Returns sequence number from data of acknowledgment packet
    */
    public static int
    getAckNumber(DatagramPacket dp)
    {
	    ByteBuffer ack = ByteBuffer.allocate(4).put(dp.getData(), 0, 4);
        ack.flip();
        return ack.getInt();
    }
}

// TimoeoutThread is a helper thread that continuously
// checks each of the values in timeSent[]:
//   If more than TIMEOUT seconds has occurred since
//   any timeSent, we should resend the packet
//   in that slot of the window.
    
class TimeoutThread extends Thread {

    InetAddress clientAddr = null;
    int port;
    int clientPort = 0;
    SeekableByteChannel fileChannel;
    int window[] = new int[WINDOW_SIZE];
    long timeSent[] = new int[WINDOW_SIZE];
    
    TimeoutThread(int p, InetAddress a, int cp, SeekableByteChannel sbc,
                    int[] w, long[] ts)
    {
        this.port = p+1;
        this.fileChannel = sbc;
        this.clientPort = cp;
        this.clientAddr = a;
        this.timeSent = ts;
        this.window = s;
    }
    
    public void run() {
        
        DatagramSocket socket = new DatagramSocket(port);
        
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if ((System.currentTimeMillis() - getTimeSent(i)) > TIMEOUT ) {
            
                int seqToSend = getSeqNumber(i);
                
                socket.send(
                    constructNextPacket(
                        fileChannel, clientAddr, clientPort, seqToSend));
                        
                updateTimeSent(seqToSend);
                
            }
        }
    }
}

/* 
    Window object is the sliding window that stores information about 
*/

public class Window {

    int window[] = null;
    long timeSent[] = null;
    int head = 0;
    int packetsToSend = 0;
    int nextToSend = 0;
    
    /*
        Construct and initialize the window object
        Each slot is initialized to its array offset
    */
    public Window(int size) {
        window = new int[size];
        timeSent = new long[size];
        for (int i = 0; i < size; i++) {
            window[i] = i;
        }
    }
    /*
        update the time sent for a given sequence number found
        in an arbitrary slot in the window
    */
    public synchronized void
    updateTimeSent(seqNum) {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (this.window[i] == seqNum) {
                this.timeSent[i] = System.currentTimeMillis();
                return;
            }
        }
    }
    /*
        retreive the time last sent for a given slot in the window
    */
    public synchronized void
    getTimeSent(int slot) {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (this.window[i] == seqNum) {
                this.timeSent[i] = System.currentTimeMillis();
                return;
            }
        }
    }
    
    /*
        retreive the sequence number found in the given slot
        in the window
    */
    public synchronized void
    getSeqNumber(int slot) {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (this.window[i] == seqNum) {
                this.timeSent[i] = System.currentTimeMillis();
                return;
            }
        }
    }
    
    
    
    /* TODO : figure out the best of these two options:

        1. the Window.update() function shouldn't return the
           number of packets to send, it should update an internal member
           that is released and reset when a new function is called from
           the user class, indicating it will send all of those packets
        2. the Window.update() function should return the number
           of packets to send
    */ 
    /*
        Returns number of new packets to send to client
    */
    public static int
    update(int ack)
    {
        int packetsToSend = 0;
        
        // should only happen in recursive case: check to see if the new head has been
        //    previously ACKNOWLEDGED. If it has it means we can send another packet,
        //    roll the head up, and check again, otherwise we can return 
        if (ack == ACKNOWLEDGED) {
        
            // set the headslot val to be one more than the prev slot val
            //     (which is the one that was just acknowledged)
            win[this.head] = win[(this.head-1)%WINDOW_SIZE] + 1;
            // then, roll the head up
            this.head = (this.head++)%WINDOW_SIZE;
            
            if (this.window[this.head] == ACKNOWLEDGED) {
                return this.window.update(this.window[this.head]) + 1;
            }
            return ++packetsToSend;
        
        // if the ack matches the window's head, then we should send the next packet
        //    and roll the head up, checking the new head to see if it has already been
        //    acknowledged. 
        } else if (ack == this.window[this.head]) {
        
            // set the headslot val to be WINDOW_SIZE more than the current val
            this.window[this.head] += WINDOW_SIZE;
            // then, roll the head up
            this.head = (this.head++)%WINDOW_SIZE;
            
            if (this.window[this.head] == ACKNOWLEDGED) {
                return this.window.update(this.window[this.head]) + 1;
            }
            return ++packetsToSend;
            
        // else the ack is somewhere else in the window so we should update it as ACKNOWLEDGED    
        } else {
        
            for (int i = 0; i < WINDOW_SIZE; i++) {
                if (ack == this.window[i]) {
                    this.window[i] = ACKNOWLEDGED;
                    return ;
                }
            }
        }
        this.head = (this.head+packetsToSend)%WINDOW_SIZE;
        return;
    }

}















