package org.hocate.http.server.websocket;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

import org.hocate.tools.log.Logger;

public class WebSocketFrame {
	private boolean		fin;
	private Opcode		opcode;
	private boolean		transfereMask;
	private ByteBuffer	frameData;

	public boolean isFin() {
		return fin;
	}

	public void setFin(boolean fin) {
		this.fin = fin;
	}

	public Opcode getOpcode() {
		return opcode;
	}

	public void setOpcode(Opcode opcode) {
		this.opcode = opcode;
	}

	public boolean isTransfereMask() {
		return transfereMask;
	}

	public void setTransfereMask(boolean transfereMask) {
		this.transfereMask = transfereMask;
	}

	public ByteBuffer getFrameData() {
		return frameData;
	}

	public void setFrameData(ByteBuffer frameData) {
		this.frameData = frameData;
	}

	/**
	 * 构建新的实例
	 * 
	 * @param binary
	 * @param mask
	 * @return
	 */
	public static WebSocketFrame newInstance(boolean fin, Opcode opcode, boolean mask, ByteBuffer binary) {
		WebSocketFrame webSocketFrame = new WebSocketFrame();
		webSocketFrame.setFin(fin);
		webSocketFrame.setOpcode(opcode);
		webSocketFrame.setTransfereMask(mask);
		webSocketFrame.setFrameData(binary);
		return webSocketFrame;
	}

	public String toString() {
		return "Framedata={FIN: " + this.isFin() + " , Mask: " + this.isTransfereMask() + " , OpCode: " + getOpcode() + " , Data: "
				+ new String(getFrameData().array()) + "}";
	}

	/**
	 * 解析报文
	 * 
	 * @param byteBuffer
	 * @return
	 */
	public static WebSocketFrame parse(ByteBuffer byteBuffer) {
		// 接受数据的大小
		int maxpacketsize = byteBuffer.remaining();
		// 期望数据包的实际大小
		int expectPackagesize = 2;
		if (maxpacketsize < expectPackagesize) {
			Logger.error("Package size error!");
			return null;
		}
		byte finByte = byteBuffer.get();
		boolean FIN = finByte >> 8 != 0;
		byte rsv = (byte) ((finByte & ~(byte) 128) >> 4);
		if (rsv != 0) {
			Logger.error("Rsv data error!");
			return null;
		}
		byte maskByte = byteBuffer.get();
		boolean mask = (maskByte & -128) != 0;
		int payloadlength = (byte) (maskByte & ~(byte) 128);
		Opcode opcode = toOpcode((byte) (finByte & 15));

		if (payloadlength >= 0 && payloadlength <= 125) {
		} else {
			if (payloadlength == 126) {
				expectPackagesize += 2;
				byte[] sizebytes = new byte[3];
				sizebytes[1] = byteBuffer.get();
				sizebytes[2] = byteBuffer.get();
				payloadlength = new BigInteger(sizebytes).intValue();
			} else {
				expectPackagesize += 8;
				byte[] bytes = new byte[8];
				for (int i = 0; i < 8; i++) {
					bytes[i] = byteBuffer.get();
				}
				long length = new BigInteger(bytes).longValue();
				if (length <= Integer.MAX_VALUE) {
					payloadlength = (int) length;
				}
			}
		}

		expectPackagesize += (mask ? 4 : 0);
		expectPackagesize += payloadlength;

		// 如果世界接受的数据小于数据包的大小则报错
		if (maxpacketsize < expectPackagesize) {
			Logger.error("Package size error!");
			return null;
		}

		// 读取实际接受数据
		ByteBuffer payload = ByteBuffer.allocate(payloadlength);
		if (mask) {
			byte[] maskskey = new byte[4];
			byteBuffer.get(maskskey);
			for (int i = 0; i < payloadlength; i++) {
				payload.put((byte) ((byte) byteBuffer.get() ^ (byte) maskskey[i % 4]));
			}
		} else {
			payload.put(byteBuffer.array(), byteBuffer.position(), payload.limit());
			byteBuffer.position(byteBuffer.position() + payload.limit());
		}

		return WebSocketFrame.newInstance(FIN, opcode, mask, payload);
	}

