import java.io.*;
import java.net.*;
import java.util.*;

public class TCPPacket
{
	InetAddress SourceIP;
	int SourcePort;

	InetAddress DestinationIP;
	int DestinationPort;

	int SequenceNumber;
	int AckNumber;

	int Window;

	Boolean[] ControlBit; // URG, ACK, PSH, RST, SYN, FIN
	String data;

	String StringPacket;

	public TCPPacket(InetAddress SourceIP, int SourcePort, InetAddress DestinationIP, int DestinationPort, int SequenceNumber, int AckNumber, int Window, Boolean ControlBit[], String data)
	{
		this.SourceIP = SourceIP;
		this.SourcePort = SourcePort;
		this.DestinationIP = DestinationIP;
		this.DestinationPort = DestinationPort;
		this.SequenceNumber = SequenceNumber;
		this.AckNumber = AckNumber;
		this.Window = Window;
		this.ControlBit = new Boolean[6];
		this.ControlBit = ControlBit;
		this.data = data;

		this.StringPacket = SourceIP.getHostName() + "#" + Integer.toString(SourcePort) + "#" + DestinationIP.getHostName() + "#" + Integer.toString(DestinationPort) + "#" + Integer.toString(SequenceNumber) + "#" + Integer.toString(AckNumber) + "#" + Integer.toString(Window) + "#";
		String StringControlBit = "";
		
		for(int i = 0; i < 6; i++)
			StringControlBit += Boolean.toString(ControlBit[i]) + "#";

		this.StringPacket += StringControlBit + data;
	}

	public TCPPacket(String StringPacket) throws UnknownHostException
	{
		this.StringPacket = StringPacket;

		String[] PacketArray = StringPacket.split("#");

		this.SourceIP = InetAddress.getByName(PacketArray[0]);
		this.SourcePort = Integer.parseInt(PacketArray[1]);

		this.DestinationIP = InetAddress.getByName(PacketArray[2]);
		this.DestinationPort = Integer.parseInt(PacketArray[3]);

		this.SequenceNumber = Integer.parseInt(PacketArray[4]);
		this.AckNumber = Integer.parseInt(PacketArray[5]);

		this.Window = Integer.parseInt(PacketArray[6]);

		this.ControlBit = new Boolean[6];
		//Arrays.fill(this.ControlBit, Boolean.FALSE);

		for(int i = 7; i < 13; i++)
			this.ControlBit[i-7] = Boolean.parseBoolean(PacketArray[i]);

		this.data = PacketArray[13];

		for(int i = 14; i < PacketArray.length; i++)
			this.data += "#" + PacketArray[i];
	}

	public InetAddress getSourceIP()
	{
		return SourceIP;
	}

	public int getSourcePort()
	{
		return SourcePort;
	}

	public InetAddress getDestinationIP()
	{
		return DestinationIP;
	}

	public int getDestinationPort()
	{
		return DestinationPort;
	}

	public int getSeqNumber()
	{
		return SequenceNumber;
	}

	public int getAckNumber()
	{
		return AckNumber;
	}

	public int getWindow()
	{
		return Window;
	}

	public Boolean[] getControlBit()
	{
		return ControlBit;	
	} 

	public String getStringPacket()
	{
		return StringPacket;
	}

	public String getdata()
	{
		return data;
	}

	public static void main(String args[]) throws UnknownHostException
	{
		Boolean[] ControlBit = new Boolean[6];
      	Arrays.fill(ControlBit, Boolean.FALSE);

		TCPPacket p1 = new TCPPacket(InetAddress.getByName("localhost"), 3030, InetAddress.getByName("localhost"), 3131, 1, 1, 1, ControlBit, "Shit");
		String stringpkt = p1.getStringPacket();
		System.out.println(stringpkt);

		TCPPacket p2 = new TCPPacket(stringpkt);
		System.out.println(p2.getSourceIP().getHostName());
		System.out.println(Integer.toString(p2.getSourcePort()));
		System.out.println(p2.getDestinationIP().getHostName());
		System.out.println(Integer.toString(p2.getDestinationPort()));
		System.out.println(Integer.toString(p2.getSeqNumber()));
		System.out.println(Integer.toString(p2.getAckNumber()));
		System.out.println(Integer.toString(p2.getWindow()));
		System.out.println(p2.getdata());
	}
}