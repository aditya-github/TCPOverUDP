import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Random;

class TimerPair
{
   public int SequenceNumber;
   public long Timeout;

   public TimerPair(int SequenceNumber, long Timeout)
   {
      this.SequenceNumber = SequenceNumber;
      this.Timeout = Timeout;
   }
   
}

public class Server
{
   DatagramSocket serverSocket;
   
   InetAddress ServerIP;
   int ServerPort;

   InetAddress ClientIP;
   int ClientPort;

   int CurrentSeqNumber;
   int CurrentAckNumber;
   
   Timer timer = new Timer();
   
   boolean timeout = false;
   
   boolean CurrentFinish;

   float CongestionWindow;
   int SSThreshold;

   ArrayList<String> PacketList;
   int FixedPacketSize;
   int NumPkts;

   ArrayList<TimerPair> TimerQ;

   PrintWriter writer;
   //ArrayList<Float> cwValues; 
   //ArrayList<Double> smoothrttValues;

   int DupCount;
   int LastAcknum;

   long timeOutPeriod;
   double smoothrtt;
   long currrtt;
   double rttdev;

   // Code for generating random Strings for packets
   // -----------------------------------------------------------------------------------------------------------------
   static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
   static Random rnd = new Random();

   String randomString( int len ){
   StringBuilder sb = new StringBuilder( len );
   for( int i = 0; i < len; i++ ) 
      sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
   return sb.toString();
   }

   void createPacketList()
   {
      PacketList = new ArrayList<String>();
      for(int i = 0; i < NumPkts; i++)
         PacketList.add(randomString(FixedPacketSize));
   }

   void printPacketList()
   {
      for(int i = 0; i < PacketList.size(); i++)
         System.out.println(PacketList.get(i));
   }

   // Constructor
   // ----------------------------------------------------------------------------------------------------------------- 

   public Server(InetAddress ServerIP, int ServerPort) throws SocketException, FileNotFoundException
   {
      // Create Server Socket
      this.serverSocket = new DatagramSocket(ServerPort);

      this.ServerIP = ServerIP;
      this.ServerPort = ServerPort; 

      CurrentSeqNumber = 1;
      CurrentAckNumber = 1;
      CurrentFinish = false;

      CongestionWindow = 1;
      SSThreshold = 20;

      FixedPacketSize = 1200;
      NumPkts = 100000;

      // Create data that the server transmits on request
      createPacketList();
      //printPacketList();

      TimerQ = new ArrayList<TimerPair>();

      writer = new PrintWriter("CW.csv");
      //cwValues = new ArrayList<Float>();
      //smoothrttValues = new ArrayList<Double>();

      DupCount = 1;
      LastAcknum = 0;

      timeOutPeriod = 5000;
      smoothrtt = 3000;
      currrtt = 3000;
      rttdev = 500;
   }

   // Low Level Functions to send and receive packets
   // -----------------------------------------------------------------------------------------------------------------

