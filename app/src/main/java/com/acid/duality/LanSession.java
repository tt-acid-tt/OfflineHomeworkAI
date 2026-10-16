package com.acid.duality;

import java.io.*;
import java.net.*;
import java.util.Enumeration;

final class LanSession {
    static final int PORT = 39475;
    volatile boolean connected = false;
    volatile boolean hosting = false;
    volatile String status = "Не подключено";
    volatile String remote = "0,0,0,0,0,0";
    volatile String outgoing = "0,0,0,0,0,0";
    private volatile boolean closed = false;
    private ServerSocket server;
    private Socket socket;
    private Thread worker;

    static String localIp() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (!ip.startsWith("169.254.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "IP не найден";
    }

    void host() {
        close();
        closed = false;
        hosting = true;
        status = "Создание комнаты...";
        worker = new Thread(() -> {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(PORT), 1);
                status = "Ожидание игрока на порту " + PORT;
                socket = server.accept();
                connected = true;
                status = "Игрок подключён";
                exchangeLoop();
            } catch (Exception e) {
                if (!closed) status = "Ошибка комнаты: " + e.getMessage();
            } finally {
                connected = false;
                closeSockets();
            }
        }, "Duality-LAN-Host");
        worker.setDaemon(true);
        worker.start();
    }

    void join(String ip) {
        close();
        closed = false;
        hosting = false;
        status = "Подключение к " + ip + "...";
        worker = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip.trim(), PORT), 5000);
                s.setSoTimeout(5000);
                socket = s;
                connected = true;
                status = "Подключено к комнате";
                exchangeLoop();
            } catch (Exception e) {
                if (!closed) status = "Не удалось подключиться. Проверь IP и Wi-Fi.";
            } finally {
                connected = false;
                closeSockets();
            }
        }, "Duality-LAN-Client");
        worker.setDaemon(true);
        worker.start();
    }

    private void exchangeLoop() throws Exception {
        socket.setSoTimeout(5000);
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        while (!closed && connected) {
            out.writeUTF(outgoing);
            out.flush();
            String packet = in.readUTF();
            if (packet != null && packet.length() < 256) remote = packet;
        }
    }

    void setOutgoing(String packet) { outgoing = packet; }

    void close() {
        closed = true;
        connected = false;
        closeSockets();
        if (worker != null) worker.interrupt();
        status = "Не подключено";
    }

    private void closeSockets() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        socket = null;
        server = null;
    }
}
