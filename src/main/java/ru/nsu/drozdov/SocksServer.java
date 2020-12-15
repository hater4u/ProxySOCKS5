package ru.nsu.drozdov;

import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SocksServer {
    private Integer proxyPort;

    public SocksServer(Integer port) {
        proxyPort = port;
    }

    public void start() {
        try (Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(proxyPort));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            DnsResolver dnsResolver = new DnsResolver();
            datagramChannel.register(selector, SelectionKey.OP_READ, dnsResolver);

//            ByteBuffer buffer = ByteBuffer.allocate(512);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
//                    int interestOps = key.interestOps();
//                    System.out.println(interestOps);
                    iter.remove();
                    if (key.isAcceptable()) {
                        SocketChannel client = serverSocketChannel.accept();
                        client.configureBlocking(false);
                        ProxyHandler proxyHandler = new ProxyHandler(client, datagramChannel, selector);
                        client.register(selector, SelectionKey.OP_READ, proxyHandler);
                    }
                    if (key.isConnectable()) {
                        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
                        proxyHandler.finishConnectionToServer();
                    }
                    if (key.isWritable()) {
                        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
                        proxyHandler.sendClientDataToServer();
                    }
                    if (key.isReadable() && key.readyOps() != SelectionKey.OP_WRITE) {
//                        SocketChannel client = (SocketChannel) key.channel();
                        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
                        proxyHandler.processMessage();
                    }

                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }
}
