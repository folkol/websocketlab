import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;


public class EchoServer {

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(1234);
        Socket accept;
        while((accept = serverSocket.accept()) != null)
            handleRequest(accept);
    }

    private static void handleRequest(Socket accept) throws IOException, NoSuchAlgorithmException, UnsupportedEncodingException {
        System.out.println("Connection from: " + accept.getRemoteSocketAddress());
        OutputStream out = accept.getOutputStream();
        InputStream in = accept.getInputStream();

        DataInputStream dis = new DataInputStream(in);
        System.out.println(dis.readLine());
        String socketKey = null;
        while(true) {
            String value = dis.readLine();
            if(value.equals("")) break;
            System.err.println(value);
            if(value.contains("Sec-WebSocket-Key")) {
                socketKey = value.split(":")[1].trim();
            }
        }
        System.err.println(String.format("Socket key %s:", socketKey));

        out.write("HTTP/1.1 101 Switching Protocols\r\n".getBytes());
        out.write("Connection: Upgrade\r\n".getBytes());
        out.write("Upgrade: websocket\r\n".getBytes());
        out.write("Connection: Upgrade\r\n".getBytes());

        String magickString = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String acceptToken = socketKey+magickString;
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(acceptToken.getBytes());
        String base64 = DatatypeConverter.printBase64Binary(digest);

        out.write(String.format("Sec-WebSocket-Accept: %s\r\n\r\n", base64).getBytes());

        int octet = dis.readUnsignedByte();
        int fin = octet & 128;
        int rsv1 = octet & 64;
        int rsv2 = octet & 32;
        int rsv3 = octet & 16;
        int opcode = octet & 15;
        System.err.println("Opcode: " + opcode);
        octet = dis.readUnsignedByte();
        int maskBit = octet & 128;
        int payloadLength = octet & 127;
        if(payloadLength == 126) {
            payloadLength = dis.readUnsignedShort();
        } else if (payloadLength == 127) {
            payloadLength = dis.readInt();
        }
        if(maskBit == 0) {
            System.err.println("MASK WAS NOT SET");
        }
        byte[] mask = new byte[4];
        mask[0] = dis.readByte();
        mask[1] = dis.readByte();
        mask[2] = dis.readByte();
        mask[3] = dis.readByte();

        byte[] buffer = new byte[payloadLength];
        dis.readFully(buffer);
        for(int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (buffer[i] ^ mask[i % 4]);
        }
        String payload = new String(buffer, "UTF-8");
        System.out.println(payload);

        out.write(129); // 0b10000001 final + text
        out.write(payloadLength);
        out.write(new StringBuilder(payload).reverse().toString().getBytes());

        accept.close();
    }

}
