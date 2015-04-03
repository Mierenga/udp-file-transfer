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
    
    // timeSent[] is used for assuming packet loss.
    // Everytime a packet is sent the time of sending should
    // be saved in the appropriate offset of timeSent[].
    public long timeSent[] = new int[WINDOW_SIZE];
    
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
                	
                	// get info about the file
                	
                	double dFileSize = fileChannel.size();
                	                	
                	int totalPackets = (int) Math.ceil(dFileSize / DATA_SIZE);
                    int fileSize = (int) dFileSize;
                	
                	// send the confirmation of request
                	
                	serverSocket.send (
            	        assembleConfirmPacket (
                            fileName, fileSize, totalPackets, clientAddr, clientPort));

                    // setup the sliding window                    
                    
                	int windowBottom = 0;
                	int sequence = 0;
                	int acknowledgment = 0;
                	int ackCount = 0;
                	int[] window = new int[WINDOW_SIZE];
                	int head = 0;
                	
                    // Construct and send the first five packets

                    System.out.print("packet traffic:\n[");

                    for (int i = 0; i < WINDOW_SIZE; i++) {
                        
                        if (sequence < totalPackets) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence++));
                            
                            timeSent[i] = System.currentTimeMillis();

                            System.out.print("s:" + sequence + ", ");
                            window[i] = i;
                            
                        } else {
                            break;
                        }
                        
                    }
	                
	                // Open a TimoutThread to check for packet losses
	                
	                TimeoutThread timeoutThread = new TimeoutThread();
	                
	                
                    // listen for acknowledgments from client
                    
                    while (sequence < totalPackets || ackCount < totalPackets) {
                    
            			// listen for any ack
            			
            			serverSocket.receive(recvPacket);
            			
            			// parse ack for sequence number
            			
            			acknowledgment = getAckNumber(recvPacket);
                        ackCount++;
                        
                        System.out.print("a:" + acknowledgment + ", ");
                        
                        // update window with new acknowledgment
                        
                        int packetsToSend = updateWindow(window, head, acknowledgment);
                        head = (head+packetsToSend)%WINDOW_SIZE;
                        
                        // send new packets, if necessary
                        
                        for (int i = 0; i < packetsToSend; i++) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence++));
                                    
                        System.out.print("s:" + sequence + ", ");
                            
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
        
        //serverSocket.close();
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
        Returns a DatagramPacket with 1000 bytes of file data, ready to be sent
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
        
        
        // compute the checkSum of the buf and seq here
        computeChecksum(seq, buf);
        
        byte[] data = new byte[PACK_SIZE];
        data = addHead(seq, checkSum, buf);
        
        return new DatagramPacket(data, data.length, addr, port);
    }
    /*
        Helper for constructNextPacket()
        Returns a byte array with header and data, ready to be loaded into a packet
        Packet includes:
            4 bytes of sequence number
            (Up to) 20 bytes of checksum
            1000 bytes data
    */
    public static byte[]
    addHead(int seq, int checkSum, ByteBuffer data)
    {
        byte[] packArr = new byte[PACK_SIZE];
        byte[] headArr = ByteBuffer.allocate(HEAD_SIZE).putInt(seq).array();
        
        System.arraycopy(headArr, 0, packArr, 0, HEAD_SIZE);
        System.arraycopy(data.array(), 0, packArr, 4, DATA_SIZE);
        
        return packArr;
    }
    /*
        Returns the checksum for the given seq# and data buffer
    */
    public static byte[]
    computeChecksum(int seq, ByteBuffer buf)
    {
        
    }
    /*
        Returns number of new packets to send to client
    */
    public static int
    updateWindow(int[] win, int head, int ack)
    {
        int packetsToSend = 0;
        if (ack == ACKNOWLEDGED) {
        
            // set the headslot val be one more than the prev slot val
            win[head] = win[(head-1)%WINDOW_SIZE] + 1;
            // then, roll the window
            head = (head++)%WINDOW_SIZE;
            
            if (win[head] == ACKNOWLEDGED) {
                return updateWindow(win, head, win[head]) + 1;
            }
            return ++packetsToSend;
            
        } else if (ack == win[head]) {
        
            // set the headslot val to be WINDOW_SIZE more than the current val
            win[head] += WINDOW_SIZE;
            // then, roll the window
            head = (head++)%WINDOW_SIZE;
            
            if (win[head] == ACKNOWLEDGED) {
                return updateWindow(win, head, win[head]) + 1;
            }
            return ++packetsToSend;
            
        } else {
        
            for (int i = 0; i < WINDOW_SIZE; i++) {
                if (ack == win[i]) {
                    win[i] = ACKNOWLEDGED;
                    return 0;
                }
            }
        }
        
        return 0;   
    }
    /*
        Returns sequence number from data of acknowledgment packet
    */
    public static int
    getAckNumber(DatagramPacket dp)
    {
	    ByteBuffer head = ByteBuffer.allocate(4).put(dp.getData(), 0, 4);
        head.flip();
        return head.getInt();
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
            if ((System.currentTimeMillis() - timeSent[i]) > TIMEOUT ) {
            
                // resend the packet with seq# in window[i]
                
                socket.send(
                    constructNextPacket(
                        fileChannel, clientAddr, clientPort, window[i]));
                        
                    
                
            }
        }
    }
}


