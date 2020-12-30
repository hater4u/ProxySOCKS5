package ru.nsu.drozdov;

import ru.nsu.drozdov.utils.MyDnsResolver;
import ru.nsu.drozdov.utils.SocksConstants;
import ru.nsu.drozdov.utils.SocksStage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class ProxyHandler {
    private SocketChannel client;
    private MyDnsResolver dnsResolver;
    private Socks5Model comm;
    private Selector selector;
    private SelectionKey key;

    private SocksStage currStage = SocksStage.AUTH;

    public ProxyHandler(SocketChannel clientSocket, MyDnsResolver dnsResolver, Selector selector, SelectionKey key) {
        client = clientSocket;
        this.dnsResolver = dnsResolver;
        comm = new Socks5Model(this);
        this.selector = selector;
        this.key = key;
    }

    public void updateKey(SelectionKey key){
        this.key = key;
    }

    public void processMessage() throws IOException {
        switch (currStage) {
            case AUTH:
                currStage = comm.authenticate();;
                break;
            case CLIENT_COMM:
                currStage = comm.getCommand();
                break;
            case RELAY_READ_FROM_CLIENT:
                currStage = comm.sendToServer();
                break;
            case RELAY_READ_FROM_SERVER:
                currStage = comm.readFromServer();
                break;
            case RELAY_WRITE_TO_CLIENT:
                closeConnection();
                currStage = SocksStage.AUTH;
                break;
        }
    }


    public void readClientMessage(ByteBuffer buffer) throws IOException {
        buffer.clear();
        client.read(buffer);
    }

    public void writeClientMessage(ByteBuffer buffer) throws IOException {
        client.write(buffer);
    }

    public boolean resolveName(String dnsName) throws IOException {
        return dnsResolver.resolve(dnsName, key);
    }

    public void setInetAddress(InetAddress address) throws IOException {
        comm.setAddressAndConnect(address);
    }

    public void sendErrorToClient(byte replyCode) throws IOException {
        currStage = SocksStage.CLIENT_COMM;
        byte[] buffer = comm.generateReplyCommandMessage(replyCode, (byte)0x01, SocksConstants.VOID_IP, SocksConstants.VOID_PORT);
        client.write(ByteBuffer.wrap(buffer));
    }

    public void registerSocketChannel(SocketChannel socketChannel, int ops) throws ClosedChannelException {
        socketChannel.register(selector, ops, this);
    }

    public void finishConnectionToServer() throws IOException {
        currStage = comm.finishConnection();
    }

    public void closeConnection() throws IOException {
        client.close();
    }
}

