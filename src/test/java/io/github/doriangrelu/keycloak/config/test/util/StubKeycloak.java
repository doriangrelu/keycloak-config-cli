/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 keycloak-config-cli contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.test.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A few endpoints of Keycloak answered by a plain blocking HTTP/1.1 server, one connection at a time. The JDK
 * {@code HttpServer} is not used: its selector cannot open on some Windows set-ups.
 */
public final class StubKeycloak implements AutoCloseable {

    /** A request received by the stub. */
    public record Request(String method, String path, String authorization, String body) {}

    /** The answer of a route. */
    public record Answer(int status, String body) {}

    private final ServerSocket server;
    private final Map<String, Supplier<Answer>> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public StubKeycloak() throws IOException {
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::serve, "stub-keycloak");
        thread.setDaemon(true);
        thread.start();
    }

    public String url() {
        return "http://" + this.server.getInetAddress().getHostAddress() + ":" + this.server.getLocalPort();
    }

    /** Answers the requests to this path, whatever their method; other paths are answered 404. */
    public void route(final String path, final Supplier<Answer> answer) {
        this.routes.put(path, answer);
    }

    public List<Request> requestsTo(final String path) {
        return this.requests.stream()
                .filter(request -> request.path().equals(path))
                .toList();
    }

    @Override
    public void close() throws IOException {
        this.server.close();
    }

    private void serve() {
        while (!this.server.isClosed()) {
            try (Socket socket = this.server.accept()) {
                handle(socket);
            } catch (IOException closedOrBroken) {
                // the next accept fails as well once the server is closed, which ends the loop
            }
        }
    }

    private void handle(final Socket socket) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        String[] requestLine = readLine(in).split(" ");
        String authorization = null;
        int length = 0;
        for (String header = readLine(in); !header.isEmpty(); header = readLine(in)) {
            int colon = header.indexOf(':');
            String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = header.substring(colon + 1).trim();
            if (name.equals("content-length")) {
                length = Integer.parseInt(value);
            } else if (name.equals("authorization")) {
                authorization = value;
            }
        }
        String body = new String(in.readNBytes(length), StandardCharsets.UTF_8);
        String path = requestLine[1].split("\\?")[0];
        this.requests.add(new Request(requestLine[0], path, authorization, body));

        Supplier<Answer> route = this.routes.get(path);
        Answer answer = route == null ? new Answer(404, "{}") : route.get();
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + answer.status() + " Stub\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + bytes.length + "\r\n"
                        + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private static String readLine(final InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int next = in.read(); next != -1 && next != '\n'; next = in.read()) {
            if (next != '\r') {
                line.write(next);
            }
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }
}
