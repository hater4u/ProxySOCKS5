package ru.nsu.drozdov;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1) {
            SocksServer socksServer = new SocksServer(Integer.parseInt(args[0]));
            socksServer.start();
        } else {
            System.out.println("error");
        }
    }
}
