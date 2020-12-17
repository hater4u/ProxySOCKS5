package ru.nsu.drozdov;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SocksServer {
    private Integer proxyPort;
    private DatagramChannel datagramChannel;

    public SocksServer(Integer port) {
        proxyPort = port;
    }

    public void start() {
        try (Selector selector = Selector.open(); ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {

            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(proxyPort));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            DnsResolver dnsResolver = new DnsResolver();
            datagramChannel.register(selector, SelectionKey.OP_READ, dnsResolver);


            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) {
                        acceptConnection(key);
                    }
                    if (key.isConnectable()) {
                        finishConnection(key);
                    }
                    if (key.isWritable()) {
                        closeConnection(key);
                    }
                    if (key.isReadable() && key.readyOps() != SelectionKey.OP_WRITE) {
                        handleMessage(key);
                    }
                }
            }
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        Selector selector = key.selector();
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel)key.channel();

        SocketChannel client = serverSocketChannel.accept();
        client.configureBlocking(false);
        ProxyHandler proxyHandler = new ProxyHandler(client, this.datagramChannel, selector);

        client.register(selector, SelectionKey.OP_READ, proxyHandler);
    }

    private void handleMessage(SelectionKey key) throws IOException {
        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
        proxyHandler.processMessage();
    }

    private void finishConnection(SelectionKey key) throws IOException {
        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
        proxyHandler.finishConnectionToServer();
    }

    private void closeConnection(SelectionKey key) throws IOException {
        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
        proxyHandler.processMessage();
    }
}
