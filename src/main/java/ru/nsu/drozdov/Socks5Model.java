package ru.nsu.drozdov;

import ru.nsu.drozdov.utils.AddressUtility;
import ru.nsu.drozdov.utils.SocksConstants;
import ru.nsu.drozdov.utils.SocksStage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

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
        commBuffer = ByteBuffer.allocate(SocksConstants.COMMAND_BUFFER_SIZE);
        dataBuffer = ByteBuffer.allocate(SocksConstants.DATA_BUFFER_SIZE);
    }

    private boolean checkVersion(byte versionByte) {
        return versionByte == 0x05;
    }

    private void sendToClient(byte[] message) throws IOException {
        proxyHandler.writeClientMessage(ByteBuffer.wrap(message));
    }

    public SocksStage authenticate() throws IOException {
        proxyHandler.readClientMessage(commBuffer);
        commBuffer.flip();

        byte SocksVersion = commBuffer.get();
        if (!checkVersion(SocksVersion)) {
//            throw new IOException("Invalid SOCKS version, for connection required Version SOCKS5");
            sendToClient(SocksConstants.AUTH_MESS_REFUSE);
            proxyHandler.closeConnection();
            return SocksStage.AUTH;
        }

        byte nMethods = commBuffer.get();
        if ((nMethods == (byte) 0x00) || (commBuffer.limit() - 2 != (int) nMethods)) {
//            throw new IOException("Invalid number of methods");
            sendToClient(SocksConstants.AUTH_MESS_REFUSE);
            proxyHandler.closeConnection();
            return SocksStage.AUTH;
        }

        byte[] methods = new byte[255];
        commBuffer.get(methods, 0, nMethods);
        for (int i = 0; i < nMethods; i++) {
            if (methods[i] == (byte) 0x00) {
                sendToClient(SocksConstants.AUTH_MESS_ACCEPT);
                return SocksStage.CLIENT_COMM;
            }
        }
//        throw new IOException("proxy does not support these methods");
        sendToClient(SocksConstants.AUTH_MESS_REFUSE);
        proxyHandler.closeConnection();
        return SocksStage.AUTH;
    }

    public byte[] generateReplyCommandMessage(byte replyCode, byte addressType, byte[] address, byte[] pt){
        int buffSize = 4 + address.length + 2;
        byte[] REPLY = new byte[buffSize];

        REPLY[0] = 0x05;
        REPLY[1] = replyCode;
        REPLY[2] = 0x00;        // Reserved	'00'
        REPLY[3] = addressType;        // DOMAIN NAME Address Type IP v4
        for(int i = 0; i < address.length; i++) REPLY[4+i] = address[i];
        REPLY[buffSize-2] = pt[0];
        REPLY[buffSize-1] = pt[1];

        return REPLY;
    }

    public void connectToServer() throws IOException {

        server = SocketChannel.open();
        server.configureBlocking(false);
        server.connect(serverAddress);
        proxyHandler.registerSocketChannel(server, SelectionKey.OP_CONNECT);
    }

    public void setAddressAndConnect(InetAddress inetAddress) throws IOException {
        serverAddress = new InetSocketAddress(inetAddress, AddressUtility.calcPort(dstPort[0], dstPort[1]));
        connectToServer();
    }

    public SocksStage getCommand() throws IOException {
        proxyHandler.readClientMessage(commBuffer);
        commBuffer.flip();

        byte SocksVersion = commBuffer.get();
        if (!checkVersion(SocksVersion)) {
            sendToClient(generateReplyCommandMessage((byte)0xFF, SocksConstants.ATYPE_IPV4, SocksConstants.VOID_IP, SocksConstants.VOID_PORT));
            return SocksStage.AUTH;
        }

        byte command = commBuffer.get();
        if (command != (byte)0x01) {

            sendToClient(generateReplyCommandMessage((byte)0x07, SocksConstants.ATYPE_IPV4, SocksConstants.VOID_IP, SocksConstants.VOID_PORT));
            return SocksStage.AUTH;
        }

        switch (command) {
            case SocksConstants.SC_CONNECT:

                commBuffer.get(); // RSV 0x00

                byte aType = commBuffer.get();
                switch (aType) {
                    case SocksConstants.ATYPE_IPV4:

                        commBuffer.get(ipv4, 0, 4);
                        commBuffer.get(dstPort, 0, 2);

                        InetAddress inetAddress = AddressUtility.calcInetAddress(ipv4);
                        if(inetAddress == null){
                            sendToClient(generateReplyCommandMessage((byte)0x04, SocksConstants.ATYPE_IPV4, SocksConstants.VOID_IP, SocksConstants.VOID_PORT));
                            return SocksStage.AUTH;
                        }

                        serverAddress = new InetSocketAddress(inetAddress, AddressUtility.calcPort(dstPort[0], dstPort[1]));
                        connectToServer();
                        return SocksStage.CONNECTION_TO_SERVER;

                    case SocksConstants.ATYPE_DOMAINNAME:
                        byte size = commBuffer.get();
                        byte[] dnsBuffer = new byte[size];

                        for(int i = 0; i < size; i++) dnsBuffer[i] = commBuffer.get();
                        commBuffer.get(dstPort, 0, 2);

                        if(proxyHandler.resolveName(new String(dnsBuffer, StandardCharsets.US_ASCII))) return SocksStage.CLIENT_COMM;
                        return SocksStage.CONNECTION_TO_SERVER;

                    case SocksConstants.ATYPE_IPV6:
                    default:
                        sendToClient(generateReplyCommandMessage((byte)0x08, SocksConstants.ATYPE_IPV4, SocksConstants.VOID_IP, SocksConstants.VOID_PORT));
                        return SocksStage.AUTH;
                }

            case SocksConstants.SC_BIND:
            case SocksConstants.SC_UDP:
            default:
                sendToClient(generateReplyCommandMessage((byte)0x07, SocksConstants.ATYPE_IPV4, SocksConstants.VOID_IP, SocksConstants.VOID_PORT));
                return SocksStage.AUTH;
        }
    }


    public SocksStage finishConnection() throws IOException {
        try {
            if(!server.finishConnect()) throw new IOException("Can't connect to Server");

        } catch (IOException e) {
            sendToClient(generateReplyCommandMessage((byte)0x05, SocksConstants.ATYPE_IPV4, ipv4, dstPort));
            return SocksStage.AUTH;
        }

        sendToClient(generateReplyCommandMessage((byte)0x00, SocksConstants.ATYPE_IPV4, ipv4, dstPort));
        return SocksStage.RELAY_READ_FROM_CLIENT;
    }

    public SocksStage sendToServer() throws IOException {
        proxyHandler.readClientMessage(dataBuffer);
        dataBuffer.flip();

        System.out.println(new String(dataBuffer.array(), StandardCharsets.US_ASCII));

        proxyHandler.registerSocketChannel(server, SelectionKey.OP_READ);
        server.write(dataBuffer);
        return SocksStage.RELAY_READ_FROM_SERVER;
    }

    public SocksStage readFromServer() throws IOException {
        dataBuffer.clear();
        server.read(dataBuffer);
        server.close();

        dataBuffer.flip();

        proxyHandler.writeClientMessage(dataBuffer);
        return SocksStage.RELAY_WRITE_TO_CLIENT;
    }

}