	/**
	 * 类型枚举
	 * 
	 * @author helyho
	 *
	 */
	public enum Opcode {
		CONTINUOUS, TEXT, BINARY, PING, PONG, CLOSING
	}

	/**
	 * 转换 WebSocket 报文类型为枚举类型
	 * 
	 * @param opcode
	 * @return
	 */
	private static Opcode toOpcode(byte opcode) {
		switch (opcode) {
		case 0:
			return Opcode.CONTINUOUS;
		case 1:
			return Opcode.TEXT;
		case 2:
			return Opcode.BINARY;
			// 3-7 are not yet defined
		case 8:
			return Opcode.CLOSING;
		case 9:
			return Opcode.PING;
		case 10:
			return Opcode.PONG;
		default:
			return null;
		}
	}

	/**
	 * opcode转换成 byte
	 * 
	 * @param opcode
	 * @return
	 */
	private static byte fromOpcode(Opcode opcode) {
		if (opcode == Opcode.CONTINUOUS)
			return 0;
		else if (opcode == Opcode.TEXT)
			return 1;
		else if (opcode == Opcode.BINARY)
			return 2;
		else if (opcode == Opcode.CLOSING)
			return 8;
		else if (opcode == Opcode.PING)
			return 9;
		else if (opcode == Opcode.PONG)
			return 10;
		return -1;
	}

	/**
	 * 转换 long 为 byte
	 * 
	 * @param val
	 * @param bytecount
	 * @return
	 */
	private static byte[] toByteArray(long value, int bytecount) {
		byte[] buffer = new byte[bytecount];
		int highest = 8 * bytecount - 8;
		for (int i = 0; i < bytecount; i++) {
			buffer[i] = (byte) (value >>> (highest - 8 * i));
		}
		return buffer;
	}

	/**
	 * 转换成 Bytebuffer 供 socket 通信用
	 * 
	 * @return
	 */
	public ByteBuffer toByteBuffer() {
		ByteBuffer data = this.getFrameData();
		if(data == null){
			data = ByteBuffer.allocate(0);
		}
		boolean mask = this.isTransfereMask(); // framedata.getTransfereMasked();
		int sizebytes = data.remaining() <= 125 ? 1 : data.remaining() <= 65535 ? 2 : 8;
		ByteBuffer buf = ByteBuffer.allocate(1 + (sizebytes > 1 ? sizebytes + 1 : sizebytes) + (mask ? 4 : 0) + data.remaining());
		byte optcode = fromOpcode(this.getOpcode());
		byte one = (byte) (this.isFin() ? -128 : 0);
		one |= optcode;
		buf.put(one);
		byte[] payloadlengthbytes = toByteArray(data.remaining(), sizebytes);
		assert (payloadlengthbytes.length == sizebytes);

		if (sizebytes == 1) {
			buf.put((byte) ((byte) payloadlengthbytes[0] | (mask ? (byte) -128 : 0)));
		} else if (sizebytes == 2) {
			buf.put((byte) ((byte) 126 | (mask ? (byte) -128 : 0)));
			buf.put(payloadlengthbytes);
		} else if (sizebytes == 8) {
			buf.put((byte) ((byte) 127 | (mask ? (byte) -128 : 0)));
			buf.put(payloadlengthbytes);
		} else
			throw new RuntimeException("Size representation not supported/specified");

		if (mask) {
			ByteBuffer maskkey = ByteBuffer.allocate(4);
			Random reuseableRandom = new Random();
			maskkey.putInt(reuseableRandom.nextInt());
			buf.put(maskkey.array());
			for (int i = 0; data.hasRemaining(); i++) {
				buf.put((byte) (data.get() ^ maskkey.get(i % 4)));
			}
		} else
			buf.put(data);
		// translateFrame ( buf.array () , buf.array ().length );
		assert (buf.remaining() == 0) : buf.remaining();
		buf.flip();

		return buf;
	}
}