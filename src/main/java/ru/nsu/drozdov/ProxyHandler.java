package ru.nsu.drozdov;

import ru.nsu.drozdov.utils.SocksStage;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ProxyHandler {
    private SocketChannel client;
    private DatagramChannel datagramDNSChannel;
    private Socks5Model comm;
    private Selector selector;

    private ByteBuffer buffer = ByteBuffer.allocate(512);

    private SocksStage currStage = SocksStage.AUTH;

    public ProxyHandler(SocketChannel clientSocket, DatagramChannel datagramChannel, Selector selector) {
        client = clientSocket;
        datagramDNSChannel = datagramChannel;
        comm = new Socks5Model(this);
        this.selector = selector;
    }

    public void processMessage() throws IOException {
        switch (currStage) {
            case AUTH:
                comm.authenticate();
                currStage = SocksStage.CLIENT_COMM;
                break;
            case CLIENT_COMM:
                currStage = comm.getCommand();
                break;
            case CONN2SERV:
                comm.sendToServer();
                currStage = SocksStage.RELAY;
                break;
            case RELAY:
                comm.relay();
                currStage = SocksStage.CLIENT_COMM;
                break;
        }
    }

    public void sendClientDataToServer() throws IOException {
        comm.relay();
        currStage = SocksStage.CLIENT_COMM;
    }

    public void readClientMessage(ByteBuffer buffer) throws IOException {
        buffer.clear();
        client.read(buffer);
    }

    public void writeClientMessage(ByteBuffer buffer) throws IOException {
        client.write(buffer);
    }

    public void registerSocketChannel(SocketChannel socketChannel, int ops) throws ClosedChannelException {
        socketChannel.register(selector, ops, this);
    }

    public void finishConnectionToServer() throws IOException {
        comm.sendToServer();
    }
}
