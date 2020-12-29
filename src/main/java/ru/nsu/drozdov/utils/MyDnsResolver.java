package ru.nsu.drozdov.utils;

import org.xbill.DNS.*;
import ru.nsu.drozdov.ProxyHandler;

import java.io.IOException;
import java.lang.Record;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

public class MyDnsResolver {
    private DatagramChannel datagramChannel;
    private InetSocketAddress inetSocketAddress;
    private Map<Integer, SelectionKey> unresolvedRequests = new HashMap<>();
    private int messageCounter;
    private ByteBuffer buffer;

    public MyDnsResolver(DatagramChannel datagramChannel) throws IOException {
        inetSocketAddress = ResolverConfig.getCurrentConfig().server();
        messageCounter = 0;

        buffer = ByteBuffer.allocate(1024);
        this.datagramChannel = datagramChannel;
        this.datagramChannel.connect(inetSocketAddress);
    }

    private Message getMessage(String dnsName) throws TextParseException {
        Header headers = new Header(messageCounter);
        headers.setFlag(Flags.RD);
        headers.setOpcode(0);

        Message message = new Message();
        message.setHeader(headers);
        message.addRecord(java.lang.Record.newRecord(new Name(dnsName + "."), Type.A, DClass.IN), Section.QUESTION);

        messageCounter++;

        return message;
    }

    public boolean resolve(String dnsName, SelectionKey key) throws IOException {
        try {
            Message message = getMessage(dnsName);
            unresolvedRequests.put(message.getHeader().getID(), key);
            datagramChannel.write(ByteBuffer.wrap(message.toWire()));
            return false;
        }
        catch (TextParseException e){
            ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
            proxyHandler.sendErrorToClient(SocksConstants.HOST_UNREACHABLE);
            return true;
        }
    }

    public void handleIp() throws IOException {
        datagramChannel.read(buffer);

        Message message = new Message(buffer.flip().array());
        List<Record> answers = message.getSection(Section.ANSWER);
        SelectionKey key = unresolvedRequests.get(message.getHeader().getID());
        unresolvedRequests.remove(message.getHeader().getID());

        ProxyHandler proxyHandler = (ProxyHandler) key.attachment();
        for(int i = 0; i < answers.size(); i++){
            if(answers.get(i) instanceof ARecord){
                InetAddress address = ((ARecord)answers.get(i)).getAddress();
                proxyHandler.setInetAddress(address);
                return;
            }
        }

        proxyHandler.sendErrorToClient(SocksConstants.HOST_UNREACHABLE);
    }
}