   public void SendPacket(String stringData, Boolean[] ControlBit) throws IOException
   {
      byte[] sendData = new byte[1024];


      TCPPacket pkt = new TCPPacket(ServerIP, ServerPort, ClientIP, ClientPort, CurrentSeqNumber, CurrentAckNumber , (int)CongestionWindow, ControlBit, stringData);
      sendData = pkt.getStringPacket().getBytes();

      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ClientIP, ClientPort);
      serverSocket.send(sendPacket);
   }

   public TCPPacket ReceivePacket() throws IOException
   {
      // Check whether this packet is for us

      byte[] receiveData = new byte[1024];

      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      serverSocket.receive(receivePacket);
      String stringData = new String(receivePacket.getData());

      TCPPacket revpkt = new TCPPacket(stringData);
      return revpkt; 
   }

   // Handshake
   // -----------------------------------------------------------------------------------------------------------------

   public void Handshake(InetAddress ClientIP, int ClientPort) throws IOException
   {
      // Send Syn+Ack
      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[4] = true;
      ControlBit[1] = true;

      String stringpkt = "Syn+Ack";

      SendPacket(stringpkt, ControlBit);

      // Receive Ack

      Boolean gotSyn = false;
      while(!gotSyn)
      {
         TCPPacket revpkt = ReceivePacket();
         ControlBit = revpkt.getControlBit();

         if(ControlBit[1])
         {
            gotSyn = true;
            System.out.println("Got SYN");
         }

      }

      CurrentSeqNumber = 1;
      CurrentAckNumber = 1;
      CurrentFinish = false;

      CongestionWindow = 1;
   }

   // Functions implementing Congestion Control 
   // -----------------------------------------------------------------------------------------------------------------

   void incrementCW()
   {
      if((int)CongestionWindow < SSThreshold)
         CongestionWindow += 1.0;
      else
         CongestionWindow += 1.0/CongestionWindow;

      writer.println(CongestionWindow + "," + smoothrtt);
      //cwValues.add(CongestionWindow);
      //smoothrttValues.add(smoothrtt);
   }
   
   void Timeout()
   {
      SSThreshold = (int)CongestionWindow / 2;
	   CongestionWindow = 1;

      writer.println(CongestionWindow + "," + smoothrtt);
      //cwValues.add(CongestionWindow);
      //smoothrttValues.add(smoothrtt);
   }

   void tripleDuplication()
   {
      //System.out.println("tripleDuplication!");

      TimerQ.clear();

      SSThreshold = (int)CongestionWindow / 2;
      CongestionWindow = SSThreshold + 3;

      writer.println(CongestionWindow + "," + smoothrtt);
      //cwValues.add(CongestionWindow);
      //smoothrttValues.add(smoothrtt);
   }
   
   // Checking for Timeouts
   // -----------------------------------------------------------------------------------------------------------------

   public void CalcTO(long rtt)
   {
      //System.out.println(rtt + "");
      currrtt = rtt;

      smoothrtt = 0.99 * smoothrtt + 0.01 * rtt;
      
      double diff = Math.abs(smoothrtt - rtt);
      rttdev = 0.99 * rttdev + 0.01 * diff;

      timeOutPeriod = (long)smoothrtt + (long)(4 * rttdev);
   }

   void removeFromQueue(int acknum)
   {
	   //Iterator itr = TimerQ.iterator();
	   
	   Boolean found = false;
	   
	   while(!found && !TimerQ.isEmpty()){
		   TimerPair tp = TimerQ.get(0);
		   if(tp.SequenceNumber <= acknum){
            CalcTO(System.nanoTime()/1000 - tp.Timeout);
			   TimerQ.remove(0);
			   //System.out.println("Ack got and Queue element poped");
		   }
		   else{
			   found = true;
		   }
	   }
   }
   
   void putInQueue()
   {
	 //add packet number and its timeout in TimerQ
	      
	   long current_time = System.nanoTime()/1000;
	   long timeout = current_time;
	   
	   TimerPair tp_now = new TimerPair(CurrentSeqNumber,timeout);
	      
	   TimerQ.add(tp_now);
   }

   class Timertask extends TimerTask 
   {
      public void run()
      {
         if(!TimerQ.isEmpty())
         {
            TimerPair tp = TimerQ.get(0);
            if(tp.Timeout + timeOutPeriod < System.nanoTime()/1000)
            {
               TimerQ.clear();
               Timeout();        
            }
         }
         //System.out.println("Time check");
      }
   }
   
   public void timeoutcheck()
   {
      timer.schedule(new Timertask(),0, 5);
   }
   
   public void timerexit()
   {
      timer.cancel();
   }

   // -----------------------------------------------------------------------------------------------------------------

   public void ProcessPacket(TCPPacket revpkt) throws IOException
   {
      Boolean[] ControlBit = revpkt.getControlBit();

      // Get Address of client
      this.ClientIP = revpkt.getSourceIP();
      this.ClientPort = revpkt.getSourcePort();

      if(ControlBit[4])
      {
         Handshake(ClientIP, ClientPort);
      }
      else if(ControlBit[1])
      {
         int acknum = revpkt.getAckNumber();

         removeFromQueue(acknum);   

         incrementCW();

         if(acknum == LastAcknum)
            DupCount++;
         else
            DupCount = 1;

         LastAcknum = acknum;

         if(DupCount == 3)
            tripleDuplication();

         if (CurrentAckNumber < revpkt.getAckNumber())
            CurrentAckNumber = revpkt.getAckNumber();         
         //System.out.println("Acknowledged Packets: " + (CurrentAckNumber - 1));
      }
   }

   public void SendNextPackets() throws IOException
   {
      int PacketNumberToSend = CurrentAckNumber - 1;

      //System.out.println(PacketNumberToSend);

      for(int i = 0; (i < (int)CongestionWindow) && (PacketNumberToSend + i < PacketList.size()); i++)
      {
         String stringpkt;
         Boolean[] ControlBit = new Boolean[6];
         Arrays.fill(ControlBit, Boolean.FALSE);
         
         if(PacketNumberToSend + i < PacketList.size())
            stringpkt = PacketList.get(PacketNumberToSend + i);
         else // Send FIN
         {
            stringpkt = "FIN";
            ControlBit[5] = true;
            CurrentFinish = true;
         }
         
         this.CurrentSeqNumber = (PacketNumberToSend + 1) + 1 + i;

         SendPacket(stringpkt, ControlBit);
         putInQueue();
      }
   }

   public void SendFin() throws IOException
   {
      String stringpkt = "FIN";

      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[5] = true;

      SendPacket(stringpkt, ControlBit);
   }

   // -----------------------------------------------------------------------------------------------------------------

   public static void main(String args[]) throws Exception, UnknownHostException
   {
      InetAddress ServerIP = InetAddress.getLocalHost();
      System.out.println(ServerIP);

      int ServerPort = 9999;

      Server thisServer = new Server(ServerIP, ServerPort);

      thisServer.serverSocket.setSoTimeout(10000); 
      
      thisServer.timeoutcheck();
       
      //boolean fileWritten = false;
      System.out.println("Server Ready!");

      while(true)
      {
         // Receive Packet
         TCPPacket revpkt = thisServer.ReceivePacket();

         thisServer.ProcessPacket(revpkt);

         if(thisServer.CurrentAckNumber < thisServer.NumPkts)
         {
         	if(thisServer.TimerQ.isEmpty())
         		thisServer.SendNextPackets();
         }
         else
         {	
         	thisServer.SendFin();
         	//System.out.println("Fin sent");
         	while(!thisServer.TimerQ.isEmpty()){
         		//System.out.println(thisServer.TimerQ.get(0).SequenceNumber);
         		thisServer.TimerQ.remove(0);
         	}

         	thisServer.timerexit();
            thisServer.writer.close();

            // if(!fileWritten)
            // {
            //    for(int i = 0; i < thisServer.cwValues.size(); i++)
            //       thisServer.writer.println(thisServer.cwValues.get(i) + "," + thisServer.smoothrttValues.get(i));                     

            //    //thisServer.writer.println(Arrays.toString(thisServer.cwValues.toArray()));
            //    //thisServer.writer.println(Arrays.toString(thisServer.smoothrttValues.toArray()));
                  
            //    thisServer.writer.close();

            //    fileWritten = true;
            // }
         }
      }

   }
} 