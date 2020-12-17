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
                currStage = comm.authenticate();;
                break;
            case CLIENT_COMM:
                currStage = comm.getCommand();
                break;
//            case CONNECTION_TO_SERVER:
//                comm.sendToServer();
//                currStage = SocksStage.RELAY;
//                break;
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
//        buffer.flip();
        client.write(buffer);
    }

    public void registerSocketChannel(SocketChannel socketChannel, int ops) throws ClosedChannelException {
        socketChannel.register(selector, ops, this);
    }

    public void finishConnectionToServer() throws IOException {
        currStage = comm.finishConnection();
    }

    private void closeConnection() throws IOException {
        client.close();
    }
}
