package ru.nsu.drozdov;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public class DnsResolver {
    private InetSocketAddress dnsServer;
    private int messageId = 0;
    private Message response;

    public DnsResolver() {
        dnsServer = new InetSocketAddress(ResolverConfig.getCurrentConfig().server().getAddress(), 53);
    }

    public Message createMessage(String domainName) throws TextParseException {
        Header header = new Header(messageId++);
        header.setFlag(Flags.RD);
        header.setOpcode(0);

        Message message = new Message();
        message.setHeader(header);

        Record record = Record.newRecord(new Name(domainName + "."), Type.A, DClass.IN);
        message.addRecord(record, Section.QUESTION);

        return message;
    }


}
