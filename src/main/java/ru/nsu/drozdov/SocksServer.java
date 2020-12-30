package ru.nsu.drozdov;

import ru.nsu.drozdov.utils.MyDnsResolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SocksServer {
    private Integer proxyPort;
    private DatagramChannel datagramChannel;
    private MyDnsResolver dnsResolver;

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
            datagramChannel.register(selector, SelectionKey.OP_READ, this);


            dnsResolver = new MyDnsResolver(datagramChannel);


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
                        if(key.attachment() instanceof ProxyHandler) handleMessage(key);
                        else endResolving(key);
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
        ProxyHandler proxyHandler = new ProxyHandler(client, dnsResolver, selector, key);

        client.register(selector, SelectionKey.OP_READ, proxyHandler);
    }

    private void handleMessage(SelectionKey key) throws IOException {
        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
        proxyHandler.updateKey(key);
        proxyHandler.processMessage();
    }

    private void endResolving(SelectionKey key) throws IOException {
        dnsResolver.handleIp();
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
