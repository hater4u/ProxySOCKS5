package ru.nsu.drozdov;

import ru.nsu.drozdov.utils.SocksStage;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Socks5Model {
    private ProxyHandler proxyHandler;
    private SocketChannel server;

    private ByteBuffer commBuffer;
    private ByteBuffer dataBuffer;

    private byte[] ipv4 = new byte[4];
    private byte[] dstPort = new byte[2];
    private SocketAddress serverAddress;


    public Socks5Model(ProxyHandler prHandler) {
        proxyHandler = prHandler;
        commBuffer = ByteBuffer.allocate(512);
        dataBuffer = ByteBuffer.allocate(65536);
    }

    public void authenticate() throws IOException {
        commBuffer.clear();
        proxyHandler.readClientMessage(commBuffer);
        commBuffer.flip();
        byte SocksVersion = commBuffer.get();
        if (!checkVersion(SocksVersion)) {
            throw new IOException("Invalid SOCKS version, for connection required Version SOCKS5");
        }
        byte nMethods = commBuffer.get();
        if ((nMethods == (byte) 0x00) || (commBuffer.limit() - 2 != (int) nMethods)) throw new IOException("Invalid number of methods");
        byte[] methods = new byte[255];
        commBuffer.get(methods, 0, nMethods);
        for (int i = 0; i < nMethods; i++) {
            if (methods[i] == (byte) 0x00) {
                sendToClient(authenticationMessage());
                return;
            }
        }
        throw new IOException("proxy does not support these methods");
    }

    public SocksStage getCommand() throws IOException {
        commBuffer.clear();
        proxyHandler.readClientMessage(commBuffer);
        commBuffer.flip();
        byte SocksVersion = commBuffer.get();
        if (!checkVersion(SocksVersion)) {
            throw new IOException("Invalid SOCKS version, for connection required Version SOCKS5");
        }
        byte command = commBuffer.get();
        if (command != (byte)0x01) throw new IOException("Unknown command");
        commBuffer.get(); // RSV 0x00
        byte aType = commBuffer.get();
        switch (aType) {
            case (byte)0x01:
                commBuffer.get(ipv4, 0, 4);
                commBuffer.get(dstPort, 0, 2);
                serverAddress = new InetSocketAddress(InetAddress.getByAddress(ipv4), ByteBuffer.wrap(dstPort).getShort());
                connectToServer();
                return SocksStage.CONN2SERV;
            case (byte)0x03:

                return SocksStage.CLIENT_COMM;
            default:
                throw new IOException("Unsupported Address type");
        }
    }

    public void relay() throws IOException {
        ByteBuffer someBuff = ByteBuffer.allocate(65536);
//        dataBuffer.clear();
//        server.read(dataBuffer);
//        dataBuffer.flip();
//        sendToClient(dataBuffer.array());
//        someBuff.clear();
        server.read(someBuff);
        server.close();
        someBuff.flip();
        sendToClient(someBuff.array());

    }

    public void connectToServer() throws IOException {
        server = SocketChannel.open();
        server.configureBlocking(false);
        server.connect(serverAddress);
        proxyHandler.registerSocketChannel(server, SelectionKey.OP_CONNECT);
    }

//    private void connect(SelectionKey key) throws IOException {
//        SocketChannel channel = ((SocketChannel) key.channel());
//        Attachment attachment = ((Attachment) key.attachment());
//        // Завершаем соединение
//        channel.finishConnect();
//        // Создаём буфер и отвечаем OK
//        attachment.in = ByteBuffer.allocate(bufferSize);
//        attachment.in.put(OK).flip();
//        attachment.out = ((Attachment) attachment.peer.attachment()).in;
//        ((Attachment) attachment.peer.attachment()).out = attachment.in;
//        // Ставим второму концу флаги на на запись и на чтение
//        // как только она запишет OK, переключит второй конец на чтение и все
//        // будут счастливы
//        attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
//        key.interestOps( 0 );
//    }

    public void sendToServer() throws IOException {
        sendToClient(responseClientMessage());
        proxyHandler.readClientMessage(dataBuffer);
        dataBuffer.flip();
        server.finishConnect();
        proxyHandler.registerSocketChannel(server, SelectionKey.OP_WRITE);
        server.write(dataBuffer);
    }

    private void sendToClient(byte[] message) throws IOException {
        proxyHandler.writeClientMessage(ByteBuffer.wrap(message));
    }

    private boolean checkVersion(byte versionByte) {
        return versionByte == 0x05;
    }

    private byte[] authenticationMessage() {
        return new byte[]{(byte)0x05, (byte)0x00};
    }

    private byte[] responseClientMessage() {
        ByteBuffer msg = ByteBuffer.allocate(300);
        msg.put((byte) 0x05);
        msg.put((byte) 0x00);
        msg.put((byte) 0x00);
        msg.put((byte) 0x01);
        msg.put(ipv4);
        msg.put(dstPort);
        return msg.array();
    }
}
